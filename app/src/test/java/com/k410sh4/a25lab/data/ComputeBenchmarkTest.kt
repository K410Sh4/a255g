package com.k410sh4.a25lab.data

import java.util.concurrent.CancellationException

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeBenchmarkTest {
    @Test
    fun run_cancelsWhenThreadIsInterrupted() {
        Thread.currentThread().interrupt()
        try {
            org.junit.Assert.assertThrows(
                CancellationException::class.java,
            ) {
                ComputeBenchmark().run(
                    size = 32,
                    warmupIterations = 0,
                    measuredIterations = 1,
                )
            }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun run_isDeterministicForSameInputs() {
        val benchmark = ComputeBenchmark()
        val first = benchmark.run(
            size = 32,
            warmupIterations = 0,
            measuredIterations = 1,
        )
        val second = benchmark.run(
            size = 32,
            warmupIterations = 0,
            measuredIterations = 1,
        )

        assertEquals(first.checksum, second.checksum, 0.000001)
        assertEquals(32, first.matrixSize)
        assertEquals(1, first.measuredIterations)
        assertTrue(first.estimatedGflops > 0.0)
    }
}
