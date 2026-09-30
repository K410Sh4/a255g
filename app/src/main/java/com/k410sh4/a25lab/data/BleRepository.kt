package com.k410sh4.a25lab.data

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.model.BleDeviceInfo
import com.k410sh4.a25lab.model.BleState
import com.k410sh4.a25lab.util.DisplaySanitizer

class BleRepository(private val context: Context) {
    companion object {
        private const val STALE_DEVICE_MS = 30_000L
        private const val UI_PUBLISH_INTERVAL_MS = 250L
        private const val MAX_DEVICES = 128
    }

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager.adapter
    private var callback: ((BleState) -> Unit)? = null
    private var state = BleState()
    private val devices = linkedMapOf<BluetoothDevice, BleDeviceInfo>()
    private var nextSessionDeviceId = 0
    private var lastPublishElapsedMs = 0L
    private val maintenanceHandler = Handler(Looper.getMainLooper())
    private val maintenanceRunnable = object : Runnable {
        override fun run() {
            if (!state.scanning) return

            val scannerAvailable = runCatching {
                adapter?.bluetoothLeScanner != null
            }.getOrDefault(false)
            if (!scannerAvailable) {
                finishScanWithError(
                    "Bluetooth foi desativado ou o scanner BLE ficou indisponível.",
                )
                return
            }

            val changed = pruneStale(SystemClock.elapsedRealtime())
            if (changed) publish()
            maintenanceHandler.postDelayed(this, 1_000L)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!state.scanning) return
            handleResult(result)
            publishIfDue()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            if (!state.scanning) return
            results.forEach(::handleResult)
            publish()
        }

        override fun onScanFailed(errorCode: Int) {
            if (!state.scanning) return
            finishScanWithError(
                "Falha no scan BLE: código $errorCode",
            )
        }
    }

    fun start(onState: (BleState) -> Unit) {
        stopInternal(clearCallback = true)
        callback = onState

        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            onState(BleState(lastError = "Permissão Bluetooth de scan necessária."))
            return
        }

        val currentAdapter = adapter
        if (currentAdapter == null) {
            onState(BleState(lastError = "Bluetooth não disponível."))
            return
        }

        devices.clear()
        nextSessionDeviceId = 0
        lastPublishElapsedMs = 0L
        state = BleState(scanning = true)
        callback?.invoke(state)

        try {
            val scanner = currentAdapter.bluetoothLeScanner
            if (scanner == null) {
                state = BleState(lastError = "Bluetooth desligado ou scanner BLE indisponível.")
                callback?.invoke(state)
                return
            }
            scanner.startScan(scanCallback)
            maintenanceHandler.postDelayed(maintenanceRunnable, 1_000L)
        } catch (security: SecurityException) {
            state = BleState(lastError = "Acesso Bluetooth negado.")
            callback?.invoke(state)
        } catch (error: RuntimeException) {
            state = BleState(
                lastError = "Falha ao iniciar BLE: ${error::class.java.simpleName}",
            )
            callback?.invoke(state)
        }
    }

    fun stop() = stopInternal(clearCallback = true)

    private fun stopInternal(clearCallback: Boolean) {
        maintenanceHandler.removeCallbacks(maintenanceRunnable)
        runCatching {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        // Cleanup best-effort: a permissão ou o estado do adaptador
        // podem mudar entre start e stop.

        if (state.scanning) {
            state = state.copy(
                scanning = false,
                devices = devices.values.sortedByDescending { it.rssi },
            )
            callback?.invoke(state)
        }
        if (clearCallback) {
            callback = null
            devices.clear()
            state = state.copy(devices = emptyList())
        }
    }

    private fun finishScanWithError(message: String) {
        maintenanceHandler.removeCallbacks(maintenanceRunnable)
        runCatching {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }

        state = state.copy(
            scanning = false,
            devices = devices.values.sortedByDescending { it.rssi },
            lastError = message,
        )
        callback?.invoke(state)
        devices.clear()
    }

    private fun handleResult(result: ScanResult) {
        val now = SystemClock.elapsedRealtime()
        pruneStale(now)

        val record = result.scanRecord
        val rawName = record?.deviceName
            ?.takeIf { it.isNotBlank() }
            ?: "Dispositivo BLE"
        val name = DisplaySanitizer.safeSingleLine(
            rawName,
            maxCodePoints = 128,
        )
        val device = result.device
        val key = devices[device]?.key
            ?: "session:${++nextSessionDeviceId}"

        devices[device] = BleDeviceInfo(
            key = key,
            name = name,
            rssi = result.rssi,
            connectable = result.isConnectable,
            lastSeenElapsedMs = now,
        )

        if (devices.size > MAX_DEVICES) {
            val oldestDevice = devices
                .minByOrNull { it.value.lastSeenElapsedMs }
                ?.key
            if (oldestDevice != null) {
                devices.remove(oldestDevice)
            }
        }
    }

    private fun pruneStale(now: Long): Boolean {
        var changed = false
        val iterator = devices.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeenElapsedMs > STALE_DEVICE_MS) {
                iterator.remove()
                changed = true
            }
        }
        return changed
    }

    private fun publishIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (lastPublishElapsedMs == 0L ||
            now - lastPublishElapsedMs >= UI_PUBLISH_INTERVAL_MS
        ) {
            publish()
        }
    }

    private fun publish() {
        lastPublishElapsedMs = SystemClock.elapsedRealtime()
        state = state.copy(devices = devices.values.sortedByDescending { it.rssi })
        callback?.invoke(state)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
}
