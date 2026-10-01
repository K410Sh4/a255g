package com.k410sh4.a25lab.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.k410sh4.a25lab.model.GenesisBrainPolicy
import com.k410sh4.a25lab.model.GenesisChunk
import com.k410sh4.a25lab.model.GenesisDocument
import com.k410sh4.a25lab.model.GenesisHypothesis
import com.k410sh4.a25lab.model.GenesisState
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

class GenesisKnowledgeStore(context: Context) {
    private val root = File(context.noBackupFilesDir, "genesis").apply { mkdirs() }
    private val snapshots = File(root, "snapshots").apply { mkdirs() }
    private val stateFile = File(root, "brain.enc")
    private val tempFile = File(root, "brain.tmp")

    fun load(): GenesisState = synchronized(lock) {
        if (!stateFile.exists()) return@synchronized GenesisState()
        decodeState(decrypt(stateFile.readBytes()))
    }

    fun save(
        state: GenesisState,
        snapshotPrevious: Boolean = true,
    ) = synchronized(lock) {
        if (snapshotPrevious && stateFile.exists()) {
            val snapshot = File(
                snapshots,
                "brain-" + System.currentTimeMillis().toString() + ".enc",
            )
            stateFile.copyTo(snapshot, overwrite = true)
            trimSnapshots()
        }

        val encoded = encodeState(state).toByteArray(StandardCharsets.UTF_8)
        val encrypted = encrypt(encoded)
        tempFile.writeBytes(encrypted)
        if (!tempFile.renameTo(stateFile)) {
            stateFile.writeBytes(encrypted)
            tempFile.delete()
        }
    }

