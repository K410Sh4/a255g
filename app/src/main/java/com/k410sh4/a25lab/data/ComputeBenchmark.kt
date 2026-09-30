package com.k410sh4.a25lab.data

import android.os.SystemClock
import java.util.Random

class ComputeBenchmark {
    data class Result(
        val matrixSize: Int,
        val elapsedMs: Long,
        val estimatedGflops: Double,
        val checksum: Double,
    )

    fun run(size: Int = 160): Result {
        require(size in 32..512)
        val random = Random(25L)
        val a = FloatArray(size * size) { random.nextFloat() - 0.5f }
        val b = FloatArray(size * size) { random.nextFloat() - 0.5f }
        val c = FloatArray(size * size)
        val start = SystemClock.elapsedRealtimeNanos()
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
        val elapsedNs = (SystemClock.elapsedRealtimeNanos() - start).coerceAtLeast(1L)
        val operations = 2.0 * size * size * size
        val gflops = operations / elapsedNs
        val checksum = c.asSequence().map { it.toDouble() }.sum()
        return Result(
            matrixSize = size,
            elapsedMs = elapsedNs / 1_000_000,
            estimatedGflops = gflops,
            checksum = checksum,
        )
    }
}
