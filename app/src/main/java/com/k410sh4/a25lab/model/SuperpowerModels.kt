package com.k410sh4.a25lab.model

data class SuperpowerSensorState(
    val magneticXUt: Float = 0f,
    val magneticYUt: Float = 0f,
    val magneticZUt: Float = 0f,
    val magneticStrengthUt: Float = 0f,
    val dynamicAccelerationMs2: Float = 0f,
    val angularSpeedRadS: Float = 0f,
    val lightLux: Float? = null,
    val cctRaw: Float? = null,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val orientationAvailable: Boolean = false,
    val cctSensorAvailable: Boolean = false,
    val cctStreamActive: Boolean = false,
    val aoisAvailable: Boolean = false,
    val aoisMinDelayUs: Int? = null,
    val vdisAvailable: Boolean = false,
    val vdisMinDelayUs: Int? = null,
)
