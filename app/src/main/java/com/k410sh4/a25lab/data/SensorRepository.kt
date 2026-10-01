package com.k410sh4.a25lab.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.k410sh4.a25lab.model.AccelerationSource
import com.k410sh4.a25lab.model.MotionSample
import com.k410sh4.a25lab.model.SensorInfo
import com.k410sh4.a25lab.model.SuperpowerSensorState
import com.k410sh4.a25lab.model.sensorTypeName
import com.k410sh4.a25lab.util.Quaternion
import com.k410sh4.a25lab.util.QuaternionMath
import com.k410sh4.a25lab.util.SuperpowerMath
import kotlin.math.PI

class SensorRepository(context: Context) : SensorEventListener {
    companion object {
        private const val UI_PUBLISH_INTERVAL_NS = 50_000_000L
        private const val ROTATION_SMOOTHING_TIME_CONSTANT_MS = 65f
        private const val CCT_STRING_TYPE = "com.samsung.sensor.light_cct"
        private const val AOIS_STRING_TYPE = "com.samsung.sensor.gyroscope_aois"
        private const val VDIS_STRING_TYPE = "com.samsung.sensor.vdis_gyro"
    }

    private val manager = context.getSystemService(SensorManager::class.java)
    private var motionCallback: ((MotionSample) -> Unit)? = null
    private var superpowerCallback: ((SuperpowerSensorState) -> Unit)? = null
    private var latestMotion = MotionSample()
    private var latestSuperpower = SuperpowerSensorState()
    private var lastMotionPublishNs = 0L
    private var lastSuperpowerPublishNs = 0L
    private var lastRotationTimestampNs = 0L
    private val rotationMatrix = FloatArray(9)
    private val orientationRadians = FloatArray(3)
    private val rotationQuaternion = FloatArray(4)
    private val smoothedRotationVector = FloatArray(4)
    private var smoothedQuaternion = QuaternionMath.Identity
    private var usingLinearAccelerationSensor = false

    fun listSensors(): List<SensorInfo> = manager.getSensorList(Sensor.TYPE_ALL)
        .sortedWith(compareBy<Sensor> { it.type }.thenBy { it.name })
        .map { sensor ->
            SensorInfo(
                name = sensor.name,
                vendor = sensor.vendor,
                type = sensor.type,
                stringType = sensor.stringType.orEmpty(),
                typeName = sensorTypeName(sensor.type),
                version = sensor.version,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMa = sensor.power,
                minDelayUs = sensor.minDelay,
                maxDelayUs = sensor.maxDelay,
                fifoReservedEventCount = sensor.fifoReservedEventCount,
                fifoMaxEventCount = sensor.fifoMaxEventCount,
                reportingMode = sensor.reportingMode,
                wakeUp = sensor.isWakeUpSensor,
                dynamic = sensor.isDynamicSensor,
                additionalInfoSupported = sensor.isAdditionalInfoSupported,
            )
        }

    fun startMotion(onSample: (MotionSample) -> Unit) {
        stopMotion()
        motionCallback = onSample
        lastMotionPublishNs = 0L

        val accelerometerActive = registerDefault(
            Sensor.TYPE_ACCELEROMETER,
            SensorManager.SENSOR_DELAY_GAME,
        )
        val gyroscopeActive = registerDefault(
            Sensor.TYPE_GYROSCOPE,
            SensorManager.SENSOR_DELAY_GAME,
        )
        val magnetometerActive = registerDefault(
            Sensor.TYPE_MAGNETIC_FIELD,
            SensorManager.SENSOR_DELAY_GAME,
        )

        latestMotion = MotionSample(
            accelerometerStreamActive = accelerometerActive,
            gyroscopeStreamActive = gyroscopeActive,
            magnetometerStreamActive = magnetometerActive,
        )
        onSample(latestMotion)
    }

