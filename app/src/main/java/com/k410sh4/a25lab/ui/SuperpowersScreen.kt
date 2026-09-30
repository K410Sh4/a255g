package com.k410sh4.a25lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k410sh4.a25lab.model.AccelerationSource
import com.k410sh4.a25lab.model.SuperpowerSensorState
import com.k410sh4.a25lab.util.Quaternion
import com.k410sh4.a25lab.util.QuaternionMath
import com.k410sh4.a25lab.util.SuperpowerMath
import java.util.Locale
import kotlin.math.ln

@Composable
fun Movement3DScreen(
    modifier: Modifier,
    sensors: SuperpowerSensorState,
) {
    val current = Quaternion(
        w = sensors.quaternionW,
        x = sensors.quaternionX,
        y = sensors.quaternionY,
        z = sensors.quaternionZ,
    )
    var reference by remember { mutableStateOf<Quaternion?>(null) }
    var showTechnical by remember { mutableStateOf(false) }

    LaunchedEffect(
        sensors.orientationSampleReady,
        sensors.quaternionW,
        sensors.quaternionX,
        sensors.quaternionY,
        sensors.quaternionZ,
    ) {
        if (!sensors.orientationSampleReady) {
            reference = null
        } else if (reference == null) {
            reference = QuaternionMath.normalize(current)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeaderBlock(
                eyebrow = "MOVIMENTO & 3D",
                title = "Seu A25 no espaço",
                subtitle = "A pose usa quaternion para evitar as distorções do cubo Euler da versão anterior.",
            )
        }

        item {
            PremiumPanel(
                title = "Pose 3D",
                subtitle = "A representação é recentrada para a posição em que você abriu esta tela.",
            ) {
                if (sensors.orientationSampleReady) {
                    PhonePose3D(
                        current = current,
                        reference = reference ?: current,
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "Aguardando Rotation Vector…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "O modelo 3D só aparece após a primeira amostra real de orientação.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricBlock("Yaw", format(sensors.yawDeg, "°"), Modifier.weight(1f))
                    MetricBlock("Pitch", format(sensors.pitchDeg, "°"), Modifier.weight(1f))
                    MetricBlock("Roll", format(sensors.rollDeg, "°"), Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = { reference = QuaternionMath.normalize(current) },
                    enabled = sensors.orientationSampleReady,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Centralizar posição atual")
                }
            }
        }

        item {
            PremiumPanel(
                title = "Movimento",
                subtitle = "Resumo humano da aceleração e rotação detectadas.",
            ) {
                Text(
                    when {
                        sensors.accelerationSource == AccelerationSource.UNAVAILABLE ->
                            "Aceleração indisponível"
                        !sensors.accelerationSampleReady ||
                            !sensors.angularSampleReady ->
                            "Aguardando sensores…"
                        else ->
                            SuperpowerMath.motionLevel(
                                sensors.dynamicAccelerationMs2,
                                sensors.angularSpeedRadS,
                            )
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricBlock(
                        "Aceleração dinâmica",
                        when {
                            sensors.accelerationSource == AccelerationSource.UNAVAILABLE ->
                                "N/D"
                            !sensors.accelerationSampleReady ->
                                "Aguardando…"
                            else ->
                                format(sensors.dynamicAccelerationMs2, "m/s²")
                        },
                        Modifier.weight(1f),
                    )
                    MetricBlock(
                        "Velocidade angular",
                        when {
                            !sensors.angularStreamActive -> "Indisponível"
                            !sensors.angularSampleReady -> "Aguardando…"
                            else -> format(sensors.angularSpeedRadS, "rad/s")
                        },
                        Modifier.weight(1f),
                    )
                }

                TextButton(onClick = { showTechnical = !showTechnical }) {
                    Text(if (showTechnical) "Ocultar detalhes técnicos" else "Ver detalhes técnicos")
                }

                if (showTechnical) {
                    HorizontalDivider()
                    TechnicalFact(
                        "Rotation Vector",
                        if (sensors.orientationAvailable) "Ativo" else "Indisponível",
                    )
                    TechnicalFact(
                        "Giroscópio",
                        when {
                            !sensors.angularStreamActive -> "Indisponível"
                            !sensors.angularSampleReady -> "Ativo · aguardando amostra"
                            else -> "Ativo · recebendo amostras"
                        },
                    )
                    TechnicalFact(
                        "Fonte da aceleração",
                        when (sensors.accelerationSource) {
                            AccelerationSource.LINEAR_SENSOR ->
                                "TYPE_LINEAR_ACCELERATION"
                            AccelerationSource.ACCELEROMETER_FALLBACK ->
                                "Fallback |a| - g"
                            AccelerationSource.UNAVAILABLE ->
                                "Indisponível"
                        },
                    )
                    TechnicalFact(
                        "AOIS",
                        if (sensors.aoisAvailable) {
                            "Detectado · HAL min ${sensors.aoisMinDelayUs ?: 0} µs"
                        } else {
                            "Não exposto"
                        },
                    )
                    TechnicalFact(
                        "VDIS",
                        if (sensors.vdisAvailable) {
                            "Detectado · HAL min ${sensors.vdisMinDelayUs ?: 0} µs"
                        } else {
                            "Não exposto"
                        },
                    )
                    Text(
                        "AOIS e VDIS continuam apenas como capacidades detectadas até medirmos taxa efetiva e jitter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            InfoBanner(
                title = "Por que ficou mais estável?",
                body = "O objeto 3D agora é rotacionado por quaternion e pode ser recentrado. " +
                    "Isso reduz gimbal lock e evita que yaw, pitch e roll sejam aplicados numa ordem visual inadequada.",
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun EnvironmentScreen(
    modifier: Modifier,
    sensors: SuperpowerSensorState,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeaderBlock(
                eyebrow = "AMBIENTE",
                title = "Veja o invisível",
                subtitle = "Campo magnético e iluminação transformados em visualizações fáceis de entender.",
            )
        }

        item {
            PremiumPanel(
                title = "Campo magnético 3D",
                subtitle = "A esfera mostra direção e intensidade relativa do vetor medido.",
            ) {
                if (sensors.magneticSampleReady) {
                    MagneticField3D(
                        x = sensors.magneticXUt,
                        y = sensors.magneticYUt,
                        z = sensors.magneticZUt,
                        magnitude = sensors.magneticStrengthUt,
                    )

                    Text(
                        format(sensors.magneticStrengthUt, "µT"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "X ${f(sensors.magneticXUt)} · Y ${f(sensors.magneticYUt)} · Z ${f(sensors.magneticZUt)} µT",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                if (sensors.magneticStreamActive) {
                                    "Aguardando magnetômetro…"
                                } else {
                                    "Magnetômetro indisponível"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Zero ainda não é tratado como uma medição real.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                TechnicalFact(
                    "Calibração",
                    SuperpowerMath.sensorAccuracyName(sensors.magneticAccuracy),
                )
                Text(
                    "A seta usa o referencial do próprio aparelho. Use para observar variações; " +
                        "ela não identifica sozinha a origem nem o material.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            PremiumPanel(
                title = "Luz ambiente",
                subtitle = "Leitura do sensor principal e do canal Samsung CCT quando disponível.",
            ) {
                Text(
                    when {
                        !sensors.lightStreamActive -> "Sensor de luz indisponível"
                        sensors.lightLux == null -> "Aguardando leitura…"
                        else -> format(sensors.lightLux, "lux")
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                LightLevelBar(sensors.lightLux ?: 0f)

                TechnicalFact(
                    "Canal vendor light_cct",
                    when {
                        !sensors.cctSensorAvailable -> "Não exposto"
                        !sensors.cctStreamActive -> "Detectado, mas sem stream"
                        sensors.cctRawValues.isNotEmpty() ->
                            sensors.cctRawValues
                                .mapIndexed { index, value ->
                                    "v$index=${f(value)}"
                                }
                                .joinToString(" · ")
                        else -> "Ativo, aguardando evento"
                    },
                )
                Text(
                    "Nenhuma posição do vetor é chamada de temperatura de cor até validarmos " +
                        "a semântica do sensor vendor Samsung no aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HeaderBlock(
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
private fun PremiumPanel(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
private fun MetricBlock(
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
            Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
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
private fun TechnicalFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InfoBanner(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LightLevelBar(lux: Float) {
    val safeLux = lux.coerceAtLeast(0f)
    val fraction = (
        ln(1.0 + safeLux.toDouble()) /
            ln(1.0 + 60_000.0)
        ).toFloat().coerceIn(0f, 1f)
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.large,
            ),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(14.dp)
                .background(
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.shapes.large,
                ),
        )
    }
}

private fun f(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun format(value: Float, unit: String): String =
    String.format(Locale.US, "%.2f %s", value, unit)
