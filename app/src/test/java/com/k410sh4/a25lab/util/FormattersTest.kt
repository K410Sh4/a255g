package com.k410sh4.a25lab.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun formatBytes_formatsBinaryUnits() {
        assertEquals("1.00 KiB", formatBytes(1024))
        assertEquals("1.00 MiB", formatBytes(1024L * 1024L))
    }

    @Test
    fun thermalStatusName_mapsKnownValues() {
        assertEquals("Nenhum", thermalStatusName(0))
        assertEquals("Severo", thermalStatusName(3))
    }
}
