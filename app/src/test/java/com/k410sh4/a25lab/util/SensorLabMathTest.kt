package com.k410sh4.a25lab.util

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorLabMathTest {
    @Test
    fun dominantFrequency_findsSyntheticTwentyHertzSignal() {
        val sampleRate = 200f
        val samples = List(200) { index ->
            sin(2.0 * PI * 20.0 * index / sampleRate.toDouble()).toFloat()
        }

        val peak = SensorLabMath.dominantFrequency(samples, sampleRate)

        assertEquals(20f, peak.frequencyHz, 1.1f)
        assertTrue(peak.confidence > 0.4f)
    }

    @Test
    fun dominantFrequency_rejectsTooShortWindow() {
        val peak = SensorLabMath.dominantFrequency(
            samples = List(16) { 1f },
            sampleRateHz = 100f,
        )

        assertEquals(0f, peak.frequencyHz, 0f)
        assertEquals(0f, peak.confidence, 0f)
    }
}
