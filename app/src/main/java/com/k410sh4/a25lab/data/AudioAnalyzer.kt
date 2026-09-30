package com.k410sh4.a25lab.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.model.AudioState
import com.k410sh4.a25lab.util.FftAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.sqrt

class AudioAnalyzer(private val context: Context) {
    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val FFT_SIZE = 2048
    }

    private data class OpenedRecorder(
        val record: AudioRecord,
        val sourceLabel: String,
        val fallbackUsed: Boolean,
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong(0L)
    private val recorderLock = Any()

    @Volatile
    private var recorder: AudioRecord? = null

    fun start(onState: (AudioState) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onState(AudioState(lastError = "Permissão de microfone necessária."))
            return
        }

        stop()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        if (minBuffer <= 0) {
            onState(AudioState(lastError = "Configuração de áudio não suportada."))
            return
        }

        try {
            val bufferBytes = maxOf(minBuffer, FFT_SIZE * 4)
            val opened = runCatching {
                OpenedRecorder(
                    record = openRecorder(
                        MediaRecorder.AudioSource.UNPROCESSED,
                        SAMPLE_RATE,
                        bufferBytes,
                    ),
                    sourceLabel = "UNPROCESSED solicitado",
                    fallbackUsed = false,
                )
            }.getOrElse {
                OpenedRecorder(
                    record = openRecorder(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        bufferBytes,
                    ),
                    sourceLabel = "MIC (fallback)",
                    fallbackUsed = true,
                )
            }

            val session = synchronized(recorderLock) {
                recorder = opened.record
                generation.incrementAndGet()
            }

            onState(
                AudioState(
                    running = true,
                    sampleRateHz = SAMPLE_RATE,
                    sourceLabel = opened.sourceLabel,
                    fallbackUsed = opened.fallbackUsed,
                ),
            )

            executor.execute {
                loop(
                    session = session,
                    record = opened.record,
                    sourceLabel = opened.sourceLabel,
                    fallbackUsed = opened.fallbackUsed,
                    onState = onState,
                )
            }
        } catch (error: Exception) {
            onState(
                AudioState(
                    lastError = error.message ?: "Falha ao abrir o microfone.",
                ),
            )
        }
    }

    fun stop() {
        val current = synchronized(recorderLock) {
            generation.incrementAndGet()
            recorder.also { recorder = null }
        }

        runCatching {
            if (current?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                current.stop()
            }
        }
        runCatching { current?.release() }
    }

    fun close() {
        stop()
        executor.shutdownNow()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openRecorder(
        source: Int,
        sampleRate: Int,
        bufferBytes: Int,
    ): AudioRecord {
        var record: AudioRecord? = null
        try {
            record = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()

            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "O AudioRecord não entrou em estado de gravação."
            }
            return record
        } catch (error: Exception) {
            runCatching { record?.release() }
            throw error
        }
    }

    private fun loop(
        session: Long,
        record: AudioRecord,
        sourceLabel: String,
        fallbackUsed: Boolean,
        onState: (AudioState) -> Unit,
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val frame = ShortArray(FFT_SIZE)
        val fft = FftAnalyzer(FFT_SIZE)

        while (generation.get() == session) {
            var offset = 0

            while (offset < frame.size && generation.get() == session) {
                val read = try {
                    record.read(
                        frame,
                        offset,
                        frame.size - offset,
                        AudioRecord.READ_BLOCKING,
                    )
                } catch (error: Exception) {
                    if (generation.get() == session) {
                        onState(
                            AudioState(
                                running = false,
                                sampleRateHz = SAMPLE_RATE,
                                sourceLabel = sourceLabel,
                                fallbackUsed = fallbackUsed,
                                lastError = error.message ?: "Falha durante leitura de áudio.",
                            ),
                        )
                    }
                    return
                }

                when {
                    read > 0 -> offset += read
                    read == 0 -> continue
                    else -> {
                        if (generation.get() == session) {
                            onState(
                                AudioState(
                                    running = false,
                                    sampleRateHz = SAMPLE_RATE,
                                    sourceLabel = sourceLabel,
                                    fallbackUsed = fallbackUsed,
                                    lastError = audioReadError(read),
                                ),
                            )
                        }
                        return
                    }
                }
            }

            if (offset != frame.size || generation.get() != session) return

            var energy = 0.0
            for (sample in frame) {
                val normalized = sample / 32768.0
                energy += normalized * normalized
            }

            val rms = sqrt(energy / frame.size)
            val dbFs = if (rms > 0.0) {
                (20.0 * log10(rms)).toFloat()
            } else {
                -120f
            }

            val dominant = fft.dominantFrequency(frame, SAMPLE_RATE)

            if (generation.get() == session) {
                onState(
                    AudioState(
                        running = true,
                        rmsDbFs = dbFs.coerceAtLeast(-120f),
                        dominantFrequencyHz = dominant,
                        sampleRateHz = SAMPLE_RATE,
                        sourceLabel = sourceLabel,
                        fallbackUsed = fallbackUsed,
                    ),
                )
            }
        }
    }

    private fun audioReadError(code: Int): String = when (code) {
        AudioRecord.ERROR_BAD_VALUE -> "AudioRecord retornou ERROR_BAD_VALUE."
        AudioRecord.ERROR_DEAD_OBJECT -> "O dispositivo de áudio foi desconectado."
        AudioRecord.ERROR_INVALID_OPERATION -> "Operação de captura de áudio inválida."
        AudioRecord.ERROR -> "Falha genérica durante captura de áudio."
        else -> "Falha de leitura de áudio: código $code."
    }
}
