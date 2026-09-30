package com.k410sh4.a25lab.data

import android.nfc.Tag
import android.nfc.tech.Ndef
import com.k410sh4.a25lab.model.NfcState
import com.k410sh4.a25lab.util.DisplaySanitizer
import java.nio.charset.Charset
import java.util.Locale

object NfcParser {
    private const val MAX_NDEF_TEXT_CHARS = 4_096
    private const val MAX_NDEF_PAYLOAD_BYTES = 16_384
    fun parse(tag: Tag, available: Boolean, enabled: Boolean): NfcState {
        val idHex = tag.id.joinToString("") {
            "%02X".format(Locale.US, it.toInt() and 0xFF)
        }
        val techs = tag.techList.map { it.substringAfterLast('.') }

        var ndefText: String? = null
        var ndefError: String? = null
        val ndef = runCatching { Ndef.get(tag) }
            .onFailure {
                ndefError = "Falha ao abrir NDEF: ${it::class.java.simpleName}"
            }
            .getOrNull()

        if (ndef != null) {
            try {
                ndef.connect()
                ndefText = ndef.ndefMessage
                    ?.records
                    ?.firstNotNullOfOrNull { record ->
                        decodeTextRecord(
                            record.tnf,
                            record.type,
                            record.payload,
                        )
                    }
            } catch (error: Exception) {
                ndefError =
                    "Falha ao ler NDEF: ${error::class.java.simpleName}"
            } finally {
                runCatching { ndef.close() }
                    .onFailure { closeError ->
                        if (ndefError == null) {
                            ndefError =
                                "Falha ao fechar NDEF: ${closeError::class.java.simpleName}"
                        }
                    }
            }
        }

        return NfcState(
            available = available,
            enabled = enabled,
            lastTagIdHex = idHex,
            technologies = techs,
            ndefText = ndefText,
            lastError = ndefError,
        )
    }

    internal fun decodeTextRecord(
        tnf: Short,
        type: ByteArray,
        payload: ByteArray,
    ): String? {
        if (
            tnf.toInt() != 1 ||
            type.decodeToString() != "T" ||
            payload.isEmpty()
        ) {
            return null
        }

        val status = payload[0].toInt()
        val utf16 = status and 0x80 != 0
        val languageLength = status and 0x3F
        val textStart = 1 + languageLength
        if (textStart > payload.size) return null

        var textEnd = minOf(
            payload.size,
            textStart + MAX_NDEF_PAYLOAD_BYTES,
        )
        if (utf16 && (textEnd - textStart) % 2 != 0) {
            textEnd--
        }

        val payloadTruncated = textEnd < payload.size
        val charset = if (utf16) {
            Charset.forName("UTF-16")
        } else {
            Charsets.UTF_8
        }
        val decoded = payload
            .copyOfRange(textStart, textEnd)
            .toString(charset)

        val displayBudget = if (payloadTruncated) {
            MAX_NDEF_TEXT_CHARS - 1
        } else {
            MAX_NDEF_TEXT_CHARS
        }
        val safe = DisplaySanitizer.safeText(
            decoded,
            displayBudget,
        )

        return if (
            payloadTruncated &&
            !safe.endsWith('…')
        ) {
            "$safe…"
        } else {
            safe
        }
    }
}
