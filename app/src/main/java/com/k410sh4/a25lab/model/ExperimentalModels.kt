package com.k410sh4.a25lab.model

data class SensorStreamMetrics(
    val available: Boolean = false,
    val registered: Boolean = false,
    val sampleReady: Boolean = false,
    val name: String = "N/D",
    val stringType: String = "",
    val declaredMinDelayUs: Int? = null,
    val eventCount: Long = 0L,
    val observedHz: Float = 0f,
    val jitterMs: Float = 0f,
    val vectorSize: Int = 0,
    val rmsMagnitude: Float = 0f,
    val peakMagnitude: Float = 0f,
    val lastValues: List<Float> = emptyList(),
    val registrationError: String? = null,
)

data class VibrationLabState(
    val running: Boolean = false,
    val sourceLabel: String = "N/D",
    val acceleration: SensorStreamMetrics = SensorStreamMetrics(),
    val gyroscope: SensorStreamMetrics = SensorStreamMetrics(),
    val aois: SensorStreamMetrics = SensorStreamMetrics(),
    val dominantFrequencyHz: Float = 0f,
    val dominantConfidence: Float = 0f,
    val lastError: String? = null,
)

data class StabilizationLabState(
    val running: Boolean = false,
    val physicalGyro: SensorStreamMetrics = SensorStreamMetrics(),
    val aois: SensorStreamMetrics = SensorStreamMetrics(),
    val vdis: SensorStreamMetrics = SensorStreamMetrics(),
    val lastError: String? = null,
)

data class MagneticMapperState(
    val running: Boolean = false,
    val magneticReady: Boolean = false,
    val orientationReady: Boolean = false,
    val currentStrengthUt: Float = 0f,
    val worldXUt: Float = 0f,
    val worldYUt: Float = 0f,
    val worldZUt: Float = 0f,
    val cellsUt: List<Float?> = List(25) { null },
    val nextIndex: Int = 0,
    val minStrengthUt: Float? = null,
    val maxStrengthUt: Float? = null,
    val strongestIndex: Int? = null,
    val lastError: String? = null,
)

data class SensorQualificationState(
    val running: Boolean = false,
    val sensorType: Int? = null,
    val sensorName: String = "Nenhum sensor selecionado",
    val stringType: String = "",
    val metrics: SensorStreamMetrics = SensorStreamMetrics(),
    val lastError: String? = null,
)
