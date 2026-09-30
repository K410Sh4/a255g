package com.k410sh4.a25lab.util

import com.k410sh4.a25lab.model.CameraInfo
import com.k410sh4.a25lab.model.DeviceSnapshot
import com.k410sh4.a25lab.model.NfcState
import com.k410sh4.a25lab.model.NetworkState
import com.k410sh4.a25lab.model.SensorInfo
import com.k410sh4.a25lab.model.SystemFeatureInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFormatterTest {
    @Test
    fun build_containsCompleteSensorAndFeatureInventory() {
        val snapshot = DeviceSnapshot(
            manufacturer = "Samsung",
            brand = "samsung",
            model = "SM-A256E",
            device = "a25x",
            product = "a25x",
            board = "s5e8825",
            hardware = "exynos1280",
            bootloader = "BL",
            buildId = "ID",
            buildDisplay = "DISPLAY",
            buildFingerprint = "fingerprint",
            socManufacturer = "Samsung",
            socModel = "Exynos 1280",
            supportedAbis = listOf("arm64-v8a"),
            supported32BitAbis = emptyList(),
            supported64BitAbis = listOf("arm64-v8a"),
            kernelVersion = "6.1",
            sdkInt = 36,
            androidRelease = "16",
            securityPatch = "2026-09-01",
            cpuCores = 8,
            totalMemoryBytes = 8L * 1024 * 1024 * 1024,
            availableMemoryBytes = 4L * 1024 * 1024 * 1024,
            lowRamDevice = false,
            totalStorageBytes = 256L * 1024 * 1024 * 1024,
            freeStorageBytes = 128L * 1024 * 1024 * 1024,
            screenWidthPx = 1080,
            screenHeightPx = 2340,
            densityDpi = 420,
            refreshRateHz = 120f,
            glEsVersion = "3.2",
            nfcAvailable = true,
            bluetoothLeAvailable = true,
            gpsAvailable = true,
            cameraAvailable = true,
            microphoneAvailable = true,
            thermalStatus = 0,
            batteryTemperatureC = 31.5f,
            batteryVoltageMv = 4300,
            batteryLevelPercent = 80,
            batteryHealth = null,
            batteryStatus = null,
            batteryTechnology = "Li-ion",
            sensorCount = 1,
            cameraCount = 1,
            systemFeatureCount = 1,
        )
        val sensor = SensorInfo(
            name = "VDIS Gyroscope\nInjected",
            vendor = "Samsung Inc.",
            type = 65607,
            stringType = "com.samsung.sensor.vdis_gyro",
            typeName = "Sensor vendor tipo 65607",
            version = 1,
            maxRange = 17.45f,
            resolution = 0.0005f,
            powerMa = 0.65f,
            minDelayUs = 1000,
            maxDelayUs = 0,
            fifoReservedEventCount = 0,
            fifoMaxEventCount = 0,
            reportingMode = 0,
            wakeUp = false,
            dynamic = false,
            additionalInfoSupported = false,
        )

        val report = ReportFormatter.build(
            snapshot = snapshot,
            sensors = listOf(sensor),
            cameras = listOf(
                CameraInfo(
                    id = "0",
                    facing = "Traseira",
                    hardwareLevel = "FULL",
                    pixelArray = "8160×6120",
                    maxJpeg = "8160×6120",
                    rawSupported = true,
                    manualSensor = true,
                    manualPostProcessing = true,
                    logicalMultiCamera = false,
                    oisModes = listOf("OFF", "ON"),
                ),
            ),
            cameraProbeErrors = listOf("ID 2: probe parcial"),
            probeWarnings = listOf("Rede: IllegalStateException"),
            systemFeatures = listOf(SystemFeatureInfo("android.hardware.nfc", 0)),
            network = NetworkState(
                lastError = "Rede: IllegalStateException",
            ),
            nfc = NfcState(
                available = true,
                enabled = true,
                lastTagIdHex = "DEADBEEF",
                technologies = listOf("Ndef"),
                ndefText = "SECRET-NDEF",
            ),
        )

        assertTrue(report.contains("VDIS Gyroscope Injected"))
        assertFalse(report.contains("\nInjected"))
        assertTrue(report.contains("Delay mínimo: 1000 µs"))
        assertTrue(report.contains("String type: com.samsung.sensor.vdis_gyro"))
        assertTrue(report.contains("android.hardware.nfc"))
        assertTrue(report.contains("Build fingerprint: fingerprint"))
        assertTrue(report.contains("Falhas parciais do Camera2 probe"))
        assertTrue(report.contains("ID 2: probe parcial"))
        assertTrue(report.contains("[AVISOS DO PROBE]"))
        assertTrue(report.contains("Rede: IllegalStateException"))
        assertTrue(report.contains("Erro de leitura: Rede: IllegalStateException"))
        assertTrue(report.contains("Inventário não inclui: IMEI"))
        assertFalse(report.contains("DEADBEEF"))
        assertFalse(report.contains("SECRET-NDEF"))
    }
}
