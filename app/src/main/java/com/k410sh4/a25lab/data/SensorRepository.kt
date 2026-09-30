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
        latestMotion = MotionSample()
        lastMotionPublishNs = 0L
        listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
        ).forEach { type ->
            registerDefault(type, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun startSuperpowers(onState: (SuperpowerSensorState) -> Unit) {
        stopMotion()
        superpowerCallback = onState
        lastSuperpowerPublishNs = 0L
        lastRotationTimestampNs = 0L
        smoothedQuaternion = QuaternionMath.Identity

        val allSensors = manager.getSensorList(Sensor.TYPE_ALL)
        val cct = allSensors.firstOrNull { it.stringType == CCT_STRING_TYPE }
        val aois = allSensors.firstOrNull { it.stringType == AOIS_STRING_TYPE }
        val vdis = allSensors.firstOrNull { it.stringType == VDIS_STRING_TYPE }
        val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val linearAcceleration = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        usingLinearAccelerationSensor = linearAcceleration?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true

        val fallbackAccelerationRegistered = if (!usingLinearAccelerationSensor) {
            registerDefault(
                Sensor.TYPE_ACCELEROMETER,
                SensorManager.SENSOR_DELAY_GAME,
            )
        } else {
            false
        }

        val rotationRegistered = rotation?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true

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
            cctSensorAvailable = cct != null,
            aoisAvailable = aois != null,
            aoisMinDelayUs = aois?.minDelay,
            vdisAvailable = vdis != null,
            vdisMinDelayUs = vdis?.minDelay,
        )

        registerDefault(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_GAME)
        registerDefault(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_GAME)
        registerDefault(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL)

        val cctActive = cct?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: false

        latestSuperpower = latestSuperpower.copy(cctStreamActive = cctActive)
        onState(latestSuperpower)
    }

    fun stopMotion() {
        manager.unregisterListener(this)
        motionCallback = null
        superpowerCallback = null
        lastMotionPublishNs = 0L
        lastSuperpowerPublishNs = 0L
        lastRotationTimestampNs = 0L
        usingLinearAccelerationSensor = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        updateMotion(event)
        updateSuperpowers(event)
    }

    private fun updateMotion(event: SensorEvent) {
        val callback = motionCallback ?: return
        val values = event.values
        if (values.size < 3) return

        latestMotion = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> latestMotion.copy(
                accelReady = true,
                ax = values[0],
                ay = values[1],
                az = values[2],
            )
            Sensor.TYPE_GYROSCOPE -> latestMotion.copy(
                gyroReady = true,
                gx = values[0],
                gy = values[1],
                gz = values[2],
            )
            Sensor.TYPE_MAGNETIC_FIELD -> latestMotion.copy(
                magReady = true,
                mx = values[0],
                my = values[1],
                mz = values[2],
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
            event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION && values.size >= 3 -> {
                latestSuperpower.copy(
                    dynamicAccelerationMs2 = SuperpowerMath.magnitude3(
                        values[0],
                        values[1],
                        values[2],
                    ),
                    accelerationSource = AccelerationSource.LINEAR_SENSOR,
                )
            }
            event.sensor.type == Sensor.TYPE_ACCELEROMETER &&
                !usingLinearAccelerationSensor &&
                values.size >= 3 -> {
                latestSuperpower.copy(
                    dynamicAccelerationMs2 = SuperpowerMath.dynamicAccelerationFallback(
                        values[0],
                        values[1],
                        values[2],
                    ),
                    accelerationSource = AccelerationSource.ACCELEROMETER_FALLBACK,
                )
            }
            event.sensor.type == Sensor.TYPE_GYROSCOPE && values.size >= 3 -> {
                latestSuperpower.copy(
                    angularSpeedRadS = SuperpowerMath.magnitude3(
                        values[0],
                        values[1],
                        values[2],
                    ),
                )
            }
            event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD && values.size >= 3 -> {
                latestSuperpower.copy(
                    magneticXUt = values[0],
                    magneticYUt = values[1],
                    magneticZUt = values[2],
                    magneticStrengthUt = SuperpowerMath.magnitude3(
                        values[0],
                        values[1],
                        values[2],
                    ),
                )
            }
            event.sensor.type == Sensor.TYPE_LIGHT && values.isNotEmpty() -> {
                latestSuperpower.copy(lightLux = values[0])
            }
            event.sensor.type == Sensor.TYPE_ROTATION_VECTOR && values.size >= 3 -> {
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
            event.sensor.stringType == CCT_STRING_TYPE && values.isNotEmpty() -> {
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

    private fun registerDefault(type: Int, rate: Int): Boolean {
        val sensor = manager.getDefaultSensor(type) ?: return false
        return manager.registerListener(this, sensor, rate)
    }

    private fun radiansToDegrees(value: Float): Float =
        value * 180f / PI.toFloat()
}
