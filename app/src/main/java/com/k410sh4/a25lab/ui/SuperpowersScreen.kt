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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Superpoderes do A25",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Seis modos que transformam sensores reais do aparelho em percepção ampliada. " +
                    "Os valores são medições do Android; interpretações como força de sinal BLE são relativas.",
            )
        }

        item {
            PowerCard("🧲 Visão Magnética") {
                PowerValue("Campo total", format(sensors.magneticStrengthUt, "µT"))
                PowerValue(
                    "XYZ",
                    "${f(sensors.magneticXUt)}, ${f(sensors.magneticYUt)}, ${f(sensors.magneticZUt)} µT",
                )
                Text(
                    "Útil para visualizar variações de campo magnético e perturbações próximas. " +
                        "Não substitui um instrumento calibrado nem identifica materiais por si só.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            PowerCard("⚡ Detector de Movimento") {
                PowerValue(
                    "Aceleração dinâmica",
                    format(sensors.dynamicAccelerationMs2, "m/s²"),
                )
                PowerValue(
                    "Velocidade angular",
                    format(sensors.angularSpeedRadS, "rad/s"),
                )
                PowerValue(
                    "Estado relativo",
                    SuperpowerMath.motionLevel(
                        sensors.dynamicAccelerationMs2,
                        sensors.angularSpeedRadS,
                    ),
                )
                PowerValue(
                    "AOIS",
                    if (sensors.aoisAvailable) {
                        "detectado · min ${sensors.aoisMinDelayUs ?: 0} µs"
                    } else {
                        "não exposto"
                    },
                )
                PowerValue(
                    "VDIS",
                    if (sensors.vdisAvailable) {
                        "detectado · min ${sensors.vdisMinDelayUs ?: 0} µs"
                    } else {
                        "não exposto"
                    },
                )
                Text(
                    "A leitura ao vivo usa os sensores Android padrão. AOIS/VDIS são apenas sinalizados aqui " +
                        "até terem taxa efetiva e semântica validadas em dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            PowerCard("💡 Visão de Luz") {
                PowerValue(
                    "Luz ambiente",
                    sensors.lightLux?.let { format(it, "lux") } ?: "aguardando sensor",
                )
                PowerValue(
                    "Canal CCT Samsung",
                    if (!sensors.cctSensorAvailable) {
                        "não exposto"
                    } else if (!sensors.cctStreamActive) {
                        "exposto, stream não iniciou"
                    } else {
                        sensors.cctRaw?.let { f(it) } ?: "ativo, aguardando evento"
                    },
                )
                Text(
                    "O valor CCT é mostrado como dado bruto do canal vendor Samsung; a unidade não é presumida " +
                        "sem documentação ou validação física.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            PowerCard("🧭 Orientação 3D") {
                PowerValue("Yaw", format(sensors.yawDeg, "°"))
                PowerValue("Pitch", format(sensors.pitchDeg, "°"))
                PowerValue("Roll", format(sensors.rollDeg, "°"))
                PowerValue(
                    "Rotation Vector",
                    if (sensors.orientationAvailable) "disponível" else "não disponível",
                )
                Text(
                    "A orientação usa o Rotation Vector fornecido pelo Android, que combina sensores do aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            PowerCard("📡 Radar BLE") {
                PowerValue("Estado", if (ble.scanning) "escaneando" else "parado")
                PowerValue("Dispositivos vistos", ble.devices.size.toString())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartBle, enabled = !ble.scanning) {
                        Text("Iniciar radar")
                    }
                    Button(onClick = onStopBle, enabled = ble.scanning) {
                        Text("Parar")
                    }
                }
                ble.lastError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "A faixa é baseada apenas em RSSI e serve como indicação relativa; paredes, corpo humano e " +
                        "potência do transmissor alteram o resultado.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        items(ble.devices.take(8), key = { "power-${it.key}" }) { device ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(device.name, fontWeight = FontWeight.Bold)
                    PowerValue("RSSI", "${device.rssi} dBm")
                    PowerValue("Sinal relativo", SuperpowerMath.rssiBand(device.rssi))
                    PowerValue("Endereço", device.address)
                }
            }
        }

        item {
            PowerCard("🎧 Ouvido Espectral") {
                PowerValue("Estado", if (audio.running) "capturando" else "parado")
                PowerValue("Nível RMS", String.format(Locale.US, "%.1f dBFS", audio.rmsDbFs))
                PowerValue(
                    "Frequência dominante",
                    String.format(Locale.US, "%.1f Hz", audio.dominantFrequencyHz),
                )
                PowerValue("Sample rate", "${audio.sampleRateHz} Hz")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartAudio, enabled = !audio.running) {
                        Text("Ouvir espectro")
                    }
                    Button(onClick = onStopAudio, enabled = audio.running) {
                        Text("Parar")
                    }
                }
                audio.lastError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Processamento PCM + FFT ocorre localmente no aparelho. O app não possui permissão INTERNET.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PowerCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
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
private fun PowerValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun f(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun format(value: Float, unit: String): String =
    String.format(Locale.US, "%.2f %s", value, unit)
