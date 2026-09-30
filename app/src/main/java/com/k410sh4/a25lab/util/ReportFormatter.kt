package com.k410sh4.a25lab.util

import android.os.BatteryManager
import com.k410sh4.a25lab.model.CameraInfo
import com.k410sh4.a25lab.model.DeviceSnapshot
import com.k410sh4.a25lab.model.NetworkState
import com.k410sh4.a25lab.model.NfcState
import com.k410sh4.a25lab.model.SensorInfo
import com.k410sh4.a25lab.model.SystemFeatureInfo
import java.util.Locale

object ReportFormatter {
    fun build(
        snapshot: DeviceSnapshot?,
        sensors: List<SensorInfo>,
        cameras: List<CameraInfo>,
        cameraProbeErrors: List<String> = emptyList(),
        probeWarnings: List<String> = emptyList(),
        systemFeatures: List<SystemFeatureInfo> = emptyList(),
        network: NetworkState = NetworkState(),
        nfc: NfcState = NfcState(),
    ): String {
        if (snapshot == null) return "A25 Lab: snapshot indisponível."

        return buildString {
            appendLine("A25 LAB — INVENTÁRIO INTERNO COMPLETO")
            appendLine("Gerado localmente pelo aparelho. Este inventário não inclui identificadores pessoais.")
            appendLine()

            appendLine("[DISPOSITIVO / BUILD]")
            appendLine("Fabricante: ${safeLine(snapshot.manufacturer)}")
            appendLine("Marca: ${safeLine(snapshot.brand)}")
            appendLine("Modelo: ${safeLine(snapshot.model)}")
            appendLine("Device: ${safeLine(snapshot.device)}")
            appendLine("Product: ${safeLine(snapshot.product)}")
            appendLine("Board: ${safeLine(snapshot.board)}")
            appendLine("Hardware: ${safeLine(snapshot.hardware)}")
            appendLine("Bootloader: ${safeLine(snapshot.bootloader)}")
            appendLine("Build ID: ${safeLine(snapshot.buildId)}")
            appendLine("Build display: ${safeLine(snapshot.buildDisplay)}")
            appendLine("Build fingerprint: ${safeLine(snapshot.buildFingerprint)}")
            appendLine("Android: ${safeLine(snapshot.androidRelease)} / API ${snapshot.sdkInt}")
            appendLine("Patch de segurança: ${safeLine(snapshot.securityPatch)}")
            appendLine("Kernel: ${safeLine(snapshot.kernelVersion)}")
            appendLine()

            appendLine("[SOC / CPU / ABI]")
            appendLine("SoC: ${safeLine(snapshot.socManufacturer)} ${safeLine(snapshot.socModel)}")
            appendLine("CPU cores lógicos: ${snapshot.cpuCores}")
            appendLine("ABIs: ${safeLine(snapshot.supportedAbis.joinToString().ifBlank { "N/D" })}")
            appendLine("ABIs 32-bit: ${safeLine(snapshot.supported32BitAbis.joinToString().ifBlank { "nenhuma" })}")
            appendLine("ABIs 64-bit: ${safeLine(snapshot.supported64BitAbis.joinToString().ifBlank { "nenhuma" })}")
            appendLine("OpenGL ES: ${safeLine(snapshot.glEsVersion)}")
            appendLine()

            appendLine("[MEMÓRIA / PARTIÇÃO DE DADOS]")
            appendLine("RAM total: ${formatBytes(snapshot.totalMemoryBytes)}")
            appendLine("RAM disponível: ${formatBytes(snapshot.availableMemoryBytes)}")
            appendLine("Low-RAM device: ${yesNo(snapshot.lowRamDevice)}")
            appendLine("Partição de dados total: ${formatBytes(snapshot.totalStorageBytes)}")
            appendLine("Partição de dados livre: ${formatBytes(snapshot.freeStorageBytes)}")
            appendLine()

            appendLine("[TELA]")
            appendLine("Resolução atual: ${snapshot.screenWidthPx}×${snapshot.screenHeightPx} px")
            appendLine("Densidade: ${snapshot.densityDpi} dpi")
            appendLine("Refresh rate: ${String.format(Locale.US, "%.2f Hz", snapshot.refreshRateHz)}")
            appendLine()

            appendLine("[BATERIA / TÉRMICO]")
            appendLine("Bateria: ${snapshot.batteryLevelPercent?.let { "$it%" } ?: "N/D"}")
            appendLine("Temperatura: ${snapshot.batteryTemperatureC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "N/D"}")
            appendLine("Tensão: ${snapshot.batteryVoltageMv?.let { "$it mV" } ?: "N/D"}")
            appendLine("Tecnologia: ${safeLine(snapshot.batteryTechnology ?: "N/D")}")
            appendLine("Saúde: ${batteryHealthName(snapshot.batteryHealth)}")
            appendLine("Estado: ${batteryStatusName(snapshot.batteryStatus)}")
            appendLine("Status térmico: ${thermalStatusName(snapshot.thermalStatus)}")
            appendLine()

            appendLine("[RECURSOS PRINCIPAIS]")
            appendLine("NFC: ${yesNo(snapshot.nfcAvailable)} / ligado agora: ${yesNo(nfc.enabled)}")
            appendLine("BLE: ${yesNo(snapshot.bluetoothLeAvailable)}")
            appendLine("GNSS: ${yesNo(snapshot.gpsAvailable)}")
            appendLine("Câmera: ${yesNo(snapshot.cameraAvailable)}")
            appendLine("Microfone: ${yesNo(snapshot.microphoneAvailable)}")
            appendLine("Sensores expostos: ${snapshot.sensorCount}")
            appendLine("Camera2 IDs: ${snapshot.cameraCount}")
            appendLine("System features: ${snapshot.systemFeatureCount}")
            appendLine()

            appendLine("[REDE ATIVA]")
            appendLine("Rede ativa: ${yesNo(network.connected)}")
            appendLine("Capacidade INTERNET: ${yesNo(network.internetCapability)}")
            appendLine("Validada pelo Android: ${yesNo(network.validated)}")
            appendLine("Portal cativo: ${yesNo(network.captivePortal)}")
            appendLine("Medida: ${yesNo(network.metered)}")
            appendLine("Transportes: ${network.transports.joinToString().ifBlank { "N/D" }}")
            appendLine("Downstream declarado: ${network.downstreamKbps} kbps")
            appendLine("Upstream declarado: ${network.upstreamKbps} kbps")
            appendLine()

            appendLine("[SENSORES — ${sensors.size}]")
            sensors.forEachIndexed { index, sensor ->
                appendLine("#${index + 1} ${safeLine(sensor.typeName)}")
                appendLine("  Nome: ${safeLine(sensor.name)}")
                appendLine("  Fabricante: ${safeLine(sensor.vendor)}")
                appendLine("  Tipo numérico: ${sensor.type}")
                appendLine("  String type: ${safeLine(sensor.stringType.ifBlank { "N/D" })}")
                appendLine("  Versão: ${sensor.version}")
                appendLine("  Resolução: ${sensor.resolution}")
                appendLine("  Faixa máxima: ${sensor.maxRange}")
                appendLine("  Consumo declarado: ${sensor.powerMa} mA")
                appendLine("  Delay mínimo: ${sensor.minDelayUs} µs")
                appendLine("  Delay máximo: ${sensor.maxDelayUs} µs")
                appendLine("  FIFO reservado: ${sensor.fifoReservedEventCount}")
                appendLine("  FIFO máximo: ${sensor.fifoMaxEventCount}")
                appendLine("  Reporting mode: ${reportingModeName(sensor.reportingMode)} (${sensor.reportingMode})")
                appendLine("  Wake-up: ${yesNo(sensor.wakeUp)}")
                appendLine("  Dinâmico: ${yesNo(sensor.dynamic)}")
                appendLine("  Additional info: ${yesNo(sensor.additionalInfoSupported)}")
            }
            appendLine()

            appendLine("[CÂMERAS — ${cameras.size} lidas / ${snapshot.cameraCount} IDs]")
            cameras.forEach { camera ->
                appendLine("ID ${safeLine(camera.id)} — ${safeLine(camera.facing)}")
                appendLine("  Hardware level: ${safeLine(camera.hardwareLevel)}")
                appendLine("  Pixel array: ${safeLine(camera.pixelArray)}")
                appendLine("  Maior JPEG: ${safeLine(camera.maxJpeg)}")
                appendLine("  RAW: ${yesNo(camera.rawSupported)}")
                appendLine("  Manual sensor: ${yesNo(camera.manualSensor)}")
                appendLine("  Manual pós-processamento: ${yesNo(camera.manualPostProcessing)}")
                appendLine("  Logical multi-camera: ${yesNo(camera.logicalMultiCamera)}")
                appendLine("  OIS modes: ${safeLine(camera.oisModes.joinToString().ifBlank { "não exposto" })}")
            }
            appendLine()

            if (cameraProbeErrors.isNotEmpty()) {
                appendLine("Falhas parciais do Camera2 probe:")
                cameraProbeErrors.forEach { error ->
                    appendLine("  - ${safeLine(error)}")
                }
                appendLine()
            }

            if (probeWarnings.isNotEmpty()) {
                appendLine("[AVISOS DO PROBE]")
                probeWarnings.forEach { warning ->
                    appendLine("- ${safeLine(warning)}")
                }
                appendLine()
            }

            appendLine("[SYSTEM FEATURES — ${systemFeatures.size}]")
            systemFeatures.forEach { feature ->
                appendLine("- ${safeLine(feature.name)}${if (feature.version > 0) " (v${feature.version})" else ""}")
            }
            appendLine()

            appendLine("[PRIVACIDADE]")
            appendLine("Inventário não inclui: IMEI, número de telefone, Android ID, contas, contatos ou histórico de localização.")
            appendLine("GNSS: quando iniciado, coordenadas podem ser entregues pela API apenas para manter o receptor ativo; são descartadas imediatamente e não entram no relatório.")
            appendLine("NFC: UID/NDEF podem ser exibidos temporariamente na tela NFC; são descartados ao sair/ocultar o app e não entram no inventário.")
            appendLine("As informações do inventário são especificações/capacidades expostas por APIs públicas do Android.")
        }
    }

    private fun safeLine(value: String): String =
        DisplaySanitizer.safeSingleLine(
            input = value,
            maxCodePoints = 512,
        )

    private fun yesNo(value: Boolean) = if (value) "sim" else "não"

    private fun reportingModeName(mode: Int): String = when (mode) {
        0 -> "contínuo"
        1 -> "mudança"
        2 -> "one-shot"
        3 -> "especial"
        else -> "desconhecido"
    }

    private fun batteryHealthName(value: Int?): String = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "boa"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "superaquecida"
        BatteryManager.BATTERY_HEALTH_DEAD -> "morta"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "sobretensão"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "falha"
        BatteryManager.BATTERY_HEALTH_COLD -> "fria"
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> "desconhecida"
        null -> "N/D"
        else -> "código $value"
    }

    private fun batteryStatusName(value: Int?): String = when (value) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "carregando"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "descarregando"
        BatteryManager.BATTERY_STATUS_FULL -> "cheia"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "não carregando"
        BatteryManager.BATTERY_STATUS_UNKNOWN -> "desconhecido"
        null -> "N/D"
        else -> "código $value"
    }
}
