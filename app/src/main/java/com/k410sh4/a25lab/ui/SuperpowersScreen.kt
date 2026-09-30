package com.k410sh4.a25lab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k410sh4.a25lab.model.AudioState
import com.k410sh4.a25lab.model.BleState
import com.k410sh4.a25lab.model.SuperpowerSensorState
import com.k410sh4.a25lab.util.SuperpowerMath
import java.util.Locale

@Composable
fun SuperpowersScreen(
    modifier: Modifier,
    sensors: SuperpowerSensorState,
    ble: BleState,
    audio: AudioState,
    onStartBle: () -> Unit,
    onStopBle: () -> Unit,
    onStartAudio: () -> Unit,
    onStopAudio: () -> Unit,
) {
    var showTechnical by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Superpoderes",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Visualizações em tempo real usando os sensores reais do seu A25.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            FriendlyCard(
                title = "🧭 Orientação 3D",
                subtitle = "Veja o celular girar junto com o aparelho.",
            ) {
                OrientationCube3D(
                    yawDeg = sensors.yawDeg,
                    pitchDeg = sensors.pitchDeg,
                    rollDeg = sensors.rollDeg,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CompactMetric("Yaw", format(sensors.yawDeg, "°"), Modifier.weight(1f))
                    CompactMetric("Pitch", format(sensors.pitchDeg, "°"), Modifier.weight(1f))
                    CompactMetric("Roll", format(sensors.rollDeg, "°"), Modifier.weight(1f))
                }
                StatusLine(
                    if (sensors.orientationAvailable) {
                        "Rotation Vector ativo"
                    } else {
                        "Rotation Vector não disponível"
                    },
                )
            }
        }

        item {
            FriendlyCard(
                title = "🧲 Visão Magnética 3D",
                subtitle = "A seta mostra a direção do campo magnético medido.",
            ) {
                MagneticVector3D(
                    x = sensors.magneticXUt,
                    y = sensors.magneticYUt,
                    z = sensors.magneticZUt,
                )
                HeroMetric(
                    label = "Força do campo",
                    value = format(sensors.magneticStrengthUt, "µT"),
                )
                Text(
                    "X ${f(sensors.magneticXUt)}  •  Y ${f(sensors.magneticYUt)}  •  Z ${f(sensors.magneticZUt)} µT",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            FriendlyCard(
                title = "⚡ Movimento",
                subtitle = "Percebe aceleração e rotação do aparelho.",
            ) {
                HeroMetric(
                    label = "Estado",
                    value = SuperpowerMath.motionLevel(
                        sensors.dynamicAccelerationMs2,
                        sensors.angularSpeedRadS,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CompactMetric(
                        "Aceleração",
                        format(sensors.dynamicAccelerationMs2, "m/s²"),
                        Modifier.weight(1f),
                    )
                    CompactMetric(
                        "Rotação",
                        format(sensors.angularSpeedRadS, "rad/s"),
                        Modifier.weight(1f),
                    )
                }

                TextButton(onClick = { showTechnical = !showTechnical }) {
                    Text(if (showTechnical) "Ocultar detalhes técnicos" else "Ver detalhes técnicos")
                }

                if (showTechnical) {
                    HorizontalDivider()
                    TechnicalRow(
                        "AOIS",
                        if (sensors.aoisAvailable) {
                            "Detectado · min ${sensors.aoisMinDelayUs ?: 0} µs"
                        } else {
                            "Não exposto"
                        },
                    )
                    TechnicalRow(
                        "VDIS",
                        if (sensors.vdisAvailable) {
                            "Detectado · min ${sensors.vdisMinDelayUs ?: 0} µs"
                        } else {
                            "Não exposto"
                        },
                    )
                    Text(
                        "O valor de min delay vem da HAL. A taxa real ainda precisa ser medida no aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            FriendlyCard(
                title = "💡 Luz",
                subtitle = "Mede a iluminação do ambiente em tempo real.",
            ) {
                HeroMetric(
                    label = "Iluminação",
                    value = sensors.lightLux?.let { format(it, "lux") } ?: "Aguardando…",
                )
                TechnicalRow(
                    "Canal Samsung CCT",
                    when {
                        !sensors.cctSensorAvailable -> "Não exposto"
                        !sensors.cctStreamActive -> "Detectado, mas sem stream"
                        sensors.cctRaw != null -> f(sensors.cctRaw)
                        else -> "Aguardando evento"
                    },
                )
                Text(
                    "O CCT Samsung ainda é exibido como valor bruto até validarmos sua unidade real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            FriendlyCard(
                title = "📡 Radar BLE",
                subtitle = "Mostra dispositivos Bluetooth próximos e a força relativa do sinal.",
            ) {
                HeroMetric(
                    label = "Dispositivos vistos",
                    value = ble.devices.size.toString(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onStartBle,
                        enabled = !ble.scanning,
                    ) {
                        Text(if (ble.scanning) "Escaneando…" else "Iniciar radar")
                    }
                    OutlinedButton(
                        onClick = onStopBle,
                        enabled = ble.scanning,
                    ) {
                        Text("Parar")
                    }
                }
                ble.lastError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        items(ble.devices.take(6), key = { "power-${it.key}" }) { device ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        device.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${device.rssi} dBm · ${SuperpowerMath.rssiBand(device.rssi)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            FriendlyCard(
                title = "🎧 Ouvido Espectral",
                subtitle = "Analisa o som localmente sem enviar áudio para a internet.",
            ) {
                HeroMetric(
                    label = "Frequência dominante",
                    value = String.format(
                        Locale.US,
                        "%.1f Hz",
                        audio.dominantFrequencyHz,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CompactMetric(
                        "Nível",
                        String.format(Locale.US, "%.1f dBFS", audio.rmsDbFs),
                        Modifier.weight(1f),
                    )
                    CompactMetric(
                        "Sample rate",
                        "${audio.sampleRateHz} Hz",
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onStartAudio,
                        enabled = !audio.running,
                    ) {
                        Text(if (audio.running) "Analisando…" else "Iniciar análise")
                    }
                    OutlinedButton(
                        onClick = onStopAudio,
                        enabled = audio.running,
                    ) {
                        Text("Parar")
                    }
                }
                audio.lastError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendlyCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            content()
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
private fun StatusLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TechnicalRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
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

private fun f(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun format(value: Float, unit: String): String =
    String.format(Locale.US, "%.2f %s", value, unit)
