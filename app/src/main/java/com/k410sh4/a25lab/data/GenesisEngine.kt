package com.k410sh4.a25lab.data

import android.content.Context
import android.net.Uri
import com.k410sh4.a25lab.model.GenesisImportResult
import com.k410sh4.a25lab.model.GenesisSearchResult
import com.k410sh4.a25lab.model.GenesisState
import com.k410sh4.a25lab.model.GenesisStudyResult
import com.k410sh4.a25lab.util.GenesisLearning

class GenesisEngine(context: Context) {
    private val store = GenesisKnowledgeStore(context.applicationContext)
    private val pdfImporter = GenesisPdfImporter(context.applicationContext)

    fun loadState(): GenesisState = store.load()

    fun importPdf(uri: Uri): GenesisImportResult {
        val payload = pdfImporter.import(uri)
        val current = store.load()

        if (current.documents.any { it.fingerprint == payload.document.fingerprint }) {
            return GenesisImportResult(
                state = current,
                message = "Este PDF já faz parte da memória do GENESIS.",
            )
        }

        require(current.chunks.size + payload.chunks.size <= MAX_CHUNKS) {
            "Limite seguro de memória atingido. Remova fontes antigas em uma versão futura antes de importar mais conteúdo."
        }

        val next = current.copy(
            brainVersion = current.brainVersion + 1,
            documents = current.documents + payload.document,
            chunks = current.chunks + payload.chunks,
        )
        store.save(next)

        val message = when {
            payload.chunks.isEmpty() ->
                "PDF registrado, mas nenhum texto incorporado foi extraído. OCR será necessário para este arquivo."
            payload.document.truncated ->
                "PDF aprendido parcialmente: limites de segurança impediram carregar o documento inteiro."
            else ->
                "PDF aprendido: " + payload.document.extractedPages + " página(s), " +
                    payload.chunks.size + " bloco(s) de conhecimento com proveniência."
        }

        return GenesisImportResult(
            state = next,
            message = message,
        )
    }

    fun study(): GenesisStudyResult {
        val current = store.load()
        val nextCycle = current.studyCycle + 1
        val now = System.currentTimeMillis()

        val created = GenesisLearning.generateHypotheses(
            chunks = current.chunks,
            existing = current.hypotheses,
            policy = current.policy,
            cycle = nextCycle,
            nowEpochMs = now,
        )

        val next = current.copy(
            brainVersion = current.brainVersion + if (created.isNotEmpty()) 1 else 0,
            studyCycle = nextCycle,
            hypotheses = (created + current.hypotheses)
                .distinctBy { it.id }
                .take(MAX_HYPOTHESES),
            lastStudyAtEpochMs = now,
        )
        store.save(next)

        return GenesisStudyResult(
            state = next,
            createdHypotheses = created.size,
        )
    }

    fun feedback(
        hypothesisId: String,
        positive: Boolean,
    ): GenesisState {
        val current = store.load()
        val value = if (positive) 1 else -1
        var changed = false

        val hypotheses = current.hypotheses.map { hypothesis ->
            if (hypothesis.id != hypothesisId || hypothesis.feedback == value) {
                hypothesis
            } else {
                changed = true
                hypothesis.copy(feedback = value)
            }
        }

        if (!changed) return current

        val positiveFeedback = current.positiveFeedback + if (positive) 1 else 0
        val negativeFeedback = current.negativeFeedback + if (positive) 0 else 1
        val adaptedPolicy = GenesisLearning.adaptedPolicy(
            policy = current.policy,
            positiveFeedback = positiveFeedback,
            negativeFeedback = negativeFeedback,
        )

        val next = current.copy(
            brainVersion = current.brainVersion + 1,
            hypotheses = hypotheses,
            positiveFeedback = positiveFeedback,
            negativeFeedback = negativeFeedback,
            policy = adaptedPolicy,
        )
        store.save(next)
        return next
    }

    fun search(
        query: String,
        limit: Int = 5,
    ): List<GenesisSearchResult> {
        val state = store.load()
        return GenesisLearning.search(
            query = query,
            chunks = state.chunks,
            limit = limit,
        ).map { (chunk, score) ->
            GenesisSearchResult(
                chunkId = chunk.id,
                sourceName = chunk.sourceName,
                page = chunk.page,
                text = chunk.text,
                score = score,
            )
        }
    }

    fun rollback(): GenesisState? = store.rollback()

    companion object {
        private const val MAX_CHUNKS = 12_000
        private const val MAX_HYPOTHESES = 300
    }
}
