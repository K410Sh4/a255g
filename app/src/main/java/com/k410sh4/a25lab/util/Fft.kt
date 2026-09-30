package com.k410sh4.a25lab.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FftAnalyzer(private val size: Int) {
    private val real = DoubleArray(size)
    private val imag = DoubleArray(size)
    private val window = DoubleArray(size) { index ->
        if (size <= 1) 1.0 else 0.5 - 0.5 * cos(2.0 * PI * index / (size - 1))
    }

    init {
        require(size > 0 && size and (size - 1) == 0) {
            "FFT size must be a power of two"
        }
    }

    fun dominantFrequency(samples: ShortArray, sampleRate: Int): Float {
        require(samples.size == size) { "Sample count must equal FFT size" }
        require(sampleRate > 0) { "sampleRate must be positive" }

        imag.fill(0.0)
        var mean = 0.0
        for (sample in samples) mean += sample.toDouble()
        mean /= size.toDouble()

        for (i in 0 until size) {
            real[i] = (samples[i].toDouble() - mean) * window[i]
        }

        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]
                real[i] = real[j]
                real[j] = tr
                val ti = imag[i]
                imag[i] = imag[j]
                imag[j] = ti
            }
        }

        var len = 2
        while (len <= size) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var i = 0
            while (i < size) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until len / 2) {
                    val even = i + k
                    val odd = even + len / 2
                    val vr = real[odd] * wr - imag[odd] * wi
                    val vi = real[odd] * wi + imag[odd] * wr
                    val ur = real[even]
                    val ui = imag[even]
                    real[even] = ur + vr
                    imag[even] = ui + vi
                    real[odd] = ur - vr
                    imag[odd] = ui - vi

                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextWr
                }
                i += len
            }
            len = len shl 1
        }

        var bestBin = 0
        var bestMagnitudeSquared = 0.0
        for (bin in 1..size / 2) {
            val magnitudeSquared =
                real[bin] * real[bin] + imag[bin] * imag[bin]
            if (magnitudeSquared > bestMagnitudeSquared) {
                bestMagnitudeSquared = magnitudeSquared
                bestBin = bin
            }
        }

        if (bestBin == 0 || bestMagnitudeSquared <= 1.0e-12) return 0f
        return bestBin * sampleRate.toFloat() / size.toFloat()
    }
}

object Fft {
    fun dominantFrequency(samples: ShortArray, sampleRate: Int): Float =
        FftAnalyzer(samples.size).dominantFrequency(samples, sampleRate)
}
