package com.k410sh4.a25lab.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var adapterReceiverRegistered = false

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

            val newState = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR,
            )
            if (
                newState == BluetoothAdapter.STATE_TURNING_OFF ||
                newState == BluetoothAdapter.STATE_OFF
            ) {
                finishScanWithError("Bluetooth foi desativado.")
            }
        }
    }
    private val devices = linkedMapOf<BluetoothDevice, BleDeviceInfo>()
    private var nextSessionDeviceId = 0
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
            registerAdapterStateReceiver()
            scanner.startScan(scanCallback)
            maintenanceHandler.postDelayed(maintenanceRunnable, 1_000L)
        } catch (security: SecurityException) {
            unregisterAdapterStateReceiver()
            state = BleState(lastError = security.message ?: "Acesso Bluetooth negado.")
            callback?.invoke(state)
        }
    }

    fun stop() = stopInternal(clearCallback = true)

    private fun stopInternal(clearCallback: Boolean) {
        maintenanceHandler.removeCallbacks(maintenanceRunnable)
        unregisterAdapterStateReceiver()
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
        if (clearCallback) {
            callback = null
            devices.clear()
            state = state.copy(devices = emptyList())
        }
    }

    private fun finishScanWithError(message: String) {
        maintenanceHandler.removeCallbacks(maintenanceRunnable)
        unregisterAdapterStateReceiver()
        runCatching {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }

        val visibleDevices = devices.values
            .sortedByDescending { it.rssi }
        state = state.copy(
            scanning = false,
            devices = visibleDevices,
            lastError = message,
        )
        callback?.invoke(state)

        devices.clear()
        state = state.copy(devices = emptyList())
    }

    private fun registerAdapterStateReceiver() {
        if (adapterReceiverRegistered) return

        runCatching {
            ContextCompat.registerReceiver(
                context,
                adapterStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            adapterReceiverRegistered = true
        }
    }

    private fun unregisterAdapterStateReceiver() {
        if (!adapterReceiverRegistered) return

        runCatching {
            context.unregisterReceiver(adapterStateReceiver)
        }
        adapterReceiverRegistered = false
    }

    private fun handleResult(result: ScanResult) {
        val now = SystemClock.elapsedRealtime()
        pruneStale(now)

        val record = result.scanRecord
        val name = DisplaySanitizer.safeSingleLine(
            record?.deviceName ?: "Dispositivo BLE",
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