    fun startSuperpowers(onState: (SuperpowerSensorState) -> Unit) {
        stopMotion()
        superpowerCallback = onState
        lastSuperpowerPublishNs = 0L
        lastRotationTimestampNs = 0L
        smoothedQuaternion = QuaternionMath.Identity

        val allSensors = safeAllSensors()
        val cct = allSensors.firstOrNull { it.stringType == CCT_STRING_TYPE }
        val aois = allSensors.firstOrNull { it.stringType == AOIS_STRING_TYPE }
        val vdis = allSensors.firstOrNull { it.stringType == VDIS_STRING_TYPE }
        val rotation = safeGetDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val linearAcceleration =
            safeGetDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        usingLinearAccelerationSensor = safeRegister(
            linearAcceleration,
            SensorManager.SENSOR_DELAY_GAME,
        )

        val fallbackAccelerationRegistered = if (!usingLinearAccelerationSensor) {
            registerDefault(
                Sensor.TYPE_ACCELEROMETER,
                SensorManager.SENSOR_DELAY_GAME,
            )
        } else {
            false
        }

        val rotationRegistered = safeRegister(
            rotation,
            SensorManager.SENSOR_DELAY_GAME,
        )

        val angularRegistered = registerDefault(
            Sensor.TYPE_GYROSCOPE,
            SensorManager.SENSOR_DELAY_GAME,
        )
        val magneticRegistered = registerDefault(
            Sensor.TYPE_MAGNETIC_FIELD,
            SensorManager.SENSOR_DELAY_GAME,
        )
        val lightRegistered = registerDefault(
            Sensor.TYPE_LIGHT,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        latestSuperpower = SuperpowerSensorState(
            orientationAvailable = rotationRegistered,
            accelerationSource = when {
                usingLinearAccelerationSensor ->
                    AccelerationSource.LINEAR_SENSOR
                fallbackAccelerationRegistered ->
                    AccelerationSource.ACCELEROMETER_FALLBACK
                else ->
                    AccelerationSource.UNAVAILABLE
            },
            angularStreamActive = angularRegistered,
            magneticStreamActive = magneticRegistered,
            lightStreamActive = lightRegistered,
            cctSensorAvailable = cct != null,
            aoisAvailable = aois != null,
            aoisMinDelayUs = aois?.minDelay,
            vdisAvailable = vdis != null,
            vdisMinDelayUs = vdis?.minDelay,
        )

        val cctActive = safeRegister(
            cct,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        latestSuperpower = latestSuperpower.copy(cctStreamActive = cctActive)
        onState(latestSuperpower)
    }

    fun stopMotion() {
        runCatching {
            manager.unregisterListener(this)
        }
        motionCallback = null
        superpowerCallback = null
        lastMotionPublishNs = 0L
        lastSuperpowerPublishNs = 0L
        lastRotationTimestampNs = 0L
        usingLinearAccelerationSensor = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            updateMotion(event)
            updateSuperpowers(event)
        } catch (_: RuntimeException) {
            // OEM/vendor sensor payloads must never terminate the app.
        }
    }

    private fun updateMotion(event: SensorEvent) {
        val callback = motionCallback ?: return
        val values = event.values
        if (values.size < 3 || !valuesAreFinite(values, 3)) return

        latestMotion = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> latestMotion.copy(
                ax = values[0],
                ay = values[1],
                az = values[2],
                accelerometerSampleReady = true,
            )
            Sensor.TYPE_GYROSCOPE -> latestMotion.copy(
                gx = values[0],
                gy = values[1],
                gz = values[2],
                gyroscopeSampleReady = true,
            )
            Sensor.TYPE_MAGNETIC_FIELD -> latestMotion.copy(
                mx = values[0],
                my = values[1],
                mz = values[2],
                magnetometerSampleReady = true,
            )
            else -> latestMotion
        }

        if (lastMotionPublishNs == 0L ||
            event.timestamp - lastMotionPublishNs >= UI_PUBLISH_INTERVAL_NS
        ) {
            lastMotionPublishNs = event.timestamp
            callback(latestMotion)
        }
    }

