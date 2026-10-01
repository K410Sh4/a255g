package com.k410sh4.a25lab.model

data class GenesisDocument(
    val id: String,
    val name: String,
    val uri: String,
    val importedAtEpochMs: Long,
    val pageCount: Int,
    val extractedPages: Int,
    val extractedChars: Int,
    val truncated: Boolean,
    val fingerprint: String,
    val note: String? = null,
)

data class GenesisChunk(
    val id: String,
    val documentId: String,
    val sourceName: String,
    val page: Int,
    val text: String,
    val terms: List<String>,
)

data class GenesisHypothesis(
    val id: String,
    val statement: String,
    val supportChunkIds: List<String>,
    val supportSourceNames: List<String>,
    val score: Double,
    val feedback: Int = 0,
    val createdAtEpochMs: Long,
    val cycle: Int,
)

data class GenesisBrainPolicy(
    val version: Int = 1,
    val evidenceWeight: Double = 1.0,
    val noveltyWeight: Double = 1.0,
    val feedbackWeight: Double = 1.0,
    val minPairSupport: Int = 2,
    val maxHypothesesPerCycle: Int = 8,
)

data class GenesisState(
    val brainVersion: Int = 1,
    val studyCycle: Int = 0,
    val documents: List<GenesisDocument> = emptyList(),
    val chunks: List<GenesisChunk> = emptyList(),
    val hypotheses: List<GenesisHypothesis> = emptyList(),
    val positiveFeedback: Int = 0,
    val negativeFeedback: Int = 0,
    val lastStudyAtEpochMs: Long? = null,
    val policy: GenesisBrainPolicy = GenesisBrainPolicy(),
)

data class GenesisSearchResult(
    val chunkId: String,
    val sourceName: String,
    val page: Int,
    val text: String,
    val score: Double,
)

data class GenesisImportResult(
    val state: GenesisState,
    val message: String,
)

data class GenesisStudyResult(
    val state: GenesisState,
    val createdHypotheses: Int,
)
