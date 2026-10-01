package com.k410sh4.a25lab.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
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
        private const val FALLBACK_PERIOD_US = 20_000
        private const val MIN_EXPLICIT_PERIOD_US = 1_000
    }

    private enum class Mode {
        Idle,
        Vibration,
        Stabilization,
        MagneticMapper,
        Qualification,
    }

    private data class RegistrationResult(
        val registered: Boolean,
        val error: String? = null,
    )

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
    private var vibrationError: String? = null

    private var stabilizationGyro: Sensor? = null
    private var stabilizationAois: Sensor? = null
    private var stabilizationVdis: Sensor? = null
    private var stabilizationGyroStats = StreamStats(null)
    private var stabilizationAoisStats = StreamStats(null)
    private var stabilizationVdisStats = StreamStats(null)
    private var lastStabilizationPublishNs = 0L
    private var stabilizationError: String? = null

    private var qualificationSensor: Sensor? = null
    private var qualificationStats = StreamStats(null)
    private var lastQualificationPublishNs = 0L
    private var qualificationError: String? = null

    private var mapperMagnetic: Sensor? = null
    private var mapperRotation: Sensor? = null
    private val mapperRotationMatrix = FloatArray(9)
    private var mapperOrientationReady = false
    private var lastMapperPublishNs = 0L
    private val mapperLock = Any()

    @Volatile
    private var mapperState = MagneticMapperState()

    private val qualificationTriggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            if (mode != Mode.Qualification) return
            val sensor = qualificationSensor ?: return
            if (event.sensor != sensor) return

            try {
                val values = event.values
                if (values.isNotEmpty() && values.all { it.isFinite() }) {
                    qualificationStats.add(event.timestamp, values)
                }
                qualificationCallback?.invoke(
                    SensorQualificationState(
                        running = false,
                        sensorType = sensor.type,
                        sensorName = sensor.name,
                        stringType = sensor.stringType.orEmpty(),
                        metrics = qualificationStats.snapshot(),
                        lastError = null,
                    ),
                )
            } catch (error: RuntimeException) {
                qualificationError = failure("Trigger", error)
                publishQualificationFailure()
            }
        }
    }

    fun startVibration(onState: (VibrationLabState) -> Unit) {
        stop()
        mode = Mode.Vibration
        vibrationCallback = onState
        vibrationSamples.clear()
        lastVibrationPublishNs = 0L
        vibrationError = null

        val linear = safeGetDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accelerometer = safeGetDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        vibrationSensor = linear ?: accelerometer
        vibrationUsesLinear = linear != null
        val gyro = safeGetDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val aois = safeFindByStringType(AOIS_STRING_TYPE)

        vibrationAccelerationStats = StreamStats(vibrationSensor)
        vibrationGyroStats = StreamStats(gyro)
        vibrationAoisStats = StreamStats(aois)

        val accel = registerContinuous(vibrationSensor)
        val gyroResult = registerContinuous(gyro)
        val aoisResult = registerContinuous(aois)

        vibrationAccelerationStats.applyRegistration(accel)
        vibrationGyroStats.applyRegistration(gyroResult)
        vibrationAoisStats.applyRegistration(aoisResult)
        vibrationError = listOfNotNull(
            accel.error,
            gyroResult.error,
            aoisResult.error,
        ).firstOrNull()

        onState(vibrationSnapshot())
    }

    fun startStabilization(onState: (StabilizationLabState) -> Unit) {
        stop()
        mode = Mode.Stabilization
        stabilizationCallback = onState
        lastStabilizationPublishNs = 0L
        stabilizationError = null

        stabilizationGyro = safeGetDefaultSensor(Sensor.TYPE_GYROSCOPE)
        stabilizationAois = safeFindByStringType(AOIS_STRING_TYPE)
        stabilizationVdis = safeFindByStringType(VDIS_STRING_TYPE)

        stabilizationGyroStats = StreamStats(stabilizationGyro)
        stabilizationAoisStats = StreamStats(stabilizationAois)
        stabilizationVdisStats = StreamStats(stabilizationVdis)

        val gyroResult = registerContinuous(stabilizationGyro)
        val aoisResult = registerContinuous(stabilizationAois)
        val vdisResult = registerContinuous(stabilizationVdis)

        stabilizationGyroStats.applyRegistration(gyroResult)
        stabilizationAoisStats.applyRegistration(aoisResult)
        stabilizationVdisStats.applyRegistration(vdisResult)
        stabilizationError = listOfNotNull(
            gyroResult.error,
            aoisResult.error,
            vdisResult.error,
        ).firstOrNull()

        onState(stabilizationSnapshot())
    }

    fun startMagneticMapper(
        initialCells: List<Float?> = List(25) { null },
        onState: (MagneticMapperState) -> Unit,
    ) {
        stop()
        mode = Mode.MagneticMapper
        mapperCallback = onState
        mapperMagnetic = safeGetDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mapperRotation = safeGetDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        mapperOrientationReady = false
        lastMapperPublishNs = 0L

        val normalizedCells = List(25) { index -> initialCells.getOrNull(index) }
        mapperState = mapperStateForCells(
            MagneticMapperState(
                running = true,
                cellsUt = normalizedCells,
                nextIndex = normalizedCells.indexOfFirst { it == null }
                    .let { if (it < 0) 25 else it },
            ),
        )

        val magneticResult = registerContinuous(mapperMagnetic)
        val rotationResult = registerContinuous(mapperRotation)
        mapperState = mapperState.copy(
            running = magneticResult.registered,
            lastError = when {
                mapperMagnetic == null -> "Magnetômetro indisponível."
                magneticResult.error != null -> magneticResult.error
                !magneticResult.registered -> "Não foi possível registrar o magnetômetro."
                mapperRotation == null ->
                    "Rotation vector indisponível; intensidade ainda pode ser mapeada."
                rotationResult.error != null -> rotationResult.error
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
            safeMapperPublish()
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
            safeMapperPublish()
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
        qualificationError = null

        qualificationSensor = safeAllSensors()
            .firstOrNull { it.type == type && it.name == name }
        qualificationStats = StreamStats(qualificationSensor)

        val sensor = qualificationSensor
        val result = when {
            sensor == null -> RegistrationResult(false)
            sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT ->
                requestOneShot(sensor)
            else -> registerContinuous(sensor)
        }

        qualificationStats.applyRegistration(result)
        qualificationError = result.error

        onState(
            SensorQualificationState(
                running = result.registered,
                sensorType = type,
                sensorName = sensor?.name ?: name,
                stringType = sensor?.stringType.orEmpty(),
                metrics = qualificationStats.snapshot(),
                lastError = when {
                    sensor == null -> "Sensor não encontrado."
                    result.error != null -> result.error
                    !result.registered -> "Falha ao registrar o sensor."
                    else -> null
                },
            ),
        )
    }

    fun stop() {
        runCatching {
            manager.unregisterListener(this)
        }

        qualificationSensor?.let { sensor ->
            if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
                runCatching {
                    manager.cancelTriggerSensor(
                        qualificationTriggerListener,
                        sensor,
                    )
                }
            }
        }

        mode = Mode.Idle
        vibrationCallback = null
        stabilizationCallback = null
        mapperCallback = null
        qualificationCallback = null
        qualificationSensor = null
    }

    fun close() {
        stop()
        thread.quitSafely()
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            when (mode) {
                Mode.Vibration -> updateVibration(event)
                Mode.Stabilization -> updateStabilization(event)
                Mode.MagneticMapper -> updateMagneticMapper(event)
                Mode.Qualification -> updateQualification(event)
                Mode.Idle -> Unit
            }
        } catch (error: RuntimeException) {
            handleCallbackFailure(error)
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
                    abs(
                        SensorLabMath.magnitude3(values) -
                            SensorManager.GRAVITY_EARTH,
                    )
                }
                vibrationAccelerationStats.add(
                    event.timestamp,
                    values,
                    magnitude,
                )
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
            stabilizationGyro ->
                stabilizationGyroStats.add(event.timestamp, values)
            stabilizationAois ->
                stabilizationAoisStats.add(event.timestamp, values)
            stabilizationVdis ->
                stabilizationVdisStats.add(event.timestamp, values)
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
                SensorManager.getRotationMatrixFromVector(
                    mapperRotationMatrix,
                    values,
                )
                mapperOrientationReady = true
                mapperState = mapperState.copy(orientationReady = true)
            } else if (event.sensor == mapperMagnetic && values.size >= 3) {
                val strength = SensorLabMath.magnitude3(values)
                var worldX = 0f
                var worldY = 0f
                var worldZ = 0f

                if (mapperOrientationReady) {
                    worldX =
                        mapperRotationMatrix[0] * values[0] +
                        mapperRotationMatrix[1] * values[1] +
                        mapperRotationMatrix[2] * values[2]
                    worldY =
                        mapperRotationMatrix[3] * values[0] +
                        mapperRotationMatrix[4] * values[1] +
                        mapperRotationMatrix[5] * values[2]
                    worldZ =
                        mapperRotationMatrix[6] * values[0] +
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

            if (lastMapperPublishNs == 0L ||
                event.timestamp - lastMapperPublishNs >= PUBLISH_INTERVAL_NS
            ) {
                lastMapperPublishNs = event.timestamp
                safeMapperPublish()
            }
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
                    lastError = qualificationError,
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
            lastError = vibrationError ?: if (!accel.available) {
                "Sensor de aceleração indisponível."
            } else {
                null
            },
        )
    }

    private fun stabilizationSnapshot(): StabilizationLabState =
        StabilizationLabState(
            running = mode == Mode.Stabilization,
            physicalGyro = stabilizationGyroStats.snapshot(),
            aois = stabilizationAoisStats.snapshot(),
            vdis = stabilizationVdisStats.snapshot(),
            lastError = stabilizationError ?: if (stabilizationGyro == null) {
                "Giroscópio físico indisponível."
            } else {
                null
            },
        )

    private fun mapperStateForCells(
        state: MagneticMapperState,
    ): MagneticMapperState {
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

    private fun registerContinuous(sensor: Sensor?): RegistrationResult {
        sensor ?: return RegistrationResult(false)

        if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
            return RegistrationResult(
                registered = false,
                error = sensor.name +
                    ": sensor one-shot requer modo de trigger.",
            )
        }

        val periodUs = sensor.minDelay
            .takeIf { it > 0 }
            ?.coerceAtLeast(MIN_EXPLICIT_PERIOD_US)
            ?: FALLBACK_PERIOD_US

        return try {
            val registered = manager.registerListener(
                this,
                sensor,
                periodUs,
                handler,
            )
            RegistrationResult(
                registered = registered,
                error = if (registered) {
                    null
                } else {
                    sensor.name + ": registro recusado pelo SensorManager."
                },
            )
        } catch (error: RuntimeException) {
            RegistrationResult(
                registered = false,
                error = failure(sensor.name, error),
            )
        }
    }

    private fun requestOneShot(sensor: Sensor): RegistrationResult =
        try {
            val registered = manager.requestTriggerSensor(
                qualificationTriggerListener,
                sensor,
            )
            RegistrationResult(
                registered = registered,
                error = if (registered) {
                    null
                } else {
                    sensor.name + ": trigger recusado pelo SensorManager."
                },
            )
        } catch (error: RuntimeException) {
            RegistrationResult(
                registered = false,
                error = failure(sensor.name, error),
            )
        }

    private fun safeGetDefaultSensor(type: Int): Sensor? =
        try {
            manager.getDefaultSensor(type)
        } catch (_: RuntimeException) {
            null
        }

    private fun safeFindByStringType(stringType: String): Sensor? =
        safeAllSensors().firstOrNull { it.stringType == stringType }

    private fun safeAllSensors(): List<Sensor> =
        try {
            manager.getSensorList(Sensor.TYPE_ALL)
        } catch (_: RuntimeException) {
            emptyList()
        }

    private fun safeMapperPublish() {
        try {
            mapperCallback?.invoke(mapperState)
        } catch (error: RuntimeException) {
            mapperState = mapperState.copy(
                lastError = failure("Publicação do mapa", error),
            )
        }
    }

    private fun handleCallbackFailure(error: RuntimeException) {
        val message = failure("Callback do sensor", error)

        when (mode) {
            Mode.Vibration -> {
                vibrationError = message
                runCatching {
                    vibrationCallback?.invoke(vibrationSnapshot())
                }
            }
            Mode.Stabilization -> {
                stabilizationError = message
                runCatching {
                    stabilizationCallback?.invoke(stabilizationSnapshot())
                }
            }
            Mode.MagneticMapper -> {
                synchronized(mapperLock) {
                    mapperState = mapperState.copy(lastError = message)
                    safeMapperPublish()
                }
            }
            Mode.Qualification -> {
                qualificationError = message
                publishQualificationFailure()
            }
            Mode.Idle -> Unit
        }
    }

    private fun publishQualificationFailure() {
        val sensor = qualificationSensor
        runCatching {
            qualificationCallback?.invoke(
                SensorQualificationState(
                    running = false,
                    sensorType = sensor?.type,
                    sensorName = sensor?.name ?: "Sensor experimental",
                    stringType = sensor?.stringType.orEmpty(),
                    metrics = qualificationStats.snapshot(),
                    lastError = qualificationError,
                ),
            )
        }
    }

    private fun failure(
        operation: String,
        error: RuntimeException,
    ): String = operation + ": " + error::class.java.simpleName

    private class StreamStats(val sensor: Sensor?) {
        var registered: Boolean = false
        private var registrationError: String? = null
        private var eventCount = 0L
        private var firstTimestampNs = 0L
        private var previousTimestampNs = 0L
        private var deltaCount = 0L
        private var deltaMeanNs = 0.0
        private var deltaM2Ns = 0.0
        private var sumSquaredMagnitude = 0.0
        private var peakMagnitude = 0.0
        private val lastValues = FloatArray(8)
        private var lastValueCount = 0

        fun applyRegistration(result: RegistrationResult) {
            registered = result.registered
            registrationError = result.error
        }

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

            val squared = magnitude * magnitude
            if (squared.isFinite()) {
                sumSquaredMagnitude += squared
            }
            peakMagnitude = maxOf(peakMagnitude, magnitude)

            lastValueCount = minOf(values.size, lastValues.size)
            for (index in 0 until lastValueCount) {
                lastValues[index] = values[index]
            }
        }

        fun snapshot(): SensorStreamMetrics {
            val elapsedNs = if (firstTimestampNs > 0L) {
                previousTimestampNs - firstTimestampNs
            } else {
                0L
            }
            val hz = if (eventCount > 1L && elapsedNs > 0L) {
                (eventCount - 1L) *
                    1_000_000_000.0 /
                    elapsedNs.toDouble()
            } else {
                0.0
            }
            val jitterNs = if (deltaCount > 1L) {
                sqrt(
                    deltaM2Ns /
                        (deltaCount - 1L).toDouble(),
                )
            } else {
                0.0
            }
            val rms = if (eventCount > 0L && sumSquaredMagnitude.isFinite()) {
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
                vectorSize = lastValueCount,
                rmsMagnitude = rms.toFloat(),
                peakMagnitude = peakMagnitude.toFloat(),
                lastValues = List(lastValueCount) { index ->
                    lastValues[index]
                },
                registrationError = registrationError,
            )
        }
    }
}
