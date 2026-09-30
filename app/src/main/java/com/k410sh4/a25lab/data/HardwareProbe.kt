package com.k410sh4.a25lab.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.view.WindowManager
import com.k410sh4.a25lab.model.DeviceSnapshot

class HardwareProbe(private val context: Context) {
    fun snapshot(cameraCount: Int, sensorCount: Int): DeviceSnapshot {
        val packageManager = context.packageManager
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val stat = StatFs(context.filesDir.absolutePath)
        val windowManager = context.getSystemService(WindowManager::class.java)
        val display = windowManager.defaultDisplay
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryTempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val batteryTemperature = if (batteryTempTenths != null && batteryTempTenths != Int.MIN_VALUE) {
            batteryTempTenths / 10f
        } else {
            null
        }

        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            socManufacturer = Build.SOC_MANUFACTURER,
            socModel = Build.SOC_MODEL,
            sdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalMemoryBytes = memoryInfo.totalMem,
            availableMemoryBytes = memoryInfo.availMem,
            totalStorageBytes = stat.totalBytes,
            freeStorageBytes = stat.availableBytes,
            refreshRateHz = display.refreshRate,
            nfcAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC),
            bluetoothLeAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            gpsAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
            cameraAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            microphoneAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            thermalStatus = powerManager.currentThermalStatus,
            batteryTemperatureC = batteryTemperature,
            sensorCount = sensorCount,
            cameraCount = cameraCount,
        )
    }
}
