package com.k410sh4.a25lab.util

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreeDMathTest {
    @Test
    fun clampMagnitude2D_limitsProjectedVector() {
        val result = ThreeDMath.clampMagnitude2D(3f, 4f, 2f)
        assertEquals(1.2f, result.x, 0.0001f)
        assertEquals(1.6f, result.y, 0.0001f)
    }

    @Test
    fun normalize_returnsUnitVector() {
        val result = ThreeDMath.normalize(Vec3(3f, 4f, 0f))
        assertEquals(0.6f, result.x, 0.0001f)
        assertEquals(0.8f, result.y, 0.0001f)
        assertEquals(0f, result.z, 0.0001f)
    }

    @Test
    fun normalize_nonFiniteVectorReturnsZero() {
        val result = ThreeDMath.normalize(
            Vec3(Float.POSITIVE_INFINITY, 1f, 2f),
        )

        assertEquals(0f, result.x, 0f)
        assertEquals(0f, result.y, 0f)
        assertEquals(0f, result.z, 0f)
    }

    @Test
    fun yawQuarterTurn_rotatesForwardAxis() {
        val result = ThreeDMath.rotateEuler(
            point = Vec3(0f, 0f, 1f),
            yawRad = (PI / 2.0).toFloat(),
            pitchRad = 0f,
            rollRad = 0f,
        )
        assertEquals(-1f, result.x, 0.0001f)
        assertEquals(0f, result.y, 0.0001f)
        assertEquals(0f, result.z, 0.0001f)
    }
}
