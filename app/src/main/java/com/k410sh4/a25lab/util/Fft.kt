package com.k410sh4.a25lab.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Fft {
    fun dominantFrequency(samples: ShortArray, sampleRate: Int): Float {
        require(samples.isNotEmpty() && samples.size and (samples.size - 1) == 0) {
            "FFT size must be a power of two"
        }
        require(sampleRate > 0) { "sampleRate must be positive" }

        val n = samples.size
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        for (i in 0 until n) {
            val window = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
            real[i] = samples[i].toDouble() * window
        }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
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
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var i = 0
            while (i < n) {
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

        var bestBin = 1
        var bestMagnitude = 0.0
        for (bin in 1 until n / 2) {
            val magnitude = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
            if (magnitude > bestMagnitude) {
                bestMagnitude = magnitude
                bestBin = bin
            }
        }

        return bestBin * sampleRate.toFloat() / n.toFloat()
    }
}