    fun rollback(): GenesisState? = synchronized(lock) {
        val latest = snapshots.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".enc") }
            ?.maxByOrNull { it.lastModified() }
            ?: return@synchronized null

        val current = if (stateFile.exists()) stateFile.readBytes() else null
        val restored = latest.readBytes()

        if (current != null) {
            val backup = File(
                snapshots,
                "brain-before-rollback-" + System.currentTimeMillis().toString() + ".enc",
            )
            backup.writeBytes(current)
        }

        stateFile.writeBytes(restored)
        latest.delete()
        trimSnapshots()
        decodeState(decrypt(restored))
    }

    private fun trimSnapshots() {
        val files = snapshots.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".enc") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        files.drop(MAX_SNAPSHOTS).forEach { it.delete() }
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP)
        return JSONObject()
            .put("v", 1)
            .put("iv", iv)
            .put("body", body)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun decrypt(payload: ByteArray): ByteArray {
        val wrapper = JSONObject(String(payload, StandardCharsets.UTF_8))
        require(wrapper.optInt("v", 0) == 1) {
            "Formato de memória GENESIS não suportado."
        }

        val iv = Base64.decode(wrapper.getString("iv"), Base64.NO_WRAP)
        val body = Base64.decode(wrapper.getString("body"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv),
        )
        return cipher.doFinal(body)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun encodeState(state: GenesisState): String {
        val root = JSONObject()
            .put("brainVersion", state.brainVersion)
            .put("studyCycle", state.studyCycle)
            .put("positiveFeedback", state.positiveFeedback)
            .put("negativeFeedback", state.negativeFeedback)
            .put("lastStudyAtEpochMs", state.lastStudyAtEpochMs ?: JSONObject.NULL)
            .put("policy", encodePolicy(state.policy))

        val documents = JSONArray()
        state.documents.forEach { documents.put(encodeDocument(it)) }
        root.put("documents", documents)

        val chunks = JSONArray()
        state.chunks.forEach { chunks.put(encodeChunk(it)) }
        root.put("chunks", chunks)

        val hypotheses = JSONArray()
        state.hypotheses.forEach { hypotheses.put(encodeHypothesis(it)) }
        root.put("hypotheses", hypotheses)

        return root.toString()
    }

    private fun decodeState(json: ByteArray): GenesisState =
        decodeState(String(json, StandardCharsets.UTF_8))

    private fun decodeState(json: String): GenesisState {
        val root = JSONObject(json)
        return GenesisState(
            brainVersion = root.optInt("brainVersion", 1),
            studyCycle = root.optInt("studyCycle", 0),
            documents = root.optJSONArray("documents").toDocuments(),
            chunks = root.optJSONArray("chunks").toChunks(),
            hypotheses = root.optJSONArray("hypotheses").toHypotheses(),
            positiveFeedback = root.optInt("positiveFeedback", 0),
            negativeFeedback = root.optInt("negativeFeedback", 0),
            lastStudyAtEpochMs = if (root.isNull("lastStudyAtEpochMs")) {
                null
            } else {
                root.optLong("lastStudyAtEpochMs")
            },
            policy = decodePolicy(root.optJSONObject("policy")),
        )
    }

    private fun encodePolicy(policy: GenesisBrainPolicy): JSONObject = JSONObject()
        .put("version", policy.version)
        .put("evidenceWeight", policy.evidenceWeight)
        .put("noveltyWeight", policy.noveltyWeight)
        .put("feedbackWeight", policy.feedbackWeight)
        .put("minPairSupport", policy.minPairSupport)
        .put("maxHypothesesPerCycle", policy.maxHypothesesPerCycle)

    private fun decodePolicy(json: JSONObject?): GenesisBrainPolicy {
        if (json == null) return GenesisBrainPolicy()
        return GenesisBrainPolicy(
            version = json.optInt("version", 1),
            evidenceWeight = json.optDouble("evidenceWeight", 1.0),
            noveltyWeight = json.optDouble("noveltyWeight", 1.0),
            feedbackWeight = json.optDouble("feedbackWeight", 1.0),
            minPairSupport = json.optInt("minPairSupport", 2),
            maxHypothesesPerCycle = json.optInt("maxHypothesesPerCycle", 8),
        )
    }

    private fun encodeDocument(item: GenesisDocument): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("uri", item.uri)
        .put("importedAtEpochMs", item.importedAtEpochMs)
        .put("pageCount", item.pageCount)
        .put("extractedPages", item.extractedPages)
        .put("extractedChars", item.extractedChars)
        .put("truncated", item.truncated)
        .put("fingerprint", item.fingerprint)
        .put("note", item.note ?: JSONObject.NULL)

    private fun decodeDocument(json: JSONObject): GenesisDocument = GenesisDocument(
        id = json.getString("id"),
        name = json.optString("name", "PDF"),
        uri = json.optString("uri", ""),
        importedAtEpochMs = json.optLong("importedAtEpochMs"),
        pageCount = json.optInt("pageCount"),
        extractedPages = json.optInt("extractedPages"),
        extractedChars = json.optInt("extractedChars"),
        truncated = json.optBoolean("truncated"),
        fingerprint = json.optString("fingerprint"),
        note = if (json.isNull("note")) null else json.optString("note"),
    )

    private fun encodeChunk(item: GenesisChunk): JSONObject = JSONObject()
        .put("id", item.id)
        .put("documentId", item.documentId)
        .put("sourceName", item.sourceName)
        .put("page", item.page)
        .put("text", item.text)
        .put("terms", JSONArray(item.terms))

    private fun decodeChunk(json: JSONObject): GenesisChunk = GenesisChunk(
        id = json.getString("id"),
        documentId = json.getString("documentId"),
        sourceName = json.optString("sourceName", "PDF"),
        page = json.optInt("page"),
        text = json.optString("text"),
        terms = json.optJSONArray("terms").toStrings(),
    )

    private fun encodeHypothesis(item: GenesisHypothesis): JSONObject = JSONObject()
        .put("id", item.id)
        .put("statement", item.statement)
        .put("supportChunkIds", JSONArray(item.supportChunkIds))
        .put("supportSourceNames", JSONArray(item.supportSourceNames))
        .put("score", item.score)
        .put("feedback", item.feedback)
        .put("createdAtEpochMs", item.createdAtEpochMs)
        .put("cycle", item.cycle)

    private fun decodeHypothesis(json: JSONObject): GenesisHypothesis = GenesisHypothesis(
        id = json.getString("id"),
        statement = json.optString("statement"),
        supportChunkIds = json.optJSONArray("supportChunkIds").toStrings(),
        supportSourceNames = json.optJSONArray("supportSourceNames").toStrings(),
        score = json.optDouble("score"),
        feedback = json.optInt("feedback"),
        createdAtEpochMs = json.optLong("createdAtEpochMs"),
        cycle = json.optInt("cycle"),
    )

    private fun JSONArray?.toDocuments(): List<GenesisDocument> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { add(decodeDocument(it)) }
            }
        }
    }

    private fun JSONArray?.toChunks(): List<GenesisChunk> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { add(decodeChunk(it)) }
            }
        }
    }

    private fun JSONArray?.toHypotheses(): List<GenesisHypothesis> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { add(decodeHypothesis(it)) }
            }
        }
    }

    private fun JSONArray?.toStrings(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i, "")
                if (value.isNotBlank()) add(value)
            }
        }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "a25lab_genesis_memory_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAX_SNAPSHOTS = 12
        private val lock = Any()
    }
}
