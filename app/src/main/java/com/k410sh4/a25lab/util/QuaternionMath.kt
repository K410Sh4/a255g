package com.k410sh4.a25lab.util

import kotlin.math.exp
import kotlin.math.sqrt

data class Quaternion(
    val w: Float,
    val x: Float,
    val y: Float,
    val z: Float,
)

object QuaternionMath {
    val Identity = Quaternion(1f, 0f, 0f, 0f)

    fun normalize(q: Quaternion): Quaternion {
        if (!q.w.isFinite() ||
            !q.x.isFinite() ||
            !q.y.isFinite() ||
            !q.z.isFinite()
        ) {
            return Identity
        }

        val length = sqrt(
            q.w * q.w + q.x * q.x + q.y * q.y + q.z * q.z,
        )
        if (!length.isFinite() || length <= 0.000001f) {
            return Identity
        }

        return Quaternion(
            q.w / length,
            q.x / length,
            q.y / length,
            q.z / length,
        )
    }

    fun conjugate(q: Quaternion): Quaternion =
        Quaternion(q.w, -q.x, -q.y, -q.z)

    fun multiply(a: Quaternion, b: Quaternion): Quaternion = Quaternion(
        w = a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
        x = a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
        y = a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
        z = a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
    )

    fun relative(reference: Quaternion, current: Quaternion): Quaternion =
        normalize(multiply(conjugate(normalize(reference)), normalize(current)))

    fun rotate(point: Vec3, rotation: Quaternion): Vec3 {
        val q = normalize(rotation)
        val p = Quaternion(0f, point.x, point.y, point.z)
        val rotated = multiply(multiply(q, p), conjugate(q))
        return Vec3(rotated.x, rotated.y, rotated.z)
    }

    fun nlerp(from: Quaternion, to: Quaternion, amount: Float): Quaternion {
        val t = amount.coerceIn(0f, 1f)
        val dot = from.w * to.w + from.x * to.x + from.y * to.y + from.z * to.z
        val target = if (dot < 0f) Quaternion(-to.w, -to.x, -to.y, -to.z) else to
        return normalize(
            Quaternion(
                w = from.w + (target.w - from.w) * t,
                x = from.x + (target.x - from.x) * t,
                y = from.y + (target.y - from.y) * t,
                z = from.z + (target.z - from.z) * t,
            ),
        )
    }

    fun smoothingAlpha(
        deltaTimeNs: Long,
        timeConstantMs: Float = 65f,
    ): Float {
        if (timeConstantMs <= 0f) return 1f
        if (deltaTimeNs <= 0L) return 0.35f

        val deltaSeconds = deltaTimeNs / 1_000_000_000.0
        val tauSeconds = timeConstantMs / 1000.0
        return (1.0 - exp(-deltaSeconds / tauSeconds))
            .toFloat()
            .coerceIn(0f, 1f)
    }
}
