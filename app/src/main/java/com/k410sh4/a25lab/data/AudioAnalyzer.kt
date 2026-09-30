package com.k410sh4.a25lab.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.model.AudioState
import com.k410sh4.a25lab.util.Fft
import java.util.concurrent.Executors
import kotlin.math.log10
import kotlin.math.sqrt

class AudioAnalyzer(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private var recorder: AudioRecord? = null

    fun start(onState: (AudioState) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onState(AudioState(lastError = "Permissão de microfone necessária."))
            return
        }
        stop()
        val sampleRate = 44_100
        val fftSize = 2048
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            onState(AudioState(lastError = "Configuração de áudio não suportada."))
            return
        }
        try {
            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, fftSize * 4))
                .build()
            recorder = audioRecord
            running = true
            audioRecord.startRecording()
            onState(AudioState(running = true, sampleRateHz = sampleRate))
            executor.execute { loop(audioRecord, fftSize, sampleRate, onState) }
        } catch (error: Exception) {
            running = false
            onState(AudioState(lastError = error.message ?: "Falha ao abrir o microfone."))
        }
    }

    fun stop() {
        running = false
        val current = recorder
        recorder = null
        runCatching { current?.stop() }
        current?.release()
    }

    fun close() {
        stop()
        executor.shutdownNow()
    }

    private fun loop(record: AudioRecord, fftSize: Int, sampleRate: Int, onState: (AudioState) -> Unit) {
        val buffer = ShortArray(fftSize)
        while (running) {
            val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read != fftSize) continue
            var energy = 0.0
            for (sample in buffer) {
                val normalized = sample / 32768.0
                energy += normalized * normalized
            }
            val rms = sqrt(energy / buffer.size)
            val dbFs = if (rms > 0.0) (20.0 * log10(rms)).toFloat() else -120f
            val dominant = Fft.dominantFrequency(buffer, sampleRate)
            onState(
                AudioState(
                    running = true,
                    rmsDbFs = dbFs.coerceAtLeast(-120f),
                    dominantFrequencyHz = dominant,
                    sampleRateHz = sampleRate,
                ),
            )
        }
    }
}
