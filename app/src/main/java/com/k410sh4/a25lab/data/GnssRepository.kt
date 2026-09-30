package com.k410sh4.a25lab.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.model.GnssState

class GnssRepository(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var callback: ((GnssState) -> Unit)? = null
    private var state = GnssState()

    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            val constellations = linkedSetOf<String>()
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
                constellations += constellationName(status.getConstellationType(i))
            }
            update(
                state.copy(
                    satellitesVisible = status.satelliteCount,
                    satellitesUsed = used,
                    constellations = constellations,
                ),
            )
        }
    }

    private val measurementsCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            update(state.copy(rawMeasurementCount = eventArgs.measurements.size))
        }

        override fun onStatusChanged(status: Int) {
            if (status == GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED) {
                update(state.copy(lastError = "Medições GNSS brutas não são suportadas pelo framework/firmware."))
            }
        }
    }

    fun start(onState: (GnssState) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onState(GnssState(lastError = "Permissão de localização precisa necessária."))
            return
        }
        stop()
        callback = onState
        state = GnssState(running = true)
        callback?.invoke(state)
        try {
            manager.registerGnssStatusCallback(context.mainExecutor, statusCallback)
            manager.registerGnssMeasurementsCallback(context.mainExecutor, measurementsCallback)
        } catch (security: SecurityException) {
            update(GnssState(lastError = security.message ?: "Acesso GNSS negado."))
        } catch (error: RuntimeException) {
            update(GnssState(lastError = error.message ?: "Falha ao iniciar GNSS."))
        }
    }

    fun stop() {
        runCatching { manager.unregisterGnssStatusCallback(statusCallback) }
        runCatching { manager.unregisterGnssMeasurementsCallback(measurementsCallback) }
        if (state.running) update(state.copy(running = false))
        callback = null
    }

    private fun update(newState: GnssState) {
        state = newState
        callback?.invoke(newState)
    }

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
        else -> "Outro"
    }
}
