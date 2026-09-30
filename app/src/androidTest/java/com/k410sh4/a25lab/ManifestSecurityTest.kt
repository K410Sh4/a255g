package com.k410sh4.a25lab

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestSecurityTest {
    @Test
    fun manifestKeepsPermissionsMinimalAndExportsOnlyLauncher() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES,
        )
        val permissions = info.requestedPermissions?.toSet().orEmpty()

        assertFalse(Manifest.permission.INTERNET in permissions)
        assertFalse(Manifest.permission.BLUETOOTH_CONNECT in permissions)
        assertFalse(Manifest.permission.CAMERA in permissions)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in permissions)
        assertFalse(Manifest.permission.READ_PHONE_STATE in permissions)
        assertFalse(Manifest.permission.READ_CONTACTS in permissions)

        assertTrue(Manifest.permission.NFC in permissions)
        assertTrue(Manifest.permission.BLUETOOTH_SCAN in permissions)
        assertTrue(Manifest.permission.RECORD_AUDIO in permissions)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in permissions)

        val exportedAppActivities = info.activities
            ?.filter { activity ->
                activity.exported &&
                    activity.name.startsWith(context.packageName)
            }
            ?.map { it.name }
            .orEmpty()

        assertEquals(
            listOf(MainActivity::class.java.name),
            exportedAppActivities,
        )
    }
}
