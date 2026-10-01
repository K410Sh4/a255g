package com.k410sh4.a25lab.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.k410sh4.a25lab.model.MagneticMapperState
import com.k410sh4.a25lab.model.SensorQualificationState
import com.k410sh4.a25lab.model.SensorStreamMetrics
import com.k410sh4.a25lab.model.StabilizationLabState
import com.k410sh4.a25lab.model.VibrationLabState
import com.k410sh4.a25lab.util.SensorLabMath
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

class ExperimentalSensorLab(context: Context) : SensorEventListener {
    companion object {
        private const val AOIS_STRING_TYPE = "com.samsung.sensor.gyroscope_aois"
        private const val VDIS_STRING_TYPE = "com.samsung.sensor.vdis_gyro"
        private const val PUBLISH_INTERVAL_NS = 100_000_000L
        private const val VIBRATION_WINDOW = 256
    }

    private enum class Mode {
        Idle,
        Vibration,
        Stabilization,
        MagneticMapper,
        Qualification,
    }

    private val manager = context.getSystemService(SensorManager::class.java)
    private val thread = HandlerThread("A25ExperimentalSensors").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var mode = Mode.Idle

    private var vibrationCallback: ((VibrationLabState) -> Unit)? = null
    private var stabilizationCallback: ((StabilizationLabState) -> Unit)? = null
    private var mapperCallback: ((MagneticMapperState) -> Unit)? = null
    private var qualificationCallback: ((SensorQualificationState) -> Unit)? = null

    private var vibrationSensor: Sensor? = null
    private var vibrationUsesLinear = false
    private var vibrationAccelerationStats = StreamStats(null)
    private var vibrationGyroStats = StreamStats(null)
    private var vibrationAoisStats = StreamStats(null)
    private val vibrationSamples = ArrayDeque<Float>()
    private var lastVibrationPublishNs = 0L

    private var stabilizationGyro: Sensor? = null
    private var stabilizationAois: Sensor? = null
    private var stabilizationVdis: Sensor? = null
    private var stabilizationGyroStats = StreamStats(null)
    private var stabilizationAoisStats = StreamStats(null)
    private var stabilizationVdisStats = StreamStats(null)
    private var lastStabilizationPublishNs = 0L

    private var qualificationSensor: Sensor? = null
    private var qualificationStats = StreamStats(null)
    private var lastQualificationPublishNs = 0L

    private var mapperMagnetic: Sensor? = null
    private var mapperRotation: Sensor? = null
    private val mapperRotationMatrix = FloatArray(9)
    private var mapperOrientationReady = false
    private val mapperLock = Any()

    @Volatile
    private var mapperState = MagneticMapperState()

    fun startVibration(onState: (VibrationLabState) -> Unit) {
        stop()
        mode = Mode.Vibration
        vibrationCallback = onState
        vibrationSamples.clear()
        lastVibrationPublishNs = 0L

        val linear = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        vibrationSensor = linear ?: accelerometer
        vibrationUsesLinear = linear != null
        val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val aois = findByStringType(AOIS_STRING_TYPE)

        vibrationAccelerationStats = StreamStats(vibrationSensor)
        vibrationGyroStats = StreamStats(gyro)
        vibrationAoisStats = StreamStats(aois)

        val accelRegistered = registerFast(vibrationSensor)
        val gyroRegistered = registerFast(gyro)
        val aoisRegistered = registerFast(aois)

        vibrationAccelerationStats.registered = accelRegistered
        vibrationGyroStats.registered = gyroRegistered
        vibrationAoisStats.registered = aoisRegistered

        onState(vibrationSnapshot())
    }

    fun startStabilization(onState: (StabilizationLabState) -> Unit) {
        stop()
        mode = Mode.Stabilization
        stabilizationCallback = onState
        lastStabilizationPublishNs = 0L

        stabilizationGyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        stabilizationAois = findByStringType(AOIS_STRING_TYPE)
        stabilizationVdis = findByStringType(VDIS_STRING_TYPE)
        stabilizationGyroStats = StreamStats(stabilizationGyro)
        stabilizationAoisStats = StreamStats(stabilizationAois)
        stabilizationVdisStats = StreamStats(stabilizationVdis)

        stabilizationGyroStats.registered = registerFast(stabilizationGyro)
        stabilizationAoisStats.registered = registerFast(stabilizationAois)
        stabilizationVdisStats.registered = registerFast(stabilizationVdis)

        onState(stabilizationSnapshot())
    }

