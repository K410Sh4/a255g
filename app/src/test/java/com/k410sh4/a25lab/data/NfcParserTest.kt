package com.k410sh4.a25lab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcParserTest {
    private val textType = byteArrayOf('T'.code.toByte())

    @Test
    fun decodeTextRecord_readsUtf8TextRtd() {
        val payload = byteArrayOf(2) +
            "en".encodeToByteArray() +
            "Olá NFC".encodeToByteArray()

        assertEquals(
            "Olá NFC",
            NfcParser.decodeTextRecord(
                tnf = 1,
                type = textType,
                payload = payload,
            ),
        )
    }

    @Test
    fun decodeTextRecord_rejectsMalformedLanguageLength() {
        val payload = byteArrayOf(10, 'e'.code.toByte())

        assertNull(
            NfcParser.decodeTextRecord(
                tnf = 1,
                type = textType,
                payload = payload,
            ),
        )
    }

    @Test
    fun decodeTextRecord_sanitizesBidiControls() {
        val payload = byteArrayOf(2) +
            "en".encodeToByteArray() +
            "ok\u202Eevil".encodeToByteArray()

        val decoded = NfcParser.decodeTextRecord(
            tnf = 1,
            type = textType,
            payload = payload,
        ).orEmpty()

        assertTrue(decoded.contains("\\u202E"))
        assertFalse(decoded.contains('\u202E'))
    }

    @Test
    fun decodeTextRecord_limitsLargePayloads() {
        val text = "A".repeat(20_000)
        val payload = byteArrayOf(2) +
            "en".encodeToByteArray() +
            text.encodeToByteArray()

        val decoded = NfcParser.decodeTextRecord(
            tnf = 1,
            type = textType,
            payload = payload,
        ).orEmpty()

        assertTrue(decoded.endsWith('…'))
        assertTrue(decoded.length <= 4_096)
    }
}
