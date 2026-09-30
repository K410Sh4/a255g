package com.k410sh4.a25lab.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplaySanitizerTest {
    @Test
    fun safeText_preservesNormalUnicode() {
        assertEquals(
            "Áudio 🙂",
            DisplaySanitizer.safeText("Áudio 🙂", 32),
        )
    }

    @Test
    fun safeText_escapesControlAndBidiCharacters() {
        val result = DisplaySanitizer.safeText(
            "ok\u202Eevil\u0000",
            64,
        )

        assertTrue(result.contains("\\u202E"))
        assertTrue(result.contains("\\u0000"))
        assertFalse(result.contains('\u202E'))
        assertFalse(result.contains('\u0000'))
    }

    @Test
    fun safeText_truncatesByCodePointWithoutSplittingEmoji() {
        assertEquals(
            "A🙂…",
            DisplaySanitizer.safeText("A🙂B", 2),
        )
    }
}
