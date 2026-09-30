package com.k410sh4.a25lab.util

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float)
data class Vec3(val x: Float, val y: Float, val z: Float)

object ThreeDMath {
    fun clampMagnitude2D(x: Float, y: Float, maxLength: Float): Vec2 {
        if (!x.isFinite() || !y.isFinite() || !maxLength.isFinite()) return Vec2(0f, 0f)
        val limit = maxLength.coerceAtLeast(0f)
        val length = sqrt(x * x + y * y)
        if (length <= 0.000001f || length <= limit) return Vec2(x, y)
        val scale = limit / length
        return Vec2(x * scale, y * scale)
    }

    fun rotateEuler(
        point: Vec3,
        yawRad: Float,
        pitchRad: Float,
        rollRad: Float,
    ): Vec3 {
        val cy = cos(yawRad)
        val sy = sin(yawRad)
        val cp = cos(pitchRad)
        val sp = sin(pitchRad)
        val cr = cos(rollRad)
        val sr = sin(rollRad)

        val x1 = point.x * cy - point.z * sy
        val z1 = point.x * sy + point.z * cy

        val y2 = point.y * cp - z1 * sp
        val z2 = point.y * sp + z1 * cp

        val x3 = x1 * cr - y2 * sr
        val y3 = x1 * sr + y2 * cr

        return Vec3(x3, y3, z2)
    }

    fun normalize(point: Vec3): Vec3 {
        if (!point.x.isFinite() ||
            !point.y.isFinite() ||
            !point.z.isFinite()
        ) {
            return Vec3(0f, 0f, 0f)
        }

        val length = sqrt(
            point.x * point.x + point.y * point.y + point.z * point.z,
        )
        if (!length.isFinite() || length <= 0.000001f) {
            return Vec3(0f, 0f, 0f)
        }

        return Vec3(
            point.x / length,
            point.y / length,
            point.z / length,
        )
    }
}
