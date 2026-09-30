package com.k410sh4.a25lab.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FftTest {
    @Test
    fun dominantFrequency_detectsOneKhzTone() {
        val sampleRate = 8192
        val size = 2048
        val frequency = 1000.0
        val samples = ShortArray(size) { index ->
            (sin(2.0 * PI * frequency * index / sampleRate) * 20_000.0).toInt().toShort()
        }

        val detected = Fft.dominantFrequency(samples, sampleRate)

        assertEquals(1000f, detected, 4.1f)
    }

    @Test
    fun dominantFrequency_rejectsNonPowerOfTwo() {
        assertThrows(IllegalArgumentException::class.java) {
            Fft.dominantFrequency(ShortArray(1000), 44_100)
        }
    }
}