    fun startMagneticMapper(
        initialCells: List<Float?> = List(25) { null },
        onState: (MagneticMapperState) -> Unit,
    ) {
        stop()
        mode = Mode.MagneticMapper
        mapperCallback = onState
        mapperMagnetic = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mapperRotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        mapperOrientationReady = false

        val normalizedCells = List(25) { index -> initialCells.getOrNull(index) }
        mapperState = mapperStateForCells(
            MagneticMapperState(
                running = true,
                cellsUt = normalizedCells,
                nextIndex = normalizedCells.indexOfFirst { it == null }
                    .let { if (it < 0) 25 else it },
            ),
        )

        val magneticRegistered = registerFast(mapperMagnetic)
        val rotationRegistered = registerFast(mapperRotation)
        mapperState = mapperState.copy(
            running = magneticRegistered || rotationRegistered,
            lastError = when {
                mapperMagnetic == null -> "Magnetômetro indisponível."
                !magneticRegistered -> "Não foi possível registrar o magnetômetro."
                mapperRotation == null -> "Rotation vector indisponível; intensidade ainda pode ser mapeada."
                else -> null
            },
        )
        onState(mapperState)
    }

    fun captureMagneticCell() {
        if (mode != Mode.MagneticMapper) return
        synchronized(mapperLock) {
            val current = mapperState
            if (!current.magneticReady || current.nextIndex !in 0..24) return
            val cells = current.cellsUt.toMutableList()
            cells[current.nextIndex] = current.currentStrengthUt
            val next = cells.indexOfFirst { it == null }.let {
                if (it < 0) 25 else it
            }
            mapperState = mapperStateForCells(
                current.copy(
                    cellsUt = cells,
                    nextIndex = next,
                ),
            )
            mapperCallback?.invoke(mapperState)
        }
    }

    fun resetMagneticGrid() {
        if (mode != Mode.MagneticMapper) return
        synchronized(mapperLock) {
            mapperState = mapperState.copy(
                cellsUt = List(25) { null },
                nextIndex = 0,
                minStrengthUt = null,
                maxStrengthUt = null,
                strongestIndex = null,
            )
            mapperCallback?.invoke(mapperState)
        }
    }

    fun startQualification(
        type: Int,
        name: String,
        onState: (SensorQualificationState) -> Unit,
    ) {
        stop()
        mode = Mode.Qualification
        qualificationCallback = onState
        lastQualificationPublishNs = 0L

        qualificationSensor = manager.getSensorList(Sensor.TYPE_ALL)
            .firstOrNull { it.type == type && it.name == name }
        qualificationStats = StreamStats(qualificationSensor)
        qualificationStats.registered = registerFast(qualificationSensor)

        onState(
            SensorQualificationState(
                running = qualificationStats.registered,
                sensorType = type,
                sensorName = qualificationSensor?.name ?: name,
                stringType = qualificationSensor?.stringType.orEmpty(),
                metrics = qualificationStats.snapshot(),
                lastError = when {
                    qualificationSensor == null -> "Sensor não encontrado."
                    !qualificationStats.registered -> "Falha ao registrar o sensor."
                    else -> null
                },
            ),
        )
    }

    fun stop() {
        manager.unregisterListener(this)
        mode = Mode.Idle
        vibrationCallback = null
        stabilizationCallback = null
        mapperCallback = null
        qualificationCallback = null
    }

