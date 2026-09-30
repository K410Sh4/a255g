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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k410sh4.a25lab.model.DeviceSnapshot
import com.k410sh4.a25lab.util.formatBytes
import com.k410sh4.a25lab.util.thermalStatusName
import java.util.Locale

@Composable
fun PremiumDashboardScreen(
    modifier: Modifier,
    device: DeviceSnapshot?,
    copyStatus: String,
    refreshRunning: Boolean,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "A25 Lab",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "O seu Galaxy A25 como laboratório de percepção, conectividade e diagnóstico.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        device?.let { "${it.manufacturer} ${it.model}" } ?: "Galaxy A25",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        device?.let { "${it.socManufacturer} ${it.socModel} · Android ${it.androidRelease}" }
                            ?: "Carregando hardware…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryTile(
                            title = "Sensores",
                            value = device?.sensorCount?.toString() ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryTile(
                            title = "Câmeras",
                            value = device?.cameraCount?.toString() ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryTile(
                            title = "Tela",
                            value = device?.let { String.format(Locale.US, "%.0f Hz", it.refreshRateHz) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryTile(
                            title = "RAM",
                            value = device?.let { formatBytes(it.totalMemoryBytes) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryTile(
                            title = "Térmico",
                            value = device?.let { thermalStatusName(it.thermalStatus) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryTile(
                            title = "Bateria",
                            value = device?.batteryLevelPercent?.let { "$it%" } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            SectionTitle(
                title = "Explorar",
                subtitle = "Tudo organizado por tipo de capacidade.",
            )
        }

        item {
            CategoryCard(
                icon = "◈",
                title = "Percepção",
                description = "Movimento 3D, magnetismo, luz e áudio.",
                accent = CategoryAccent.Primary,
                onClick = { onNavigate(Screen.Perception) },
            )
        }
        item {
            CategoryCard(
                icon = "⌁",
                title = "Conectividade",
                description = "Bluetooth LE, GNSS, NFC e rede.",
                accent = CategoryAccent.Secondary,
                onClick = { onNavigate(Screen.Connectivity) },
            )
        }
        item {
            CategoryCard(
                icon = "⌘",
                title = "Laboratório",
                description = "Sensores brutos, Camera2, compute/IA e diagnóstico interno.",
                accent = CategoryAccent.Tertiary,
                onClick = { onNavigate(Screen.Lab) },
            )
        }

        item {
            SectionTitle(
                title = "Inventário",
                subtitle = "Relatório técnico completo do aparelho.",
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Snapshot interno",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        copyStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRefresh,
                            enabled = !refreshRunning,
                        ) {
                            Text(if (refreshRunning) "Atualizando…" else "Atualizar")
                        }
                        OutlinedButton(
                            onClick = onCopy,
                            enabled = device != null && !refreshRunning,
                        ) {
                            Text("Copiar")
                        }
                    }
                    OutlinedButton(
                        onClick = onShare,
                        enabled = device != null && !refreshRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Compartilhar inventário")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun PerceptionHubScreen(
    modifier: Modifier,
    onNavigate: (Screen) -> Unit,
) {
    CategoryHub(
        modifier = modifier,
        eyebrow = "PERCEPÇÃO",
        title = "Perceba além dos sentidos",
        subtitle = "Visualizações e análises usando sensores de movimento, ambiente e microfone do A25.",
        cards = listOf(
            HubItem("◎", "Movimento & 3D", "Pose 3D, aceleração, rotação, AOIS e VDIS.", Screen.Superpowers),
            HubItem("◇", "Ambiente", "Campo magnético 3D, luz e canal vendor light_cct.", Screen.Environment),
            HubItem("≈", "Áudio", "RMS, frequência dominante e FFT local.", Screen.Audio),
        ),
        onNavigate = onNavigate,
    )
}

@Composable
fun ConnectivityHubScreen(
    modifier: Modifier,
    onNavigate: (Screen) -> Unit,
) {
    CategoryHub(
        modifier = modifier,
        eyebrow = "CONECTIVIDADE",
        title = "Veja o que está ao redor",
        subtitle = "Rádio, posicionamento e interfaces de proximidade.",
        cards = listOf(
            HubItem("⌁", "Radar BLE", "Dispositivos próximos, RSSI e conectabilidade.", Screen.Bluetooth),
            HubItem("⌖", "GNSS", "Satélites, constelações e medições brutas.", Screen.Gnss),
            HubItem("N", "NFC", "Reader mode, tecnologias e NDEF.", Screen.Nfc),
            HubItem("↗", "Rede", "Transporte ativo, validação e link declarado.", Screen.Network),
        ),
        onNavigate = onNavigate,
    )
}

@Composable
fun LabHubScreen(
    modifier: Modifier,
    onNavigate: (Screen) -> Unit,
) {
    CategoryHub(
        modifier = modifier,
        eyebrow = "LABORATÓRIO",
        title = "Diagnóstico profundo",
        subtitle = "Ferramentas técnicas para investigar o hardware exposto.",
        cards = listOf(
            HubItem("∿", "Sensores", "Inventário de sensores e leitura ao vivo.", Screen.Sensors),
            HubItem("▦", "Camera2", "Hardware level, OIS, RAW e controles.", Screen.Cameras),
            HubItem("Σ", "Compute / IA", "Baseline CPU e preparação para backends de ML.", Screen.Compute),
        ),
        onNavigate = onNavigate,
    )
}

private data class HubItem(
    val icon: String,
    val title: String,
    val description: String,
    val screen: Screen,
)

private enum class CategoryAccent { Primary, Secondary, Tertiary }

@Composable
private fun CategoryHub(
    modifier: Modifier,
    eyebrow: String,
    title: String,
    subtitle: String,
    cards: List<HubItem>,
    onNavigate: (Screen) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
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
        cards.forEachIndexed { index, hub ->
            item {
                CategoryCard(
                    icon = hub.icon,
                    title = hub.title,
                    description = hub.description,
                    accent = when (index % 3) {
                        0 -> CategoryAccent.Primary
                        1 -> CategoryAccent.Secondary
                        else -> CategoryAccent.Tertiary
                    },
                    onClick = { onNavigate(hub.screen) },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CategoryCard(
    icon: String,
    title: String,
    description: String,
    accent: CategoryAccent,
    onClick: () -> Unit,
) {
    val container = when (accent) {
        CategoryAccent.Primary -> MaterialTheme.colorScheme.primaryContainer
        CategoryAccent.Secondary -> MaterialTheme.colorScheme.secondaryContainer
        CategoryAccent.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    icon,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Abrir  →",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
