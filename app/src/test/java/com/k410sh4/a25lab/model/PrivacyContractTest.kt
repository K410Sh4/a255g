package com.k410sh4.a25lab.model

import org.junit.Assert.assertFalse
import org.junit.Test

class PrivacyContractTest {
    @Test
    fun bleDeviceInfo_doesNotExposeAddressField() {
        assertFalse(
            BleDeviceInfo::class.java.declaredFields.any {
                it.name.equals("address", ignoreCase = true)
            },
        )
    }
}
