package com.k410sh4.a25lab.data

import java.util.Random

class ComputeBenchmark {
    companion object {
        private const val DEFAULT_WARMUP_ITERATIONS = 1
        private const val DEFAULT_MEASURED_ITERATIONS = 3
    }

    data class Result(
        val matrixSize: Int,
        val warmupIterations: Int,
        val measuredIterations: Int,
        val elapsedMs: Long,
        val estimatedGflops: Double,
        val checksum: Double,
    )

    fun run(
        size: Int = 160,
        warmupIterations: Int = DEFAULT_WARMUP_ITERATIONS,
        measuredIterations: Int = DEFAULT_MEASURED_ITERATIONS,
    ): Result {
        require(size in 32..512)
        require(warmupIterations in 0..5)
        require(measuredIterations in 1..9)

        val random = Random(25L)
        val a = FloatArray(size * size) {
            random.nextFloat() - 0.5f
        }
        val b = FloatArray(size * size) {
            random.nextFloat() - 0.5f
        }
        val c = FloatArray(size * size)

        repeat(warmupIterations) {
            c.fill(0f)
            multiply(a, b, c, size)
        }

        val samplesNs = LongArray(measuredIterations)
        repeat(measuredIterations) { iteration ->
            c.fill(0f)
            val start = System.nanoTime()
            multiply(a, b, c, size)
            samplesNs[iteration] = (System.nanoTime() - start)
                .coerceAtLeast(1L)
        }

        val sortedNs = samplesNs.sortedArray()
        val middle = sortedNs.size / 2
        val medianNs = if (sortedNs.size % 2 == 1) {
            sortedNs[middle]
        } else {
            (sortedNs[middle - 1] + sortedNs[middle]) / 2L
        }
        val operations = 2.0 * size * size * size
        val gflops = operations / medianNs
        val checksum = c.asSequence()
            .map { it.toDouble() }
            .sum()

        return Result(
            matrixSize = size,
            warmupIterations = warmupIterations,
            measuredIterations = measuredIterations,
            elapsedMs = medianNs / 1_000_000,
            estimatedGflops = gflops,
            checksum = checksum,
        )
    }

    private fun multiply(
        a: FloatArray,
        b: FloatArray,
        c: FloatArray,
        size: Int,
    ) {
        for (i in 0 until size) {
            val row = i * size
            for (k in 0 until size) {
                val aik = a[row + k]
                val bRow = k * size
                for (j in 0 until size) {
                    c[row + j] += aik * b[bRow + j]
                }
            }
        }
    }
}
