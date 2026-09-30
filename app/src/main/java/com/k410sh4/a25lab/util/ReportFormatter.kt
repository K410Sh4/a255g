package com.k410sh4.a25lab.util

import com.k410sh4.a25lab.model.CameraInfo
import com.k410sh4.a25lab.model.DeviceSnapshot
import com.k410sh4.a25lab.model.SensorInfo

object ReportFormatter {
    fun build(snapshot: DeviceSnapshot?, sensors: List<SensorInfo>, cameras: List<CameraInfo>): String {
        if (snapshot == null) return "A25 Lab: snapshot indisponível."
        return buildString {
            appendLine("A25 LAB — RELATÓRIO DE CAPACIDADES")
            appendLine("Modelo: ${snapshot.manufacturer} ${snapshot.model} (${snapshot.device})")
            appendLine("Android: ${snapshot.androidRelease} / API ${snapshot.sdkInt}")
            appendLine("Patch de segurança: ${snapshot.securityPatch}")
            appendLine("SoC: ${snapshot.socManufacturer} ${snapshot.socModel}")
            appendLine("Hardware: ${snapshot.hardware}")
            appendLine("CPU cores lógicos: ${snapshot.cpuCores}")
            appendLine("RAM: ${formatBytes(snapshot.totalMemoryBytes)} total / ${formatBytes(snapshot.availableMemoryBytes)} disponível")
            appendLine("Armazenamento: ${formatBytes(snapshot.totalStorageBytes)} total / ${formatBytes(snapshot.freeStorageBytes)} livre")
            appendLine("Tela: %.1f Hz".format(snapshot.refreshRateHz))
            appendLine("Térmico: ${thermalStatusName(snapshot.thermalStatus)}")
            appendLine("Bateria: ${snapshot.batteryTemperatureC?.let { "%.1f °C".format(it) } ?: "N/D"}")
            appendLine("NFC: ${yesNo(snapshot.nfcAvailable)}")
            appendLine("BLE: ${yesNo(snapshot.bluetoothLeAvailable)}")
            appendLine("GNSS: ${yesNo(snapshot.gpsAvailable)}")
            appendLine("Câmera: ${yesNo(snapshot.cameraAvailable)} (${snapshot.cameraCount} IDs Camera2)")
            appendLine("Microfone: ${yesNo(snapshot.microphoneAvailable)}")
            appendLine("Sensores expostos: ${snapshot.sensorCount}")
            appendLine()
            appendLine("SENSORES")
            sensors.forEach { appendLine("- ${it.typeName}: ${it.name} / ${it.vendor} / ${it.powerMa} mA") }
            appendLine()
            appendLine("CÂMERAS")
            cameras.forEach {
                appendLine("- ID ${it.id} ${it.facing}: ${it.hardwareLevel}, pixels ${it.pixelArray}, JPEG ${it.maxJpeg}, RAW=${it.rawSupported}, Manual=${it.manualSensor}, OIS=${it.oisModes.joinToString()}")
            }
            appendLine()
            appendLine("Observação: capacidades do SoC não implicam que o firmware/HAL exponha o recurso a aplicativos. Este relatório registra APIs públicas observáveis pelo app.")
        }
    }

    private fun yesNo(value: Boolean) = if (value) "sim" else "não"
}
