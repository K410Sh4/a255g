package com.k410sh4.a25lab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k410sh4.a25lab.model.AudioState
import com.k410sh4.a25lab.model.BleState
import com.k410sh4.a25lab.model.CameraInfo
import com.k410sh4.a25lab.model.GnssState
import com.k410sh4.a25lab.model.MotionSample
import com.k410sh4.a25lab.model.NetworkState
import com.k410sh4.a25lab.model.NfcState
import com.k410sh4.a25lab.model.SensorInfo
import com.k410sh4.a25lab.util.SuperpowerMath
import java.util.Locale

@Composable
fun PremiumSensorsScreen(
    modifier: Modifier,
    sensors: List<SensorInfo>,
    motion: MotionSample,
) {
    val vendorCount = sensors.count { it.type >= 65_536 }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ToolHeader(
                eyebrow = "LABORATÓRIO",
                title = "Sensores",
                subtitle = "${sensors.size} sensores expostos · $vendorCount vendor/OEM",
            )
        }

        item {
            ToolPanel("Leitura ao vivo") {
                MetricLine("Acelerômetro", "${v3(motion.ax, motion.ay, motion.az)} m/s²")
                MetricLine("Giroscópio", "${v3(motion.gx, motion.gy, motion.gz)} rad/s")
                MetricLine("Magnetômetro", "${v3(motion.mx, motion.my, motion.mz)} µT")
            }
        }

        items(sensors) { sensor ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                sensor.typeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                sensor.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (sensor.type >= 65_536) {
                            MiniBadge("VENDOR")
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    CompactFact("Fabricante", sensor.vendor)
                    CompactFact("Tipo", "${sensor.type} · ${sensor.stringType.ifBlank { "N/D" }}")
                    CompactFact("Faixa", sensor.maxRange.toString())
                    CompactFact("Resolução", sensor.resolution.toString())
                    CompactFact("Delay mínimo", "${sensor.minDelayUs} µs")
                    CompactFact("Consumo declarado", "${sensor.powerMa} mA")
                    if (sensor.wakeUp) MiniBadge("WAKE-UP")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun PremiumGnssScreen(
    modifier: Modifier,
    state: GnssState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    ToolPage(modifier, "CONECTIVIDADE", "GNSS", "Satélites e medições expostas pelo framework Android.") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroTile("Visíveis", state.satellitesVisible.toString(), Modifier.weight(1f))
            HeroTile("Usados no fix", state.satellitesUsed.toString(), Modifier.weight(1f))
        }
        HeroTile(
            "Medições brutas",
            state.rawMeasurementCount.toString(),
            Modifier.fillMaxWidth(),
        )
        CompactFact(
            "Localização do Android",
            if (state.locationEnabled) "ativada" else "desativada",
        )
        CompactFact(
            "Provedor GPS",
            if (state.gpsProviderEnabled) "disponível" else "indisponível",
        )
        CompactFact(
            "Receptor GNSS",
            if (state.engineActive) "ativo" else "parado",
        )
        CompactFact(
            "GNSS raw anunciado",
            if (state.rawMeasurementsSupported) "sim" else "não",
        )
        CompactFact(
            "GNSS raw ativo",
            if (state.rawMeasurementsActive) "sim" else "não",
        )
        CompactFact(
            "Constelações",
            state.constellations.joinToString().ifBlank { "N/D" },
        )
        state.lastError?.let { ErrorMessage(it) }
        StartStopButtons(state.running, onStart, onStop)
        HintText("Ao iniciar, o app solicita GPS somente para manter o receptor ativo. Coordenadas recebidas são descartadas e não são armazenadas nem exibidas.")
    }
}

