package com.k410sh4.a25lab.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.graphics.pdf.PdfRenderer
import androidx.annotation.RequiresApi
import com.k410sh4.a25lab.model.GenesisChunk
import com.k410sh4.a25lab.model.GenesisDocument
import com.k410sh4.a25lab.util.GenesisLearning
import java.security.MessageDigest

data class GenesisPdfPayload(
    val document: GenesisDocument,
    val chunks: List<GenesisChunk>,
)

class GenesisPdfImporter(
    private val context: Context,
) {
    fun import(uri: Uri): GenesisPdfPayload {
        if (Build.VERSION.SDK_INT < 35) {
            error("Extração nativa de texto PDF requer Android 15/API 35 ou superior.")
        }
        return importApi35(uri)
    }

    @RequiresApi(35)
    private fun importApi35(uri: Uri): GenesisPdfPayload {
        val resolver = context.contentResolver
        val displayName = displayName(resolver, uri)
        val now = System.currentTimeMillis()
        val digest = MessageDigest.getInstance("SHA-256")
        val pages = mutableListOf<Pair<Int, String>>()

        var totalChars = 0
        var extractedPages = 0
        var truncated = false
        var pageCount = 0

        val descriptor = resolver.openFileDescriptor(uri, "r")
            ?: error("Não foi possível abrir o PDF selecionado.")

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                pageCount = renderer.pageCount
                val pagesToRead = minOf(renderer.pageCount, MAX_PAGES)
                if (renderer.pageCount > MAX_PAGES) truncated = true

                for (index in 0 until pagesToRead) {
                    if (totalChars >= MAX_TOTAL_CHARS) {
                        truncated = true
                        break
                    }

                    val pageText = renderer.openPage(index).use { page ->
                        page.textContents
                            .joinToString(separator = "\n") { it.text }
                            .let(GenesisLearning::normalizeText)
                    }

                    if (pageText.isBlank()) continue

                    val room = MAX_TOTAL_CHARS - totalChars
                    val accepted = if (pageText.length > room) {
                        truncated = true
                        pageText.take(room)
                    } else {
                        pageText
                    }

                    digest.update(accepted.toByteArray(Charsets.UTF_8))
                    pages += (index + 1) to accepted
                    totalChars += accepted.length
                    extractedPages++

                    if (truncated && totalChars >= MAX_TOTAL_CHARS) break
                }
            }
        }

        if (pages.isEmpty()) {
            digest.update(displayName.toByteArray(Charsets.UTF_8))
            digest.update(uri.toString().toByteArray(Charsets.UTF_8))
        }

        val fingerprint = digest.digest().joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
        val documentId = "pdf-" + fingerprint.take(20)

        val chunks = pages.flatMap { (page, text) ->
            GenesisLearning.chunksForPage(
                documentId = documentId,
                sourceName = displayName,
                page = page,
                text = text,
            )
        }

        val note = when {
            chunks.isEmpty() ->
                "Nenhum texto incorporado foi encontrado. O arquivo pode ser escaneado; OCR ainda não está ativo."
            truncated ->
                "Importação limitada para proteger memória, bateria e armazenamento."
            else -> null
        }

        return GenesisPdfPayload(
            document = GenesisDocument(
                id = documentId,
                name = displayName,
                uri = uri.toString(),
                importedAtEpochMs = now,
                pageCount = pageCount,
                extractedPages = extractedPages,
                extractedChars = totalChars,
                truncated = truncated,
                fingerprint = fingerprint,
                note = note,
            ),
            chunks = chunks,
        )
    }

    private fun displayName(
        resolver: ContentResolver,
        uri: Uri,
    ): String {
        val queried = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column < 0) null else cursor.getString(column)
            }
        }.getOrNull()

        return queried?.takeIf { it.isNotBlank() } ?: "documento.pdf"
    }

    companion object {
        private const val MAX_PAGES = 400
        private const val MAX_TOTAL_CHARS = 2_000_000
    }
}
