package com.k410sh4.a25lab.data

import android.Manifest
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
    private val devices = linkedMapOf<String, BleDeviceInfo>()
    private var lastPublishElapsedMs = 0L
    private val maintenanceHandler = Handler(Looper.getMainLooper())
    private val maintenanceRunnable = object : Runnable {
        override fun run() {
            if (!state.scanning) return
            val changed = pruneStale(SystemClock.elapsedRealtime())
            if (changed) publish()
            maintenanceHandler.postDelayed(this, 1_000L)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
            publishIfDue()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleResult)
            publish()
        }

        override fun onScanFailed(errorCode: Int) {
            maintenanceHandler.removeCallbacks(maintenanceRunnable)
            state = state.copy(
                scanning = false,
                lastError = "Falha no scan BLE: código $errorCode",
            )
            callback?.invoke(state)
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
            state = BleState(lastError = security.message ?: "Acesso Bluetooth negado.")
            callback?.invoke(state)
        }
    }

    fun stop() = stopInternal(clearCallback = true)

    private fun stopInternal(clearCallback: Boolean) {
        maintenanceHandler.removeCallbacks(maintenanceRunnable)
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // Cleanup best-effort: a permissão pode ter sido revogada.
        }

        if (state.scanning) {
            state = state.copy(
                scanning = false,
                devices = devices.values.sortedByDescending { it.rssi },
            )
            callback?.invoke(state)
        }
        if (clearCallback) callback = null
    }

    private fun handleResult(result: ScanResult) {
        val now = SystemClock.elapsedRealtime()
        pruneStale(now)

        val record = result.scanRecord
        val name = DisplaySanitizer.safeSingleLine(
            record?.deviceName ?: "Dispositivo BLE",
            maxCodePoints = 128,
        )
        val key = "anon:${result.device.hashCode()}"

        devices[key] = BleDeviceInfo(
            key = key,
            name = name,
            address = "não coletado",
            rssi = result.rssi,
            connectable = result.isConnectable,
            lastSeenElapsedMs = now,
        )

        if (devices.size > MAX_DEVICES) {
            val oldest = devices.minByOrNull { it.value.lastSeenElapsedMs }?.key
            if (oldest != null) devices.remove(oldest)
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
