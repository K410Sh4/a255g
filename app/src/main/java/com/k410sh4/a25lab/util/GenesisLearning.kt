package com.k410sh4.a25lab.util

import com.k410sh4.a25lab.model.GenesisBrainPolicy
import com.k410sh4.a25lab.model.GenesisChunk
import com.k410sh4.a25lab.model.GenesisHypothesis
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

object GenesisLearning {
    private const val DEFAULT_CHUNK_CHARS = 1_100
    private const val CHUNK_OVERLAP_SENTENCES = 1

    private val stopWords = setOf(
        "para", "com", "sem", "uma", "umas", "uns", "dos", "das", "que", "por", "como",
        "mais", "menos", "este", "esta", "isso", "esse", "essa", "entre", "sobre", "tambem",
        "quando", "onde", "qual", "quais", "porque", "pelo", "pela", "pelos", "pelas", "ser",
        "sao", "foi", "foram", "tem", "ter", "seu", "sua", "seus", "suas", "ele", "ela",
        "eles", "elas", "nos", "nas", "the", "and", "for", "with", "without", "from", "this",
        "that", "these", "those", "into", "between", "about", "when", "where", "which", "what",
        "have", "has", "had", "will", "would", "could", "should", "their", "there", "than",
    )

    fun normalizeText(text: String): String = text
        .replace('\u0000', ' ')
        .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    fun chunksForPage(
        documentId: String,
        sourceName: String,
        page: Int,
        text: String,
        maxChars: Int = DEFAULT_CHUNK_CHARS,
    ): List<GenesisChunk> {
        val clean = normalizeText(text)
        if (clean.isBlank()) return emptyList()

        val sentences = clean
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.isEmpty()) return emptyList()

        val chunks = mutableListOf<GenesisChunk>()
        var cursor = 0
        var chunkIndex = 0

        while (cursor < sentences.size) {
            val builder = StringBuilder()
            var end = cursor
            while (end < sentences.size) {
                val next = sentences[end]
                val extra = if (builder.isEmpty()) next.length else next.length + 1
                if (builder.isNotEmpty() && builder.length + extra > maxChars) break
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(next)
                end++
            }

            if (end == cursor) {
                val forced = sentences[cursor].take(maxChars)
                builder.append(forced)
                end = cursor + 1
            }

            val chunkText = builder.toString().trim()
            if (chunkText.isNotBlank()) {
                chunks += GenesisChunk(
                    id = "$documentId-p${page}-c${chunkIndex}",
                    documentId = documentId,
                    sourceName = sourceName,
                    page = page,
                    text = chunkText,
                    terms = topTerms(chunkText, 12),
                )
                chunkIndex++
            }

            if (end >= sentences.size) break
            cursor = (end - CHUNK_OVERLAP_SENTENCES).coerceAtLeast(cursor + 1)
        }

