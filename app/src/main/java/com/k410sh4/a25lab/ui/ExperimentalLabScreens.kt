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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k410sh4.a25lab.model.MagneticMapperState
import com.k410sh4.a25lab.model.SensorInfo
import com.k410sh4.a25lab.model.SensorQualificationState
import com.k410sh4.a25lab.model.SensorStreamMetrics
import com.k410sh4.a25lab.model.StabilizationLabState
import com.k410sh4.a25lab.model.VibrationLabState
import java.util.Locale

@Composable
fun VibrationLabScreen(
    modifier: Modifier,
    state: VibrationLabState,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LabHeader(
                eyebrow = "EXPERIMENTAL",
                title = "Vibration Lab",
                subtitle = "Transforma o A25 em um analisador de vibração usando aceleração, giroscópio e AOIS quando disponível.",
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Assinatura atual",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    MetricRow("Fonte", state.sourceLabel)
                    MetricRow(
                        "Frequência dominante",
                        if (state.dominantFrequencyHz > 0f) {
                            f1(state.dominantFrequencyHz) + " Hz"
                        } else {
                            "Coletando janela…"
                        },
                    )
                    MetricRow(
                        "Confiança espectral",
                        if (state.dominantFrequencyHz > 0f) {
                            f0(state.dominantConfidence * 100f) + "%"
                        } else {
                            "—"
                        },
                    )
                    state.lastError?.let { ErrorText(it) }
                }
            }
        }
        item {
            StreamMetricsCard(
                title = "Aceleração dinâmica",
                metrics = state.acceleration,
                magnitudeUnit = "m/s²",
            )
        }
        item {
            StreamMetricsCard(
                title = "Giroscópio físico",
                metrics = state.gyroscope,
                magnitudeUnit = "rad/s",
            )
        }
        item {
            StreamMetricsCard(
                title = "AOIS Samsung",
                metrics = state.aois,
                magnitudeUnit = "unidade do sensor",
            )
        }
        item {
            Hint(
                "Use com o aparelho apoiado sobre uma superfície ou equipamento. A frequência dominante é uma medição do sinal capturado, não um diagnóstico automático de falha mecânica.",
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun StabilizationLabScreen(
    modifier: Modifier,
    state: StabilizationLabState,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LabHeader(
                eyebrow = "SAMSUNG SENSOR LAB",
                title = "Stabilization Analyzer",
                subtitle = "Mede o que cada stream realmente entrega. Hz observado e jitter vêm dos timestamps recebidos, não do minDelay declarado.",
            )
        }
        state.lastError?.let { message ->
            item { ErrorText(message) }
        }
        item {
            StreamMetricsCard(
                title = "ICM42632M / gyro padrão",
                metrics = state.physicalGyro,
                magnitudeUnit = "rad/s",
            )
        }
        item {
            StreamMetricsCard(
                title = "AOIS",
                metrics = state.aois,
                magnitudeUnit = "unidade do sensor",
            )
        }
        item {
            StreamMetricsCard(
                title = "VDIS gyro",
                metrics = state.vdis,
                magnitudeUnit = "unidade do sensor",
            )
        }
        item {
            Hint(
                "Faça movimentos curtos e repetíveis. O laboratório compara a taxa realmente entregue por cada stream.",
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun MagneticMapperScreen(
    modifier: Modifier,
    state: MagneticMapperState,
    onCapture: () -> Unit,
    onReset: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LabHeader(
                eyebrow = "FIELD MAPPER",
                title = "Magnetic Mapper",
                subtitle = "Faça uma varredura 5×5. Mantenha orientação e distância consistentes e capture um ponto por posição.",
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetricRow(
                        "Campo atual",
                        if (state.magneticReady) f1(state.currentStrengthUt) + " µT" else "Aguardando…",
                    )
                    MetricRow(
                        "Referencial global X/Y/Z",
                        if (state.orientationReady && state.magneticReady) {
                            f1(state.worldXUt) + ", " +
                                f1(state.worldYUt) + ", " +
                                f1(state.worldZUt) + " µT"
                        } else {
                            "Aguardando rotation vector…"
                        },
                    )
                    MetricRow(
                        "Pontos",
                        state.cellsUt.count { it != null }.toString() + "/25",
                    )
                    if (state.minStrengthUt != null && state.maxStrengthUt != null) {
                        MetricRow(
                            "Faixa do mapa",
                            f1(state.minStrengthUt) + " – " +
                                f1(state.maxStrengthUt) + " µT",
                        )
                    }
                    state.strongestIndex?.let {
                        MetricRow(
                            "Maior leitura",
                            "linha " + (it / 5 + 1) +
                                ", coluna " + (it % 5 + 1),
                        )
                    }
                    state.lastError?.let { ErrorText(it) }
                }
            }
        }
        item {
            MagneticGrid(state.cellsUt, state.nextIndex)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onCapture,
                    enabled = state.magneticReady && state.nextIndex in 0..24,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (state.nextIndex in 0..24) {
                            "Capturar próximo"
                        } else {
                            "Mapa completo"
                        },
                    )
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Limpar")
                }
            }
        }
        item {
            Hint(
                "O mapa mostra intensidade magnética relativa. Uma anomalia indica mudança de campo, mas não identifica sozinha a causa.",
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun SensorQualificationScreen(
    modifier: Modifier,
    sensors: List<SensorInfo>,
    state: SensorQualificationState,
    onSelect: (SensorInfo) -> Unit,
    onStop: () -> Unit,
) {
    val vendorSensors = sensors.filter { it.type >= 65_536 }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LabHeader(
                eyebrow = "RAW SENSOR LAB",
                title = "Sensor Qualification",
                subtitle = "Teste cada sensor vendor isoladamente sem presumir o significado do payload.",
            )
        }
        if (state.sensorType != null) {
            item {
                StreamMetricsCard(
                    title = state.sensorName,
                    metrics = state.metrics,
                    magnitudeUnit = "unidade do sensor",
                )
            }
            item {
                if (state.stringType.isNotBlank()) {
                    Hint(state.stringType)
                }
                state.lastError?.let { ErrorText(it) }
            }
            item {
                OutlinedButton(
                    onClick = onStop,
                    enabled = state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Parar sensor atual")
                }
            }
        }
        item {
            Text(
                "Sensores vendor detectados (" +
                    vendorSensors.size.toString() + ")",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        vendorSensors.forEach { sensor ->
            item {
                OutlinedButton(
                    onClick = { onSelect(sensor) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(sensor.name, fontWeight = FontWeight.Bold)
                        Text(
                            sensor.type.toString() + " · " +
                                sensor.stringType.ifBlank { "sem stringType" } +
                                " · minDelay " +
                                sensor.minDelayUs.toString() + " µs",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MagneticGrid(
    cells: List<Float?>,
    nextIndex: Int,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(5) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(5) { column ->
                        val index = row * 5 + column
                        val value = cells.getOrNull(index)
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            tonalElevation = if (index == nextIndex) 6.dp else 1.dp,
                        ) {
                            Column(
                                Modifier.padding(
                                    vertical = 10.dp,
                                    horizontal = 4.dp,
                                ),
                            ) {
                                Text(
                                    (row + 1).toString() + "," +
                                        (column + 1).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    value?.let { f0(it) } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamMetricsCard(
    title: String,
    metrics: SensorStreamMetrics,
    magnitudeUnit: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            MetricRow("Sensor", metrics.name)
            MetricRow(
                "Status",
                when {
                    !metrics.available -> "indisponível"
                    !metrics.registered -> "detectado, não registrou"
                    !metrics.sampleReady -> "registrado, aguardando eventos"
                    else -> "streaming"
                },
            )
            MetricRow(
                "minDelay declarado",
                metrics.declaredMinDelayUs?.let {
                    it.toString() + " µs"
                } ?: "N/D",
            )
            MetricRow("Eventos", metrics.eventCount.toString())
            MetricRow(
                "Hz observado",
                if (metrics.sampleReady) f1(metrics.observedHz) else "—",
            )
            MetricRow(
                "Jitter",
                if (metrics.sampleReady) {
                    f2(metrics.jitterMs) + " ms"
                } else {
                    "—"
                },
            )
            MetricRow("Dimensões", metrics.vectorSize.toString())
            MetricRow(
                "RMS magnitude",
                if (metrics.sampleReady) {
                    f3(metrics.rmsMagnitude) + " " + magnitudeUnit
                } else {
                    "—"
                },
            )
            MetricRow(
                "Pico",
                if (metrics.sampleReady) {
                    f3(metrics.peakMagnitude) + " " + magnitudeUnit
                } else {
                    "—"
                },
            )
            if (metrics.lastValues.isNotEmpty()) {
                MetricRow(
                    "Último payload",
                    metrics.lastValues.joinToString(
                        prefix = "[",
                        postfix = "]",
                    ) { f3(it) },
                )
            }
        }
    }
}

@Composable
private fun LabHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun MetricRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.46f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(0.54f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun f0(value: Float): String =
    String.format(Locale.US, "%.0f", value)

private fun f1(value: Float): String =
    String.format(Locale.US, "%.1f", value)

private fun f2(value: Float): String =
    String.format(Locale.US, "%.2f", value)

private fun f3(value: Float): String =
    String.format(Locale.US, "%.3f", value)
