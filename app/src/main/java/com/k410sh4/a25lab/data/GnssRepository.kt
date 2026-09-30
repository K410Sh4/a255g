package com.k410sh4.a25lab.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var locationReceiverRegistered = false

    private val locationModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.MODE_CHANGED_ACTION) {
                handleLocationModeChanged()
            }
        }
    }

    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            val constellations = linkedSetOf<String>()

            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
                constellations += constellationName(
                    status.getConstellationType(i),
                )
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
            update(
                state.copy(
                    rawMeasurementCount = eventArgs.measurements.size,
                ),
            )
        }
    }

    fun start(onState: (GnssState) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onState(
                GnssState(
                    lastError = "Permissão de localização precisa necessária.",
                ),
            )
            return
        }

        stop()
        callback = onState
        registerLocationModeReceiver()

        val locationEnabled = runCatching {
            manager.isLocationEnabled
        }.getOrDefault(false)

        if (!locationEnabled) {
            state = GnssState(
                running = false,
                locationEnabled = false,
                lastError = "Ative a localização do Android para usar GNSS.",
            )
            callback?.invoke(state)
            return
        }

        val rawMeasurementsSupported = runCatching {
            manager.gnssCapabilities.hasMeasurements()
        }.getOrDefault(false)

        state = GnssState(
            running = true,
            locationEnabled = true,
            rawMeasurementsSupported = rawMeasurementsSupported,
            lastError = if (rawMeasurementsSupported) {
                null
            } else {
                "O chipset/firmware não anuncia suporte a medições GNSS brutas."
            },
        )
        callback?.invoke(state)

        try {
            val statusRegistered = manager.registerGnssStatusCallback(
                context.mainExecutor,
                statusCallback,
            )

            val measurementsRegistered = if (rawMeasurementsSupported) {
                manager.registerGnssMeasurementsCallback(
                    context.mainExecutor,
                    measurementsCallback,
                )
            } else {
                false
            }

            if (!statusRegistered) {
                unregisterGnssCallbacks()
                update(
                    state.copy(
                        running = false,
                        lastError = "O Android recusou o callback de status GNSS.",
                    ),
                )
            } else if (rawMeasurementsSupported && !measurementsRegistered) {
                update(
                    state.copy(
                        rawMeasurementsSupported = false,
                        lastError = "O Android recusou o callback de medições GNSS brutas.",
                    ),
                )
            }
        } catch (security: SecurityException) {
            unregisterGnssCallbacks()
            update(
                GnssState(
                    locationEnabled = true,
                    lastError = security.message ?: "Acesso GNSS negado.",
                ),
            )
        } catch (error: RuntimeException) {
            unregisterGnssCallbacks()
            update(
                GnssState(
                    locationEnabled = true,
                    lastError = error.message ?: "Falha ao iniciar GNSS.",
                ),
            )
        }
    }

    fun stop() {
        unregisterGnssCallbacks()
        unregisterLocationModeReceiver()

        if (state.running) {
            update(state.copy(running = false))
        }
        callback = null
    }

    private fun registerLocationModeReceiver() {
        if (locationReceiverRegistered) return

        runCatching {
            ContextCompat.registerReceiver(
                context,
                locationModeReceiver,
                IntentFilter(LocationManager.MODE_CHANGED_ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
            locationReceiverRegistered = true
        }
    }

    private fun unregisterLocationModeReceiver() {
        if (!locationReceiverRegistered) return

        runCatching {
            context.unregisterReceiver(locationModeReceiver)
        }
        locationReceiverRegistered = false
    }

    private fun unregisterGnssCallbacks() {
        runCatching {
            manager.unregisterGnssStatusCallback(statusCallback)
        }
        runCatching {
            manager.unregisterGnssMeasurementsCallback(
                measurementsCallback,
            )
        }
    }

    private fun handleLocationModeChanged() {
        val enabled = runCatching {
            manager.isLocationEnabled
        }.getOrDefault(false)

        if (!enabled) {
            unregisterGnssCallbacks()
            update(
                state.copy(
                    running = false,
                    locationEnabled = false,
                    lastError = "A localização do Android foi desativada.",
                ),
            )
        } else if (!state.locationEnabled) {
            update(
                state.copy(
                    locationEnabled = true,
                    lastError = null,
                ),
            )
        }
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