    private fun updateSuperpowers(event: SensorEvent) {
        val callback = superpowerCallback ?: return
        val values = event.values

        latestSuperpower = when {
            event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION &&
                values.size >= 3 &&
                valuesAreFinite(values, 3) -> {
                latestSuperpower.copy(
                    dynamicAccelerationMs2 = SuperpowerMath.magnitude3(
                        values[0],
                        values[1],
                        values[2],
                    ),
                    accelerationSource = AccelerationSource.LINEAR_SENSOR,
                    accelerationSampleReady = true,
                )
            }
            event.sensor.type == Sensor.TYPE_ACCELEROMETER &&
                !usingLinearAccelerationSensor &&
                values.size >= 3 &&
                valuesAreFinite(values, 3) -> {
                latestSuperpower.copy(
                    dynamicAccelerationMs2 = SuperpowerMath.dynamicAccelerationFallback(
                        values[0],
                        values[1],
                        values[2],
                    ),
                    accelerationSource = AccelerationSource.ACCELEROMETER_FALLBACK,
                    accelerationSampleReady = true,
                )
            }
            event.sensor.type == Sensor.TYPE_GYROSCOPE &&
                values.size >= 3 &&
                valuesAreFinite(values, 3) -> {
                latestSuperpower.copy(
                    angularSpeedRadS = SuperpowerMath.magnitude3(
                        values[0],
                        values[1],
                        values[2],
                    ),
                    angularSampleReady = true,
                )
            }
            event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD &&
                values.size >= 3 &&
                valuesAreFinite(values, 3) -> {
                latestSuperpower.copy(
                    magneticXUt = values[0],
                    magneticYUt = values[1],
                    magneticZUt = values[2],
                    magneticStrengthUt = SuperpowerMath.magnitude3(
                        values[0],
                        values[1],
                        values[2],
                    ),
                    magneticSampleReady = true,
                )
            }
            event.sensor.type == Sensor.TYPE_LIGHT &&
                values.isNotEmpty() &&
                values[0].isFinite() -> {
                latestSuperpower.copy(lightLux = values[0])
            }
            event.sensor.type == Sensor.TYPE_ROTATION_VECTOR &&
                values.size >= 3 &&
                valuesAreFinite(values, minOf(values.size, 4)) -> {
                SensorManager.getQuaternionFromVector(rotationQuaternion, values)

                val rawQuaternion = QuaternionMath.normalize(
                    Quaternion(
                        w = rotationQuaternion[0],
                        x = rotationQuaternion[1],
                        y = rotationQuaternion[2],
                        z = rotationQuaternion[3],
                    ),
                )

                smoothedQuaternion = if (!latestSuperpower.orientationSampleReady) {
                    rawQuaternion
                } else {
                    val alpha = QuaternionMath.smoothingAlpha(
                        deltaTimeNs = event.timestamp - lastRotationTimestampNs,
                        timeConstantMs = ROTATION_SMOOTHING_TIME_CONSTANT_MS,
                    )
                    QuaternionMath.nlerp(smoothedQuaternion, rawQuaternion, alpha)
                }
                lastRotationTimestampNs = event.timestamp

                smoothedRotationVector[0] = smoothedQuaternion.x
                smoothedRotationVector[1] = smoothedQuaternion.y
                smoothedRotationVector[2] = smoothedQuaternion.z
                smoothedRotationVector[3] = smoothedQuaternion.w
                SensorManager.getRotationMatrixFromVector(
                    rotationMatrix,
                    smoothedRotationVector,
                )
                SensorManager.getOrientation(
                    rotationMatrix,
                    orientationRadians,
                )

                latestSuperpower.copy(
                    yawDeg = radiansToDegrees(orientationRadians[0]),
                    pitchDeg = radiansToDegrees(orientationRadians[1]),
                    rollDeg = radiansToDegrees(orientationRadians[2]),
                    quaternionW = smoothedQuaternion.w,
                    quaternionX = smoothedQuaternion.x,
                    quaternionY = smoothedQuaternion.y,
                    quaternionZ = smoothedQuaternion.z,
                    orientationSampleReady = true,
                )
            }
            event.sensor.stringType == CCT_STRING_TYPE &&
                values.isNotEmpty() &&
                valuesAreFinite(values, values.size) -> {
                val rawValues = values.copyOf().toList()
                latestSuperpower.copy(
                    cctRaw = rawValues.firstOrNull(),
                    cctRawValues = rawValues,
                )
            }
            else -> latestSuperpower
        }

        if (lastSuperpowerPublishNs == 0L ||
            event.timestamp - lastSuperpowerPublishNs >= UI_PUBLISH_INTERVAL_NS
        ) {
            lastSuperpowerPublishNs = event.timestamp
            callback(latestSuperpower)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) return
        val callback = superpowerCallback ?: return
        if (latestSuperpower.magneticAccuracy == accuracy) return

        latestSuperpower = latestSuperpower.copy(magneticAccuracy = accuracy)
        callback(latestSuperpower)
    }

    private fun registerDefault(type: Int, rate: Int): Boolean =
        safeRegister(
            safeGetDefaultSensor(type),
            rate,
        )

    private fun safeRegister(
        sensor: Sensor?,
        rate: Int,
    ): Boolean {
        sensor ?: return false
        return try {
            manager.registerListener(this, sensor, rate)
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun safeGetDefaultSensor(type: Int): Sensor? =
        try {
            manager.getDefaultSensor(type)
        } catch (_: RuntimeException) {
            null
        }

    private fun safeAllSensors(): List<Sensor> =
        try {
            manager.getSensorList(Sensor.TYPE_ALL)
        } catch (_: RuntimeException) {
            emptyList()
        }

    private fun valuesAreFinite(
        values: FloatArray,
        count: Int,
    ): Boolean {
        val limit = minOf(count, values.size)
        for (index in 0 until limit) {
            if (!values[index].isFinite()) return false
        }
        return true
    }

    private fun radiansToDegrees(value: Float): Float =
        value * 180f / PI.toFloat()
}
