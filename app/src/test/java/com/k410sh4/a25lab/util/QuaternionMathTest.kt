package com.k410sh4.a25lab.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class QuaternionMathTest {
    @Test
    fun relative_sameQuaternion_isIdentity() {
        val q = Quaternion(0.9f, 0.1f, 0.2f, 0.3f)
        val result = QuaternionMath.relative(q, q)
        assertEquals(1f, result.w, 0.0001f)
        assertEquals(0f, result.x, 0.0001f)
        assertEquals(0f, result.y, 0.0001f)
        assertEquals(0f, result.z, 0.0001f)
    }

    @Test
    fun rotate_quarterTurnAroundZ_rotatesXAxisToYAxis() {
        val half = (PI / 4.0).toFloat()
        val q = Quaternion(cos(half), 0f, 0f, sin(half))
        val result = QuaternionMath.rotate(Vec3(1f, 0f, 0f), q)
        assertEquals(0f, result.x, 0.0001f)
        assertEquals(1f, result.y, 0.0001f)
        assertEquals(0f, result.z, 0.0001f)
    }

    @Test
    fun nlerp_staysNormalized() {
        val result = QuaternionMath.nlerp(
            QuaternionMath.Identity,
            Quaternion(0f, 1f, 0f, 0f),
            0.5f,
        )
        val norm = result.w * result.w + result.x * result.x + result.y * result.y + result.z * result.z
        assertEquals(1f, norm, 0.0001f)
    }
}
