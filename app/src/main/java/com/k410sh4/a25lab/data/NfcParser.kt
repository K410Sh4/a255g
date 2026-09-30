package com.k410sh4.a25lab.data

import android.nfc.Tag
import android.nfc.tech.Ndef
import com.k410sh4.a25lab.model.NfcState
import com.k410sh4.a25lab.util.DisplaySanitizer
import java.nio.charset.Charset
import java.util.Locale

object NfcParser {
    private const val MAX_NDEF_TEXT_CHARS = 4_096
    fun parse(tag: Tag, available: Boolean, enabled: Boolean): NfcState {
        val idHex = tag.id.joinToString("") { "%02X".format(Locale.US, it.toInt() and 0xFF) }
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val ndefText = runCatching {
            val ndef = Ndef.get(tag) ?: return@runCatching null
            ndef.connect()
            try {
                ndef.ndefMessage?.records?.firstNotNullOfOrNull { record ->
                    decodeTextRecord(record.tnf, record.type, record.payload)
                }
            } finally {
                ndef.close()
            }
        }.getOrNull()
        return NfcState(
            available = available,
            enabled = enabled,
            lastTagIdHex = idHex,
            technologies = techs,
            ndefText = ndefText,
        )
    }

    private fun decodeTextRecord(tnf: Short, type: ByteArray, payload: ByteArray): String? {
        if (tnf.toInt() != 1 || type.decodeToString() != "T" || payload.isEmpty()) return null
        val status = payload[0].toInt()
        val utf16 = status and 0x80 != 0
        val languageLength = status and 0x3F
        if (1 + languageLength > payload.size) return null
        val charset = if (utf16) Charset.forName("UTF-16") else Charsets.UTF_8
        val decoded = payload
            .copyOfRange(1 + languageLength, payload.size)
            .toString(charset)

        return DisplaySanitizer.safeText(
            decoded,
            MAX_NDEF_TEXT_CHARS,
        )
    }
}