    fun close() {
        stop()
        thread.quitSafely()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (mode) {
            Mode.Vibration -> updateVibration(event)
            Mode.Stabilization -> updateStabilization(event)
            Mode.MagneticMapper -> updateMagneticMapper(event)
            Mode.Qualification -> updateQualification(event)
            Mode.Idle -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateVibration(event: SensorEvent) {
        val values = event.values
        if (values.isEmpty() || values.any { !it.isFinite() }) return

        when (event.sensor) {
            vibrationSensor -> {
                val magnitude = if (vibrationUsesLinear) {
                    SensorLabMath.magnitude3(values)
                } else {
                    abs(SensorLabMath.magnitude3(values) - SensorManager.GRAVITY_EARTH)
                }
                vibrationAccelerationStats.add(event.timestamp, values, magnitude)
                vibrationSamples.addLast(magnitude)
                while (vibrationSamples.size > VIBRATION_WINDOW) {
                    vibrationSamples.removeFirst()
                }
            }
            vibrationGyroStats.sensor ->
                vibrationGyroStats.add(event.timestamp, values)
            vibrationAoisStats.sensor ->
                vibrationAoisStats.add(event.timestamp, values)
        }

        if (lastVibrationPublishNs == 0L ||
            event.timestamp - lastVibrationPublishNs >= PUBLISH_INTERVAL_NS
        ) {
            lastVibrationPublishNs = event.timestamp
            vibrationCallback?.invoke(vibrationSnapshot())
        }
    }

    private fun updateStabilization(event: SensorEvent) {
        val values = event.values
        if (values.isEmpty() || values.any { !it.isFinite() }) return

        when (event.sensor) {
            stabilizationGyro -> stabilizationGyroStats.add(event.timestamp, values)
            stabilizationAois -> stabilizationAoisStats.add(event.timestamp, values)
            stabilizationVdis -> stabilizationVdisStats.add(event.timestamp, values)
        }

        if (lastStabilizationPublishNs == 0L ||
            event.timestamp - lastStabilizationPublishNs >= PUBLISH_INTERVAL_NS
        ) {
            lastStabilizationPublishNs = event.timestamp
            stabilizationCallback?.invoke(stabilizationSnapshot())
        }
    }

    private fun updateMagneticMapper(event: SensorEvent) {
        val values = event.values
        if (values.isEmpty() || values.any { !it.isFinite() }) return

        synchronized(mapperLock) {
            if (event.sensor == mapperRotation && values.size >= 3) {
                SensorManager.getRotationMatrixFromVector(mapperRotationMatrix, values)
                mapperOrientationReady = true
                mapperState = mapperState.copy(orientationReady = true)
            } else if (event.sensor == mapperMagnetic && values.size >= 3) {
                val strength = SensorLabMath.magnitude3(values)
                var worldX = 0f
                var worldY = 0f
                var worldZ = 0f
                if (mapperOrientationReady) {
                    worldX = mapperRotationMatrix[0] * values[0] +
                        mapperRotationMatrix[1] * values[1] +
                        mapperRotationMatrix[2] * values[2]
                    worldY = mapperRotationMatrix[3] * values[0] +
                        mapperRotationMatrix[4] * values[1] +
                        mapperRotationMatrix[5] * values[2]
                    worldZ = mapperRotationMatrix[6] * values[0] +
                        mapperRotationMatrix[7] * values[1] +
                        mapperRotationMatrix[8] * values[2]
                }
                mapperState = mapperState.copy(
                    magneticReady = true,
                    currentStrengthUt = strength,
                    worldXUt = worldX,
                    worldYUt = worldY,
                    worldZUt = worldZ,
                )
            }
            mapperCallback?.invoke(mapperState)
        }
    }

    private fun updateQualification(event: SensorEvent) {
        if (event.sensor != qualificationSensor) return
        val values = event.values
        if (values.isEmpty() || values.any { !it.isFinite() }) return

        qualificationStats.add(event.timestamp, values)
        if (lastQualificationPublishNs == 0L ||
            event.timestamp - lastQualificationPublishNs >= PUBLISH_INTERVAL_NS
        ) {
            lastQualificationPublishNs = event.timestamp
            qualificationCallback?.invoke(
                SensorQualificationState(
                    running = true,
                    sensorType = event.sensor.type,
                    sensorName = event.sensor.name,
                    stringType = event.sensor.stringType.orEmpty(),
                    metrics = qualificationStats.snapshot(),
                ),
            )
        }
    }

    private fun vibrationSnapshot(): VibrationLabState {
        val accel = vibrationAccelerationStats.snapshot()
        val spectral = SensorLabMath.dominantFrequency(
            vibrationSamples.toList(),
            accel.observedHz,
        )
        return VibrationLabState(
            running = mode == Mode.Vibration,
            sourceLabel = if (vibrationUsesLinear) {
                "Aceleração linear"
            } else {
                "Acelerômetro com gravidade aproximada removida"
            },
            acceleration = accel,
            gyroscope = vibrationGyroStats.snapshot(),
            aois = vibrationAoisStats.snapshot(),
            dominantFrequencyHz = spectral.frequencyHz,
            dominantConfidence = spectral.confidence,
            lastError = if (!accel.available) "Sensor de aceleração indisponível." else null,
        )
    }

    private fun stabilizationSnapshot(): StabilizationLabState =
        StabilizationLabState(
            running = mode == Mode.Stabilization,
            physicalGyro = stabilizationGyroStats.snapshot(),
            aois = stabilizationAoisStats.snapshot(),
            vdis = stabilizationVdisStats.snapshot(),
            lastError = if (stabilizationGyro == null) {
                "Giroscópio físico indisponível."
            } else {
                null
            },
        )

    private fun mapperStateForCells(state: MagneticMapperState): MagneticMapperState {
        val populated = state.cellsUt.mapIndexedNotNull { index, value ->
            value?.let { index to it }
        }
        if (populated.isEmpty()) {
            return state.copy(
                minStrengthUt = null,
                maxStrengthUt = null,
                strongestIndex = null,
            )
        }
        val strongest = populated.maxBy { it.second }
        return state.copy(
            minStrengthUt = populated.minOf { it.second },
            maxStrengthUt = strongest.second,
            strongestIndex = strongest.first,
        )
    }

    private fun registerFast(sensor: Sensor?): Boolean {
        sensor ?: return false
        return manager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_FASTEST,
            handler,
        )
    }

    private fun findByStringType(stringType: String): Sensor? =
        manager.getSensorList(Sensor.TYPE_ALL)
            .firstOrNull { it.stringType == stringType }

    private class StreamStats(val sensor: Sensor?) {
        var registered: Boolean = false
        private var eventCount = 0L
        private var firstTimestampNs = 0L
        private var previousTimestampNs = 0L
        private var deltaCount = 0L
        private var deltaMeanNs = 0.0
        private var deltaM2Ns = 0.0
        private var sumSquaredMagnitude = 0.0
        private var peakMagnitude = 0.0
        private var lastValues: List<Float> = emptyList()

        fun add(
            timestampNs: Long,
            values: FloatArray,
            magnitudeOverride: Float? = null,
        ) {
            val magnitude = magnitudeOverride?.toDouble()
                ?: SensorLabMath.magnitude3(values).toDouble()
            if (!magnitude.isFinite()) return

            eventCount++
            if (firstTimestampNs == 0L) firstTimestampNs = timestampNs

            if (previousTimestampNs != 0L && timestampNs > previousTimestampNs) {
                val delta = (timestampNs - previousTimestampNs).toDouble()
                deltaCount++
                val diff = delta - deltaMeanNs
                deltaMeanNs += diff / deltaCount.toDouble()
                deltaM2Ns += diff * (delta - deltaMeanNs)
            }
            previousTimestampNs = timestampNs

            sumSquaredMagnitude += magnitude * magnitude
            peakMagnitude = maxOf(peakMagnitude, magnitude)
            lastValues = values.copyOf().take(8)
        }

        fun snapshot(): SensorStreamMetrics {
            val elapsedNs = if (firstTimestampNs > 0L) {
                previousTimestampNs - firstTimestampNs
            } else {
                0L
            }
            val hz = if (eventCount > 1L && elapsedNs > 0L) {
                (eventCount - 1L) * 1_000_000_000.0 / elapsedNs.toDouble()
            } else {
                0.0
            }
            val jitterNs = if (deltaCount > 1L) {
                sqrt(deltaM2Ns / (deltaCount - 1L).toDouble())
            } else {
                0.0
            }
            val rms = if (eventCount > 0L) {
                sqrt(sumSquaredMagnitude / eventCount.toDouble())
            } else {
                0.0
            }

            return SensorStreamMetrics(
                available = sensor != null,
                registered = registered,
                sampleReady = eventCount > 0L,
                name = sensor?.name ?: "N/D",
                stringType = sensor?.stringType.orEmpty(),
                declaredMinDelayUs = sensor?.minDelay,
                eventCount = eventCount,
                observedHz = hz.toFloat(),
                jitterMs = (jitterNs / 1_000_000.0).toFloat(),
                vectorSize = lastValues.size,
                rmsMagnitude = rms.toFloat(),
                peakMagnitude = peakMagnitude.toFloat(),
                lastValues = lastValues,
            )
        }
    }
}
