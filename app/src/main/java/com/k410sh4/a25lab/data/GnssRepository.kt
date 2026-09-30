package com.k410sh4.a25lab.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.model.GnssState

class GnssRepository(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var callback: ((GnssState) -> Unit)? = null
    private var state = GnssState()
    private var locationReceiverRegistered = false

    private val engineLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Intencionalmente descartado. A25 Lab ativa o receptor para
            // obter status/measurements, mas não armazena coordenadas.
        }
    }

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
        if (!hasFineLocationPermission()) {
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
                gpsProviderEnabled = false,
                lastError = "Ative a localização do Android para usar GNSS.",
            )
            callback?.invoke(state)
            return
        }

        val gpsProviderEnabled = runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        if (!gpsProviderEnabled) {
            state = GnssState(
                running = false,
                locationEnabled = true,
                gpsProviderEnabled = false,
                lastError = "O provedor GPS está indisponível ou desativado.",
            )
            callback?.invoke(state)
            return
        }

        val rawMeasurementsSupported = runCatching {
            manager.gnssCapabilities.hasMeasurements()
        }.getOrDefault(false)

        state = GnssState(
            running = false,
            locationEnabled = true,
            gpsProviderEnabled = true,
            engineActive = false,
            rawMeasurementsSupported = rawMeasurementsSupported,
        )
        callback?.invoke(state)

        try {
            val statusRegistered = manager.registerGnssStatusCallback(
                context.mainExecutor,
                statusCallback,
            )

            if (!statusRegistered) {
                unregisterGnssCallbacks()
                update(
                    state.copy(
                        running = false,
                        engineActive = false,
                        lastError = "O Android recusou o callback de status GNSS.",
                    ),
                )
                return
            }

            val measurementsRegistered = if (rawMeasurementsSupported) {
                manager.registerGnssMeasurementsCallback(
                    context.mainExecutor,
                    measurementsCallback,
                )
            } else {
                false
            }

            val request = LocationRequest.Builder(1_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                .build()

            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                request,
                context.mainExecutor,
                engineLocationListener,
            )

            update(
                state.copy(
                    running = true,
                    engineActive = true,
                    rawMeasurementsSupported =
                        rawMeasurementsSupported && measurementsRegistered,
                    lastError = when {
                        rawMeasurementsSupported && !measurementsRegistered ->
                            "O Android anunciou GNSS raw, mas recusou o callback de medições."
                        else -> null
                    },
                ),
            )
        } catch (security: SecurityException) {
            unregisterGnssCallbacks()
            update(
                GnssState(
                    locationEnabled = true,
                    gpsProviderEnabled = gpsProviderEnabled,
                    lastError = security.message ?: "Acesso GNSS negado.",
                ),
            )
        } catch (error: RuntimeException) {
            unregisterGnssCallbacks()
            update(
                GnssState(
                    locationEnabled = true,
                    gpsProviderEnabled = gpsProviderEnabled,
                    lastError = error.message ?: "Falha ao iniciar GNSS.",
                ),
            )
        }
    }

    fun stop() {
        unregisterGnssCallbacks()
        unregisterLocationModeReceiver()

        if (state.running || state.engineActive) {
            update(
                state.copy(
                    running = false,
                    engineActive = false,
                ),
            )
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
                ContextCompat.RECEIVER_NOT_EXPORTED,
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
        if (hasFineLocationPermission()) {
            runCatching {
                manager.removeUpdates(engineLocationListener)
            }
        }
    }

    private fun handleLocationModeChanged() {
        val enabled = runCatching {
            manager.isLocationEnabled
        }.getOrDefault(false)

        val gpsEnabled = enabled && runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)

        if (!enabled || !gpsEnabled) {
            unregisterGnssCallbacks()
            update(
                state.copy(
                    running = false,
                    locationEnabled = enabled,
                    gpsProviderEnabled = gpsEnabled,
                    engineActive = false,
                    lastError = if (!enabled) {
                        "A localização do Android foi desativada."
                    } else {
                        "O provedor GPS foi desativado."
                    },
                ),
            )
        } else if (!state.engineActive) {
            val activeCallback = callback
            if (activeCallback != null) {
                start(activeCallback)
            } else {
                update(
                    state.copy(
                        locationEnabled = true,
                        gpsProviderEnabled = true,
                        lastError = null,
                    ),
                )
            }
        }
    }

    private fun update(newState: GnssState) {
        state = newState
        callback?.invoke(newState)
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

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
