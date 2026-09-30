package com.k410sh4.a25lab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class ComputeBenchmarkTest {
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

    @Test
    fun run_stopsWhenCancellationIsRequested() {
        var cancelled = false

        try {
            ComputeBenchmark().run(
                size = 32,
                warmupIterations = 0,
                measuredIterations = 1,
                shouldCancel = { true },
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }
}
