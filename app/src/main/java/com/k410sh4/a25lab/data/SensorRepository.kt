package com.k410sh4.a25lab.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.k410sh4.a25lab.model.MotionSample
import com.k410sh4.a25lab.model.SensorInfo
import com.k410sh4.a25lab.model.sensorTypeName

class SensorRepository(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private var callback: ((MotionSample) -> Unit)? = null
    private var latest = MotionSample()

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
        callback = onSample
        listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
        ).forEach { type ->
            manager.getDefaultSensor(type)?.let { sensor ->
                manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stopMotion() {
        manager.unregisterListener(this)
        callback = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size < 3) return
        latest = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> latest.copy(ax = v[0], ay = v[1], az = v[2])
            Sensor.TYPE_GYROSCOPE -> latest.copy(gx = v[0], gy = v[1], gz = v[2])
            Sensor.TYPE_MAGNETIC_FIELD -> latest.copy(mx = v[0], my = v[1], mz = v[2])
            else -> latest
        }
        callback?.invoke(latest)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
