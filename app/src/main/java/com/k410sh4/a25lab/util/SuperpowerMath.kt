package com.k410sh4.a25lab.util

import kotlin.math.abs
import kotlin.math.sqrt

object SuperpowerMath {
    private const val GRAVITY_EARTH = 9.80665f

    fun magnitude3(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)

    fun dynamicAcceleration(ax: Float, ay: Float, az: Float): Float =
        abs(magnitude3(ax, ay, az) - GRAVITY_EARTH)

    fun motionLevel(dynamicAccelerationMs2: Float, angularSpeedRadS: Float): String = when {
        dynamicAccelerationMs2 < 0.15f && angularSpeedRadS < 0.08f -> "quase parado"
        dynamicAccelerationMs2 < 0.8f && angularSpeedRadS < 0.5f -> "movimento leve"
        dynamicAccelerationMs2 < 2.5f && angularSpeedRadS < 1.5f -> "movimento moderado"
        else -> "movimento forte"
    }

    fun rssiBand(rssi: Int): String = when {
        rssi >= -50 -> "muito forte"
        rssi >= -65 -> "forte"
        rssi >= -78 -> "médio"
        else -> "fraco"
    }
}