        return chunks
    }

    fun topTerms(
        text: String,
        limit: Int = 12,
    ): List<String> {
        val counts = linkedMapOf<String, Int>()
        tokenize(text).forEach { token ->
            if (token.length < 4 || token in stopWords) return@forEach
            counts[token] = (counts[token] ?: 0) + 1
        }

        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
                    .thenBy { it.key },
            )
            .take(limit)
            .map { it.key }
    }

    fun search(
        query: String,
        chunks: List<GenesisChunk>,
        limit: Int = 5,
    ): List<Pair<GenesisChunk, Double>> {
        val queryTerms = tokenize(query)
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()

        if (queryTerms.isEmpty()) return emptyList()

        return chunks
            .map { chunk ->
                val chunkTerms = chunk.terms.toSet()
                val directMatches = queryTerms.intersect(chunkTerms).size
                val textTokens = tokenize(chunk.text).toSet()
                val textMatches = queryTerms.intersect(textTokens).size
                val score =
                    directMatches * 2.0 +
                        textMatches.toDouble() +
                        if (chunk.text.contains(query, ignoreCase = true)) 2.5 else 0.0
                chunk to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
    }

    fun generateHypotheses(
        chunks: List<GenesisChunk>,
        existing: List<GenesisHypothesis>,
        policy: GenesisBrainPolicy,
        cycle: Int,
        nowEpochMs: Long,
    ): List<GenesisHypothesis> {
        if (chunks.isEmpty()) return emptyList()

        data class PairEvidence(
            val chunks: MutableSet<String> = linkedSetOf(),
            val sources: MutableSet<String> = linkedSetOf(),
        )

        val evidence = linkedMapOf<Pair<String, String>, PairEvidence>()
        chunks.forEach { chunk ->
            val terms = chunk.terms.take(8).distinct()
            for (i in terms.indices) {
                for (j in i + 1 until terms.size) {
                    val a = terms[i]
                    val b = terms[j]
                    if (a == b) continue
                    val key = if (a <= b) a to b else b to a
                    val slot = evidence.getOrPut(key) { PairEvidence() }
                    slot.chunks += chunk.id
                    slot.sources += chunk.sourceName
                }
            }
        }

        val existingStatements = existing
            .map { canonical(it.statement) }
            .toSet()

        val ranked = evidence.entries
            .filter { it.value.chunks.size >= policy.minPairSupport }
            .map { (pair, support) ->
                val supportCount = support.chunks.size
                val sourceCount = support.sources.size
                val evidenceScore = min(1.0, supportCount / 6.0)
                val crossSourceScore = min(1.0, sourceCount / 3.0)
                val score = (
                    evidenceScore * policy.evidenceWeight +
                        crossSourceScore * policy.noveltyWeight
                    ) / (policy.evidenceWeight + policy.noveltyWeight).coerceAtLeast(0.01)

                Triple(pair, support, score)
            }
            .sortedByDescending { it.third }

        val output = mutableListOf<GenesisHypothesis>()
        for ((pair, support, score) in ranked) {
            if (output.size >= policy.maxHypothesesPerCycle) break

            val statement =
                "Os conceitos “${pair.first}” e “${pair.second}” aparecem juntos em " +
                    "${support.chunks.size} trechos de ${support.sources.size} fonte(s). " +
                    "Pode existir uma relação relevante entre eles; trate isto como hipótese até validação."

            if (canonical(statement) in existingStatements) continue

            output += GenesisHypothesis(
                id = "h-$cycle-${output.size}-${stableHash(statement)}",
                statement = statement,
                supportChunkIds = support.chunks.take(8),
                supportSourceNames = support.sources.toList().take(6),
                score = score.coerceIn(0.0, 1.0),
                createdAtEpochMs = nowEpochMs,
                cycle = cycle,
            )
        }

        if (output.isEmpty()) {
            val frequent = chunks
                .flatMap { it.terms }
                .groupingBy { it }
                .eachCount()
                .entries
                .filter { it.value >= 2 }
                .sortedByDescending { it.value }
                .take(policy.maxHypothesesPerCycle)

            frequent.forEachIndexed { index, entry ->
                val supporting = chunks.filter { entry.key in it.terms }
                val statement =
                    "O conceito “${entry.key}” é recorrente em ${entry.value} trechos. " +
                        "Vale investigar se ele é central para o material ou apenas repetitivo."

                if (canonical(statement) !in existingStatements) {
                    output += GenesisHypothesis(
                        id = "h-$cycle-$index-${stableHash(statement)}",
                        statement = statement,
                        supportChunkIds = supporting.map { it.id }.take(8),
                        supportSourceNames = supporting.map { it.sourceName }.distinct().take(6),
                        score = min(1.0, entry.value / 8.0),
                        createdAtEpochMs = nowEpochMs,
                        cycle = cycle,
                    )
                }
            }
        }

        return output.take(policy.maxHypothesesPerCycle)
    }

    fun adaptedPolicy(
        policy: GenesisBrainPolicy,
        positiveFeedback: Int,
        negativeFeedback: Int,
    ): GenesisBrainPolicy {
        val total = positiveFeedback + negativeFeedback
        if (total < 5 || total % 5 != 0) return policy

        val positiveRate = positiveFeedback.toDouble() / total.toDouble()
        val evidenceWeight = when {
            positiveRate < 0.45 -> (policy.evidenceWeight + 0.10).coerceAtMost(1.8)
            positiveRate > 0.75 -> (policy.evidenceWeight - 0.05).coerceAtLeast(0.6)
            else -> policy.evidenceWeight
        }
        val noveltyWeight = when {
            positiveRate < 0.45 -> (policy.noveltyWeight - 0.08).coerceAtLeast(0.5)
            positiveRate > 0.75 -> (policy.noveltyWeight + 0.08).coerceAtMost(1.8)
            else -> policy.noveltyWeight
        }

        if (
            evidenceWeight == policy.evidenceWeight &&
            noveltyWeight == policy.noveltyWeight
        ) {
            return policy
        }

        return policy.copy(
            version = policy.version + 1,
            evidenceWeight = evidenceWeight,
            noveltyWeight = noveltyWeight,
        )
    }

    private fun tokenize(text: String): List<String> {
        val folded = Normalizer.normalize(
            text.lowercase(Locale.ROOT),
            Normalizer.Form.NFD,
        ).replace(Regex("\\p{Mn}+"), "")

        return Regex("[\\p{L}\\p{N}]+")
            .findAll(folded)
            .map { it.value }
            .toList()
    }

    private fun canonical(text: String): String =
        normalizeText(text).lowercase(Locale.ROOT)

    private fun stableHash(text: String): String =
        text.hashCode().toUInt().toString(16)
}
