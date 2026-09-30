package com.k410sh4.a25lab.model

import android.hardware.Sensor

data class DeviceSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val hardware: String,
    val socManufacturer: String,
    val socModel: String,
    val sdkInt: Int,
    val androidRelease: String,
    val securityPatch: String,
    val cpuCores: Int,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val refreshRateHz: Float,
    val nfcAvailable: Boolean,
    val bluetoothLeAvailable: Boolean,
    val gpsAvailable: Boolean,
    val cameraAvailable: Boolean,
    val microphoneAvailable: Boolean,
    val thermalStatus: Int,
    val batteryTemperatureC: Float?,
    val sensorCount: Int,
    val cameraCount: Int,
)

data class SensorInfo(
    val name: String,
    val vendor: String,
    val type: Int,
    val typeName: String,
    val version: Int,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val fifoMaxEventCount: Int,
    val reportingMode: Int,
    val wakeUp: Boolean,
)

data class MotionSample(
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 0f,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f,
    val mx: Float = 0f,
    val my: Float = 0f,
    val mz: Float = 0f,
)

data class GnssState(
    val running: Boolean = false,
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val rawMeasurementCount: Int = 0,
    val constellations: Set<String> = emptySet(),
    val lastError: String? = null,
)

data class BleDeviceInfo(
    val key: String,
    val name: String,
    val address: String,
    val rssi: Int,
    val connectable: Boolean?,
)

data class BleState(
    val scanning: Boolean = false,
    val devices: List<BleDeviceInfo> = emptyList(),
    val lastError: String? = null,
)

data class CameraInfo(
    val id: String,
    val facing: String,
    val hardwareLevel: String,
    val pixelArray: String,
    val maxJpeg: String,
    val rawSupported: Boolean,
    val manualSensor: Boolean,
    val manualPostProcessing: Boolean,
    val logicalMultiCamera: Boolean,
    val oisModes: List<String>,
)

data class AudioState(
    val running: Boolean = false,
    val rmsDbFs: Float = -120f,
    val dominantFrequencyHz: Float = 0f,
    val sampleRateHz: Int = 44_100,
    val lastError: String? = null,
)

data class NetworkState(
    val connected: Boolean = false,
    val transports: List<String> = emptyList(),
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    val validated: Boolean = false,
    val metered: Boolean = false,
)

data class NfcState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val lastTagIdHex: String? = null,
    val technologies: List<String> = emptyList(),
    val ndefText: String? = null,
)

fun sensorTypeName(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER -> "Acelerômetro"
    Sensor.TYPE_GYROSCOPE -> "Giroscópio"
    Sensor.TYPE_MAGNETIC_FIELD -> "Magnetômetro"
    Sensor.TYPE_LIGHT -> "Luz"
    Sensor.TYPE_PROXIMITY -> "Proximidade"
    Sensor.TYPE_PRESSURE -> "Pressão"
    Sensor.TYPE_GRAVITY -> "Gravidade"
    Sensor.TYPE_LINEAR_ACCELERATION -> "Aceleração linear"
    Sensor.TYPE_ROTATION_VECTOR -> "Vetor de rotação"
    Sensor.TYPE_GAME_ROTATION_VECTOR -> "Rotação de jogo"
    Sensor.TYPE_STEP_COUNTER -> "Contador de passos"
    Sensor.TYPE_STEP_DETECTOR -> "Detector de passos"
    else -> "Tipo $type"
}
