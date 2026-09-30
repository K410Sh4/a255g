package com.k410sh4.a25lab.model

import android.hardware.Sensor

data class DeviceSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String,
    val bootloader: String,
    val buildId: String,
    val buildDisplay: String,
    val buildFingerprint: String,
    val socManufacturer: String,
    val socModel: String,
    val supportedAbis: List<String>,
    val supported32BitAbis: List<String>,
    val supported64BitAbis: List<String>,
    val kernelVersion: String,
    val sdkInt: Int,
    val androidRelease: String,
    val securityPatch: String,
    val cpuCores: Int,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val lowRamDevice: Boolean,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val glEsVersion: String,
    val nfcAvailable: Boolean,
    val bluetoothLeAvailable: Boolean,
    val gpsAvailable: Boolean,
    val cameraAvailable: Boolean,
    val microphoneAvailable: Boolean,
    val thermalStatus: Int,
    val batteryTemperatureC: Float?,
    val batteryVoltageMv: Int?,
    val batteryLevelPercent: Int?,
    val batteryHealth: Int?,
    val batteryStatus: Int?,
    val batteryTechnology: String?,
    val sensorCount: Int,
    val cameraCount: Int,
    val systemFeatureCount: Int,
)

data class SystemFeatureInfo(
    val name: String,
    val version: Int,
)

data class SensorInfo(
    val name: String,
    val vendor: String,
    val type: Int,
    val stringType: String,
    val typeName: String,
    val version: Int,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val fifoReservedEventCount: Int,
    val fifoMaxEventCount: Int,
    val reportingMode: Int,
    val wakeUp: Boolean,
    val dynamic: Boolean,
    val additionalInfoSupported: Boolean,
)

data class MotionSample(
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 0f,
    val accelerometerStreamActive: Boolean = false,
    val accelerometerSampleReady: Boolean = false,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f,
    val gyroscopeStreamActive: Boolean = false,
    val gyroscopeSampleReady: Boolean = false,
    val mx: Float = 0f,
    val my: Float = 0f,
    val mz: Float = 0f,
    val magnetometerStreamActive: Boolean = false,
    val magnetometerSampleReady: Boolean = false,
)

data class GnssState(
    val running: Boolean = false,
    val locationEnabled: Boolean? = null,
    val gpsProviderEnabled: Boolean? = null,
    val engineActive: Boolean = false,
    val rawMeasurementsSupported: Boolean? = null,
    val rawMeasurementsActive: Boolean = false,
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val rawMeasurementCount: Int = 0,
    val constellations: Set<String> = emptySet(),
    val lastError: String? = null,
)

data class BleDeviceInfo(
    val key: String,
    val name: String,
    val rssi: Int,
    val connectable: Boolean?,
    val lastSeenElapsedMs: Long = 0L,
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

data class CameraProbeResult(
    val totalIds: Int = 0,
    val cameras: List<CameraInfo> = emptyList(),
    val errors: List<String> = emptyList(),
)

data class AudioState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val rmsDbFs: Float = -120f,
    val dominantFrequencyHz: Float = 0f,
    val sampleRateHz: Int = 44_100,
    val sourceLabel: String = "N/D",
    val fallbackUsed: Boolean = false,
    val lastError: String? = null,
)

data class NetworkState(
    val connected: Boolean = false,
    val internetCapability: Boolean = false,
    val validated: Boolean = false,
    val captivePortal: Boolean = false,
    val transports: List<String> = emptyList(),
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    val metered: Boolean = false,
    val lastError: String? = null,
)

data class NfcState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val lastTagIdHex: String? = null,
    val technologies: List<String> = emptyList(),
    val ndefText: String? = null,
    val lastError: String? = null,
)

fun sensorTypeName(type: Int): String = when (type) {
    Sensor.TYPE_ACCELEROMETER -> "Acelerômetro"
    Sensor.TYPE_MAGNETIC_FIELD -> "Magnetômetro"
    3 -> "Orientação"
    Sensor.TYPE_GYROSCOPE -> "Giroscópio"
    Sensor.TYPE_LIGHT -> "Luz"
    Sensor.TYPE_PRESSURE -> "Pressão"
    Sensor.TYPE_PROXIMITY -> "Proximidade"
    Sensor.TYPE_GRAVITY -> "Gravidade"
    Sensor.TYPE_LINEAR_ACCELERATION -> "Aceleração linear"
    Sensor.TYPE_ROTATION_VECTOR -> "Vetor de rotação"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "Umidade relativa"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperatura ambiente"
    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetômetro não calibrado"
    Sensor.TYPE_GAME_ROTATION_VECTOR -> "Rotação de jogo"
    Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Giroscópio não calibrado"
    Sensor.TYPE_SIGNIFICANT_MOTION -> "Movimento significativo"
    Sensor.TYPE_STEP_DETECTOR -> "Detector de passos"
    Sensor.TYPE_STEP_COUNTER -> "Contador de passos"
    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Rotação geomagnética"
    Sensor.TYPE_HEART_RATE -> "Frequência cardíaca"
    22 -> "Detector de inclinação"
    23 -> "Gesto de despertar"
    24 -> "Gesto de olhar"
    25 -> "Gesto de levantar"
    26 -> "Inclinação do pulso"
    27 -> "Orientação do dispositivo"
    28 -> "Pose 6DoF"
    29 -> "Detecção estacionária"
    30 -> "Detecção de movimento"
    31 -> "Batimento cardíaco"
    32 -> "Meta sensor dinâmico"
    33 -> "Informação adicional"
    34 -> "Baixa latência fora do corpo"
    35 -> "Acelerômetro não calibrado"
    36 -> "Ângulo de dobradiça"
    37 -> "Head tracker"
    38 -> "Acelerômetro de eixos limitados"
    39 -> "Giroscópio de eixos limitados"
    40 -> "Acelerômetro não calibrado de eixos limitados"
    41 -> "Giroscópio não calibrado de eixos limitados"
    42 -> "Heading"
    else -> if (type >= 65_536) "Sensor vendor tipo $type" else "Tipo $type"
}
