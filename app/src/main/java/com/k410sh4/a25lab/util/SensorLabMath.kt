package com.k410sh4.a25lab.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SpectrumPeak(
    val frequencyHz: Float = 0f,
    val confidence: Float = 0f,
)

object SensorLabMath {
    fun magnitude3(values: FloatArray): Float {
        if (values.size < 3) return 0f
        val x = values[0].toDouble()
        val y = values[1].toDouble()
        val z = values[2].toDouble()
        return sqrt(x * x + y * y + z * z).toFloat()
    }

    fun dominantFrequency(
        samples: List<Float>,
        sampleRateHz: Float,
    ): SpectrumPeak {
        val n = samples.size
        if (n < 32 || !sampleRateHz.isFinite() || sampleRateHz <= 0f) {
            return SpectrumPeak()
        }

        val mean = samples.sumOf { it.toDouble() } / n.toDouble()
        var bestPower = 0.0
        var bestBin = 0
        var totalPower = 0.0
        val maxBin = n / 2

        for (bin in 1..maxBin) {
            var real = 0.0
            var imag = 0.0
            for (i in 0 until n) {
                val window = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1).toDouble())
                val value = (samples[i] - mean) * window
                val angle = 2.0 * PI * bin * i / n.toDouble()
                real += value * cos(angle)
                imag -= value * sin(angle)
            }
            val power = real * real + imag * imag
            totalPower += power
            if (power > bestPower) {
                bestPower = power
                bestBin = bin
            }
        }

        if (bestBin == 0 || totalPower <= 0.0) return SpectrumPeak()

        return SpectrumPeak(
            frequencyHz = bestBin * sampleRateHz / n.toFloat(),
            confidence = (bestPower / totalPower)
                .coerceIn(0.0, 1.0)
                .toFloat(),
        )
    }
}
