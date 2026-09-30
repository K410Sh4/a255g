package com.k410sh4.a25lab.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import com.k410sh4.a25lab.model.DeviceSnapshot
import com.k410sh4.a25lab.model.SystemFeatureInfo
import com.k410sh4.a25lab.util.glEsVersionName

class HardwareProbe(private val context: Context) {
    fun snapshot(
        cameraCount: Int,
        sensorCount: Int,
        featureCount: Int,
    ): DeviceSnapshot {
        val packageManager = context.packageManager
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val stat = StatFs(context.filesDir.absolutePath)
        val metrics = context.resources.displayMetrics
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val batteryTempTenths = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            Int.MIN_VALUE,
        )
        val batteryTemperature = batteryTempTenths
            ?.takeIf { it != Int.MIN_VALUE }
            ?.div(10f)

        val batteryVoltage = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }

        val batteryLevelRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (batteryLevelRaw >= 0 && batteryScale > 0) {
            ((batteryLevelRaw * 100f) / batteryScale).toInt().coerceIn(0, 100)
        } else {
            null
        }

        val batteryHealth = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_HEALTH, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
        val batteryStatus = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }

        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            bootloader = Build.BOOTLOADER,
            buildId = Build.ID,
            buildDisplay = Build.DISPLAY,
            buildFingerprint = Build.FINGERPRINT,
            socManufacturer = Build.SOC_MANUFACTURER,
            socModel = Build.SOC_MODEL,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            supported32BitAbis = Build.SUPPORTED_32_BIT_ABIS.toList(),
            supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS.toList(),
            kernelVersion = System.getProperty("os.version").orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalMemoryBytes = memoryInfo.totalMem,
            availableMemoryBytes = memoryInfo.availMem,
            lowRamDevice = activityManager.isLowRamDevice,
            totalStorageBytes = stat.totalBytes,
            freeStorageBytes = stat.availableBytes,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            refreshRateHz = display?.refreshRate ?: 0f,
            glEsVersion = activityManager.deviceConfigurationInfo.glEsVersion,
            nfcAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC),
            bluetoothLeAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            gpsAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
            cameraAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            microphoneAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            thermalStatus = powerManager.currentThermalStatus,
            batteryTemperatureC = batteryTemperature,
            batteryVoltageMv = batteryVoltage,
            batteryLevelPercent = batteryLevel,
            batteryHealth = batteryHealth,
            batteryStatus = batteryStatus,
            batteryTechnology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            sensorCount = sensorCount,
            cameraCount = cameraCount,
            systemFeatureCount = featureCount,
        )
    }

    fun systemFeatures(): List<SystemFeatureInfo> =
        context.packageManager.systemAvailableFeatures
            .map { feature ->
                SystemFeatureInfo(
                    name = featureName(feature),
                    version = feature.version,
                )
            }
            .sortedBy { it.name }

    private fun featureName(feature: FeatureInfo): String =
        feature.name ?: "OpenGL ES ${glEsVersionName(feature.glEsVersion)}"
}
