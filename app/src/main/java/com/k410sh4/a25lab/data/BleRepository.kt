package com.k410sh4.a25lab.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.model.BleDeviceInfo
import com.k410sh4.a25lab.model.BleState

class BleRepository(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager.adapter
    private var callback: ((BleState) -> Unit)? = null
    private var state = BleState()
    private val devices = linkedMapOf<String, BleDeviceInfo>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val hasConnectPermission = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

            val address = if (hasConnectPermission) {
                try {
                    result.device.address
                } catch (_: SecurityException) {
                    "indisponível"
                }
            } else {
                "indisponível"
            }

            val platformName = if (hasConnectPermission) {
                try {
                    result.device.name
                } catch (_: SecurityException) {
                    null
                }
            } else {
                null
            }

            val name = record?.deviceName ?: platformName ?: "Dispositivo BLE"
            val key = if (address != "indisponível") {
                address
            } else {
                "$name:${result.rssi}:${record?.bytes?.contentHashCode()}"
            }

            devices[key] = BleDeviceInfo(
                key = key,
                name = name,
                address = address,
                rssi = result.rssi,
                connectable = result.isConnectable,
            )
            publish()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            state = state.copy(scanning = false, lastError = "Falha no scan BLE: código $errorCode")
            callback?.invoke(state)
        }
    }

    fun start(onState: (BleState) -> Unit) {
        callback = onState

        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) ||
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            onState(BleState(lastError = "Permissões Bluetooth de scan e conexão são necessárias."))
            return
        }

        val currentAdapter = adapter
        if (currentAdapter == null) {
            onState(BleState(lastError = "Bluetooth não disponível."))
            return
        }

        val enabled = try {
            currentAdapter.isEnabled
        } catch (_: SecurityException) {
            false
        }
        if (!enabled) {
            onState(BleState(lastError = "Ative o Bluetooth para iniciar o scan."))
            return
        }

        stopInternal(clearCallback = false)
        devices.clear()
        state = BleState(scanning = true)
        callback?.invoke(state)

        try {
            val scanner = currentAdapter.bluetoothLeScanner
            if (scanner == null) {
                state = BleState(lastError = "Scanner BLE indisponível.")
                callback?.invoke(state)
                return
            }
            scanner.startScan(scanCallback)
        } catch (security: SecurityException) {
            state = BleState(lastError = security.message ?: "Acesso Bluetooth negado.")
            callback?.invoke(state)
        }
    }

    fun stop() = stopInternal(clearCallback = true)

    private fun stopInternal(clearCallback: Boolean) {
        if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            try {
                adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {
                // A permissão pode ser revogada entre a checagem e a chamada.
            }
        }

        if (state.scanning) {
            state = state.copy(scanning = false)
            callback?.invoke(state)
        }
        if (clearCallback) callback = null
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun publish() {
        state = state.copy(devices = devices.values.sortedByDescending { it.rssi })
        callback?.invoke(state)
    }
}
