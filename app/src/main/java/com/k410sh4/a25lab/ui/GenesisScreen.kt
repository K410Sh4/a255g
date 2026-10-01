package com.k410sh4.a25lab.ui

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k410sh4.a25lab.data.GenesisEngine
import com.k410sh4.a25lab.model.GenesisSearchResult
import com.k410sh4.a25lab.model.GenesisState
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

@Composable
fun GenesisScreen(
    modifier: Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { GenesisEngine(context.applicationContext) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    var state by remember {
        mutableStateOf(
            runCatching { engine.loadState() }.getOrDefault(GenesisState()),
        )
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            "Memória local criptografada. Fontes e hipóteses ficam separadas.",
        )
    }
    var query by remember { mutableStateOf("") }
    var searchResults by remember {
        mutableStateOf<List<GenesisSearchResult>>(emptyList())
    }

    fun runTask(
        startingMessage: String,
        block: () -> Pair<GenesisState?, String>,
    ) {
        if (busy) return
        busy = true
        status = startingMessage
        try {
            executor.execute {
                val result = runCatching(block)
                mainHandler.post {
                    busy = false
                    result.onSuccess { (newState, message) ->
                        if (newState != null) state = newState
                        status = message
                    }.onFailure { error ->
                        status = "Falha: " + (error.message ?: error::class.java.simpleName)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            busy = false
            status = "Executor do GENESIS indisponível."
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            runTask("Lendo e indexando o PDF…") {
                val result = engine.importPdf(uri)
                result.state to result.message
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdownNow()
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "GENESIS",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Memória aprendente v0.1",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Aprende com PDFs, mantém a origem de cada evidência, cria hipóteses e usa seu feedback para ajustar a política de estudo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Cérebro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    GenesisMetric("Brain state", "v" + state.brainVersion)
                    GenesisMetric("Política", "v" + state.policy.version)
                    GenesisMetric("Ciclos de estudo", state.studyCycle.toString())
                    GenesisMetric("Fontes", state.documents.size.toString())
                    GenesisMetric("Blocos de conhecimento", state.chunks.size.toString())
                    GenesisMetric("Hipóteses", state.hypotheses.size.toString())
                    GenesisMetric(
                        "Feedback",
                        "👍 " + state.positiveFeedback + "   👎 " + state.negativeFeedback,
                    )
                    state.lastStudyAtEpochMs?.let {
                        GenesisMetric("Último estudo", formatEpoch(it))
                    }
                }
            }
        }

        if (busy) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Aprender",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 35) {
                                pdfLauncher.launch(arrayOf("application/pdf"))
                            } else {
                                status = "Este importador usa a extração nativa disponível a partir do Android 15."
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Importar PDF")
                    }

                    OutlinedButton(
                        onClick = {
                            runTask("Executando ciclo de estudo…") {
                                val result = engine.study()
                                result.state to (
                                    if (result.createdHypotheses > 0) {
                                        "Estudo concluído: " + result.createdHypotheses +
                                            " nova(s) hipótese(s) criada(s)."
                                    } else {
                                        "Estudo concluído sem novas hipóteses. O GENESIS preservou o estado anterior."
                                    }
                                    )
                            }
                        },
                        enabled = !busy && state.chunks.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Estudar agora")
                    }

                    OutlinedButton(
                        onClick = {
                            runTask("Restaurando snapshot anterior…") {
                                val restored = engine.rollback()
                                restored to if (restored != null) {
                                    "Rollback concluído."
                                } else {
                                    "Ainda não existe snapshot anterior."
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Rollback do último aprendizado")
                    }

                    Text(
                        "O estudo autônomo também é agendado enquanto o aparelho estiver carregando e com bateria em condição segura.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Consultar memória",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Pergunte pelos conceitos do material") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Button(
                        onClick = {
                            val requested = query.trim()
                            if (requested.isBlank()) return@Button
                            runTask("Buscando evidências…") {
                                val results = engine.search(requested)
                                mainHandler.post { searchResults = results }
                                null to if (results.isEmpty()) {
                                    "Nenhuma evidência lexical relevante foi encontrada."
                                } else {
                                    "Foram encontradas " + results.size + " evidência(s) com fonte e página."
                                }
                            }
                        },
                        enabled = !busy && query.isNotBlank() && state.chunks.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Buscar evidências")
                    }
                }
            }
        }

        if (searchResults.isNotEmpty()) {
            item {
                Text(
                    "Evidências encontradas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            searchResults.forEach { result ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                result.sourceName + " · pág. " + result.page,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                result.text.take(900),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        if (state.documents.isNotEmpty()) {
            item {
                Text(
                    "Fontes aprendidas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            state.documents.asReversed().take(8).forEach { document ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                document.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                document.extractedPages.toString() + "/" +
                                    document.pageCount + " páginas com texto · " +
                                    document.extractedChars + " caracteres",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Importado em " + formatEpoch(document.importedAtEpochMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            document.note?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.hypotheses.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Hipóteses do GENESIS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Hipótese não é fato. O feedback abaixo altera o estado aprendente, mas preserva a evidência original.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.hypotheses.take(12).forEach { hypothesis ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                hypothesis.statement,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (hypothesis.supportSourceNames.isNotEmpty()) {
                                Text(
                                    "Fontes: " + hypothesis.supportSourceNames.joinToString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = {
                                        runTask("Aplicando feedback positivo…") {
                                            val next = engine.feedback(hypothesis.id, true)
                                            next to "Feedback positivo incorporado ao cérebro."
                                        }
                                    },
                                    enabled = !busy && hypothesis.feedback != 1,
                                ) {
                                    Text("👍 Útil")
                                }
                                OutlinedButton(
                                    onClick = {
                                        runTask("Aplicando correção…") {
                                            val next = engine.feedback(hypothesis.id, false)
                                            next to "Feedback negativo incorporado; a hipótese foi penalizada."
                                        }
                                    },
                                    enabled = !busy && hypothesis.feedback != -1,
                                ) {
                                    Text("👎 Ruim")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            Text(
                "Kernel v0.1: o GENESIS pode mudar memória, hipóteses e pesos de política dentro de limites. Ele não pode reescrever APK/Kotlin nem permissões do Android por conta própria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GenesisMetric(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatEpoch(epochMs: Long): String =
    DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
    ).format(Date(epochMs))
