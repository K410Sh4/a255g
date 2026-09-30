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
            val address = runCatching { result.device.address }.getOrDefault("indisponível")
            val name = record?.deviceName
                ?: runCatching { result.device.name }.getOrNull()
                ?: "Dispositivo BLE"
            val key = if (address != "indisponível") address else "$name:${result.rssi}:${record?.bytes?.contentHashCode()}"
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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            onState(BleState(lastError = "Permissões Bluetooth de scan e conexão são necessárias."))
            return
        }
        val currentAdapter = adapter
        if (currentAdapter == null) {
            onState(BleState(lastError = "Bluetooth não disponível."))
            return
        }
        if (!currentAdapter.isEnabled) {
            onState(BleState(lastError = "Ative o Bluetooth para iniciar o scan."))
            return
        }
        stopInternal(clearCallback = false)
        devices.clear()
        state = BleState(scanning = true)
        callback?.invoke(state)
        try {
            currentAdapter.bluetoothLeScanner?.startScan(scanCallback)
                ?: onState(BleState(lastError = "Scanner BLE indisponível."))
        } catch (security: SecurityException) {
            onState(BleState(lastError = security.message ?: "Acesso Bluetooth negado."))
        }
    }

    fun stop() = stopInternal(clearCallback = true)

    private fun stopInternal(clearCallback: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        }
        if (state.scanning) {
            state = state.copy(scanning = false)
            callback?.invoke(state)
        }
        if (clearCallback) callback = null
    }

    private fun publish() {
        state = state.copy(devices = devices.values.sortedByDescending { it.rssi })
        callback?.invoke(state)
    }
}
