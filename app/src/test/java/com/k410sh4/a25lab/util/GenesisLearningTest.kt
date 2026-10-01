package com.k410sh4.a25lab.util

import com.k410sh4.a25lab.model.GenesisBrainPolicy
import com.k410sh4.a25lab.model.GenesisChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenesisLearningTest {
    @Test
    fun searchRanksChunksContainingQueryConcepts() {
        val chunks = listOf(
            GenesisChunk(
                id = "a",
                documentId = "d",
                sourceName = "redes.pdf",
                page = 2,
                text = "DNS resolve nomes e pode utilizar cache para reduzir consultas.",
                terms = GenesisLearning.topTerms(
                    "DNS resolve nomes e pode utilizar cache para reduzir consultas.",
                ),
            ),
            GenesisChunk(
                id = "b",
                documentId = "d",
                sourceName = "redes.pdf",
                page = 8,
                text = "DHCP entrega endereços IP aos clientes da rede.",
                terms = GenesisLearning.topTerms(
                    "DHCP entrega endereços IP aos clientes da rede.",
                ),
            ),
        )

        val results = GenesisLearning.search("Como funciona cache DNS?", chunks)

        assertTrue(results.isNotEmpty())
        assertEquals("a", results.first().first.id)
    }

    @Test
    fun repeatedConceptPairBecomesHypothesisNotFact() {
        val chunks = listOf(
            GenesisChunk(
                id = "a",
                documentId = "d1",
                sourceName = "sinais.pdf",
                page = 1,
                text = "FFT permite analisar vibração periódica.",
                terms = listOf("fft", "vibracao", "periodica"),
            ),
            GenesisChunk(
                id = "b",
                documentId = "d2",
                sourceName = "motores.pdf",
                page = 4,
                text = "Vibração de motores pode ser analisada por FFT.",
                terms = listOf("vibracao", "motores", "fft"),
            ),
        )

        val hypotheses = GenesisLearning.generateHypotheses(
            chunks = chunks,
            existing = emptyList(),
            policy = GenesisBrainPolicy(minPairSupport = 2),
            cycle = 1,
            nowEpochMs = 1L,
        )

        assertTrue(hypotheses.isNotEmpty())
        assertTrue(
            hypotheses.any {
                it.statement.contains("fft") &&
                    it.statement.contains("vibracao") &&
                    it.statement.contains("hipótese")
            },
        )
    }

    @Test
    fun policyAdaptsOnlyAfterFeedbackBatch() {
        val base = GenesisBrainPolicy()

        assertEquals(
            base,
            GenesisLearning.adaptedPolicy(
                base,
                positiveFeedback = 3,
                negativeFeedback = 1,
            ),
        )

        val adapted = GenesisLearning.adaptedPolicy(
            base,
            positiveFeedback = 1,
            negativeFeedback = 4,
        )

        assertTrue(adapted.version > base.version)
        assertTrue(adapted.evidenceWeight > base.evidenceWeight)
    }
}