@Composable
fun PremiumBleScreen(
    modifier: Modifier,
    state: BleState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ToolHeader(
                eyebrow = "CONECTIVIDADE",
                title = "Radar BLE",
                subtitle = "Dispositivos Bluetooth Low Energy visíveis ao A25.",
            )
        }
        item {
            ToolPanel("Varredura") {
                HeroTile(
                    "Dispositivos vistos",
                    state.devices.size.toString(),
                    Modifier.fillMaxWidth(),
                )
                StartStopButtons(state.scanning, onStart, onStop)
                state.lastError?.let { ErrorMessage(it) }
                HintText("RSSI é relativo. Endereços Bluetooth não são exibidos; nomes vistos no anúncio ficam somente na sessão e são limpos ao sair desta tela.")
            }
        }
        items(state.devices, key = { it.key }) { device ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${device.rssi} dBm · ${SuperpowerMath.rssiBand(device.rssi)}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Identificador de hardware não coletado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    device.connectable?.let {
                        MiniBadge(if (it) "CONECTÁVEL" else "NÃO CONECTÁVEL")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun PremiumAudioScreen(
    modifier: Modifier,
    state: AudioState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    ToolPage(modifier, "PERCEPÇÃO", "Ouvido espectral", "Análise local do microfone em tempo real.") {
        HeroTile(
            "Frequência dominante",
            String.format(Locale.US, "%.1f Hz", state.dominantFrequencyHz),
            Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroTile(
                "Nível",
                String.format(Locale.US, "%.1f dBFS", state.rmsDbFs),
                Modifier.weight(1f),
            )
            HeroTile(
                "Sample rate",
                "${state.sampleRateHz} Hz",
                Modifier.weight(1f),
            )
        }
        CompactFact(
            "Fonte de captura",
            state.sourceLabel + if (state.fallbackUsed) " · fallback ativo" else "",
        )
        if (state.starting) {
            HintText("Abrindo a rota de áudio fora da thread da interface…")
        }
        StartStopButtons(state.running || state.starting, onStart, onStop)
        state.lastError?.let { ErrorMessage(it) }
        HintText("PCM e FFT são processados localmente. O app continua sem permissão INTERNET.")
    }
}

@Composable
fun PremiumCamerasScreen(
    modifier: Modifier,
    totalIds: Int,
    cameras: List<CameraInfo>,
    errors: List<String>,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ToolHeader(
                eyebrow = "LABORATÓRIO",
                title = "Camera2",
                subtitle = if (totalIds == cameras.size) {
                    "$totalIds IDs de câmera expostos pela HAL."
                } else {
                    "${cameras.size} de $totalIds IDs lidos com sucesso."
                },
            )
        }
        if (errors.isNotEmpty()) {
            item {
                ToolPanel("Falhas parciais do probe") {
                    errors.forEach { error ->
                        ErrorMessage(error)
                    }
                    HintText("As outras câmeras continuam disponíveis; uma falha isolada não apaga todo o inventário.")
                }
            }
        }
        items(cameras, key = { it.id }) { camera ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        "${camera.facing} · ID ${camera.id}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    MiniBadge(camera.hardwareLevel)
                    CompactFact("Pixel array", camera.pixelArray)
                    CompactFact("Maior JPEG", camera.maxJpeg)
                    CompactFact("OIS", camera.oisModes.joinToString().ifBlank { "não exposto" })
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        MiniBadge(if (camera.rawSupported) "RAW" else "SEM RAW")
                        MiniBadge(if (camera.manualSensor) "MANUAL" else "LIMITADO")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun PremiumNfcScreen(
    modifier: Modifier,
    state: NfcState,
) {
    ToolPage(modifier, "CONECTIVIDADE", "NFC", "Leitura local de tags enquanto o app está em primeiro plano.") {
        HeroTile(
            "Estado",
            if (state.enabled) "Ativado" else if (state.available) "Desativado" else "Ausente",
            Modifier.fillMaxWidth(),
        )
        CompactFact("Última tag", state.lastTagIdHex ?: "Aproxime uma tag NFC")
        CompactFact("Tecnologias", state.technologies.joinToString().ifBlank { "N/D" })
        state.ndefText?.let {
            CompactFact("Texto NDEF (visualização segura)", it)
        }
        state.lastError?.let { ErrorMessage(it) }
        HintText("Reader Mode fica ativo somente nesta tela. Ao sair, os dados da última tag são descartados da memória da sessão.")
    }
}

@Composable
fun PremiumNetworkScreen(
    modifier: Modifier,
    state: NetworkState,
    onRefresh: () -> Unit,
) {
    ToolPage(modifier, "CONECTIVIDADE", "Rede", "Estado da interface ativa segundo NetworkCapabilities.") {
        HeroTile(
            "Estado",
            when {
                state.validated -> "Internet validada"
                state.captivePortal -> "Portal de acesso"
                state.connected && state.internetCapability -> "Rede sem validação"
                state.connected -> "Rede local"
                else -> "Sem rede ativa"
            },
            Modifier.fillMaxWidth(),
        )
        CompactFact(
            "Transportes",
            state.transports.joinToString().ifBlank { "N/D" },
        )
        CompactFact(
            "Capacidade INTERNET",
            if (state.internetCapability) "declarada" else "não declarada",
        )
        CompactFact(
            "Rede validada",
            if (state.validated) "sim" else "não",
        )
        CompactFact(
            "Portal cativo",
            if (state.captivePortal) "detectado" else "não detectado",
        )
        CompactFact("Medida", if (state.metered) "sim" else "não")
        state.lastError?.let { ErrorMessage(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroTile("Down", "${state.downstreamKbps} kbps", Modifier.weight(1f))
            HeroTile("Up", "${state.upstreamKbps} kbps", Modifier.weight(1f))
        }
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Atualizar")
        }
        HintText("Down/Up são valores declarados pelo sistema, não um speed test.")
    }
}

@Composable
fun PremiumComputeScreen(
    modifier: Modifier,
    running: Boolean,
    elapsedMs: Long?,
    gflops: Double?,
    error: String?,
    onRun: () -> Unit,
) {
    ToolPage(modifier, "LABORATÓRIO", "Compute / IA", "Baseline verificável antes de comparar backends de ML.") {
        HeroTile(
            "Tempo CPU",
            elapsedMs?.let { "$it ms" } ?: "Não medido",
            Modifier.fillMaxWidth(),
        )
        CompactFact(
            "Estimativa",
            gflops?.let { String.format(Locale.US, "%.3f GFLOP/s", it) } ?: "N/D",
        )
        CompactFact("Carga", "Matriz 160×160 FP32")
        CompactFact("Medição", "1 aquecimento + mediana de 3 execuções")
        error?.let { ErrorMessage(it) }
        Button(
            onClick = onRun,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (running) "Executando…" else "Executar baseline")
        }
        HorizontalDivider()
        Text(
            "Aceleração de IA",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        HintText(
            "A presença de NPU no Exynos 1280 não prova que um delegate a esteja usando. " +
                "A comparação real será feita com o mesmo modelo em backends disponíveis.",
        )
    }
}

@Composable
private fun ToolPage(
    modifier: Modifier,
    eyebrow: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ToolHeader(eyebrow, title, subtitle) }
        item {
            ToolPanel(title) {
                content()
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ToolHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolPanel(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun HeroTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CompactFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MiniBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StartStopButtons(
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onStart,
            enabled = !running,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (running) "Ativo" else "Iniciar")
        }
        OutlinedButton(
            onClick = onStop,
            enabled = running,
            modifier = Modifier.weight(1f),
        ) {
            Text("Parar")
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun v3(x: Float, y: Float, z: Float): String =
    String.format(Locale.US, "%.2f, %.2f, %.2f", x, y, z)
