package com.k410sh4.a25lab.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.model.AudioState
import com.k410sh4.a25lab.model.BleState
import com.k410sh4.a25lab.model.CameraInfo
import com.k410sh4.a25lab.model.DeviceSnapshot
import com.k410sh4.a25lab.model.GnssState
import com.k410sh4.a25lab.model.MotionSample
import com.k410sh4.a25lab.model.NetworkState
import com.k410sh4.a25lab.model.NfcState
import com.k410sh4.a25lab.model.SensorInfo
import com.k410sh4.a25lab.util.formatBytes
import com.k410sh4.a25lab.ui.theme.A25LabTheme
import com.k410sh4.a25lab.util.thermalStatusName
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A25LabApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) pendingPermissionAction?.invoke()
        pendingPermissionAction = null
    }

    fun runWithPermissions(permissions: Array<String>, action: () -> Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(permissions)
        }
    }

    A25LabTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val rootScreens = setOf(
                Screen.Dashboard,
                Screen.Perception,
                Screen.Connectivity,
                Screen.Lab,
            )
            val parent = parentScreen(viewModel.screen)

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            if (viewModel.screen !in rootScreens) {
                                Text(viewModel.screen.title)
                            }
                        },
                        navigationIcon = {
                            if (viewModel.screen !in rootScreens) {
                                TextButton(onClick = { viewModel.navigate(parent) }) {
                                    Text("‹ Voltar")
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    PremiumBottomBar(
                        current = viewModel.screen,
                        onNavigate = viewModel::navigate,
                    )
                },
            ) { padding ->
                BackHandler(enabled = viewModel.screen !in rootScreens) {
                    viewModel.navigate(parent)
                }
                when (viewModel.screen) {
                    Screen.Dashboard -> PremiumDashboardScreen(
                        modifier = Modifier.padding(padding),
                        device = viewModel.device,
                        copyStatus = viewModel.copyStatus,
                        onRefresh = viewModel::refreshAllAndCopy,
                        onCopy = viewModel::copySpecificationsToClipboard,
                        onNavigate = viewModel::navigate,
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Relatório A25 Lab")
                                putExtra(Intent.EXTRA_TEXT, viewModel.report())
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartilhar relatório"))
                        },
                    )
                    Screen.Perception -> PerceptionHubScreen(
                        modifier = Modifier.padding(padding),
                        onNavigate = viewModel::navigate,
                    )
                    Screen.Connectivity -> ConnectivityHubScreen(
                        modifier = Modifier.padding(padding),
                        onNavigate = viewModel::navigate,
                    )
                    Screen.Lab -> LabHubScreen(
                        modifier = Modifier.padding(padding),
                        onNavigate = viewModel::navigate,
                    )
                    Screen.Superpowers -> Movement3DScreen(
                        modifier = Modifier.padding(padding),
                        sensors = viewModel.superpowers,
                    )
                    Screen.Environment -> EnvironmentScreen(
                        modifier = Modifier.padding(padding),
                        sensors = viewModel.superpowers,
                    )
                    Screen.Sensors -> SensorsScreen(
                        Modifier.padding(padding),
                        viewModel.sensors,
                        viewModel.motion,
                    )
                    Screen.Gnss -> GnssScreen(
                        Modifier.padding(padding),
                        viewModel.gnss,
                        onStart = {
                            runWithPermissions(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                                viewModel::startGnss,
                            )
                        },
                        onStop = viewModel::stopGnss,
                    )
                    Screen.Bluetooth -> BleScreen(
                        Modifier.padding(padding),
                        viewModel.ble,
                        onStart = {
                            runWithPermissions(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                ),
                                viewModel::startBle,
                            )
                        },
                        onStop = viewModel::stopBle,
                    )
                    Screen.Audio -> AudioScreen(
                        Modifier.padding(padding),
                        viewModel.audio,
                        onStart = {
                            runWithPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), viewModel::startAudio)
                        },
                        onStop = viewModel::stopAudio,
                    )
                    Screen.Cameras -> CamerasScreen(Modifier.padding(padding), viewModel.cameras)
                    Screen.Nfc -> NfcScreen(Modifier.padding(padding), viewModel.nfc)
                    Screen.Network -> NetworkScreen(
                        Modifier.padding(padding),
                        viewModel.network,
                        onRefresh = viewModel::refreshNetwork,
                    )
                    Screen.Compute -> ComputeScreen(
                        Modifier.padding(padding),
                        viewModel.computeRunning,
                        viewModel.computeResult?.elapsedMs,
                        viewModel.computeResult?.estimatedGflops,
                        onRun = viewModel::runCpuBaseline,
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumBottomBar(
    current: Screen,
    onNavigate: (Screen) -> Unit,
) {
    val selectedRoot = parentScreen(current).takeIf {
        it == Screen.Perception || it == Screen.Connectivity || it == Screen.Lab
    } ?: if (current == Screen.Dashboard) Screen.Dashboard else parentScreen(current)

    NavigationBar {
        listOf(
            Triple(Screen.Dashboard, "⌂", "Início"),
            Triple(Screen.Perception, "◈", "Percepção"),
            Triple(Screen.Connectivity, "⌁", "Conexões"),
            Triple(Screen.Lab, "⌘", "Lab"),
        ).forEach { (screen, icon, label) ->
            NavigationBarItem(
                selected = selectedRoot == screen,
                onClick = { onNavigate(screen) },
                icon = {
                    Text(
                        icon,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                label = { Text(label) },
            )
        }
    }
}

private fun parentScreen(screen: Screen): Screen = when (screen) {
    Screen.Superpowers,
    Screen.Environment,
    Screen.Audio,
    Screen.Cameras,
    -> Screen.Perception

    Screen.Gnss,
    Screen.Bluetooth,
    Screen.Nfc,
    Screen.Network,
    -> Screen.Connectivity

    Screen.Sensors,
    Screen.Compute,
    -> Screen.Lab

    Screen.Dashboard,
    Screen.Perception,
    Screen.Connectivity,
    Screen.Lab,
    -> screen
}

@Composable
private fun DashboardScreen(
    modifier: Modifier,
    device: DeviceSnapshot?,
    copyStatus: String,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onShare: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Mapa real do hardware exposto pelo Android",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "O app mede o que o firmware/HAL realmente disponibiliza; não presume que toda capacidade do Exynos esteja acessível.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (device == null) {
                        Text("Snapshot indisponível.")
                    } else {
                        Text("${device.manufacturer} ${device.model}", style = MaterialTheme.typography.titleLarge)
                        KeyValue("SoC", "${device.socManufacturer} ${device.socModel}")
                        KeyValue("Android", "${device.androidRelease} / API ${device.sdkInt}")
                        KeyValue("Patch", device.securityPatch)
                        KeyValue("CPU", "${device.cpuCores} cores lógicos")
                        KeyValue("RAM", "${formatBytes(device.totalMemoryBytes)} total")
                        KeyValue("Armazenamento", "${formatBytes(device.freeStorageBytes)} livres")
                        KeyValue("Tela", String.format(Locale.US, "%.1f Hz", device.refreshRateHz))
                        KeyValue("Térmico", thermalStatusName(device.thermalStatus))
                        KeyValue("Bateria", device.batteryTemperatureC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "N/D")
                        KeyValue("Sensores", device.sensorCount.toString())
                        KeyValue("Camera2 IDs", device.cameraCount.toString())
                    }
                    Text(
                        copyStatus,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Ao abrir ou atualizar, o inventário completo é copiado automaticamente para a área de transferência e salvo localmente no app.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRefresh) { Text("Atualizar + copiar") }
                        Button(onClick = onCopy) { Text("Copiar specs") }
                    }
                    Button(onClick = onShare) { Text("Compartilhar inventário") }
                }
            }
        }
        items(
            listOf(
                Triple(
                    Screen.Superpowers,
                    "Superpoderes",
                    "Visão magnética, detector de movimento, luz, orientação 3D, radar BLE e ouvido espectral.",
                ),
                Triple(Screen.Sensors, "Sensores + motion", "Acelerômetro, giroscópio, magnetômetro e inventário completo."),
                Triple(Screen.Gnss, "GNSS bruto", "Satélites por constelação e callback de medições GNSS brutas."),
                Triple(Screen.Bluetooth, "Bluetooth LE", "Scanner BLE com RSSI, nome, endereço e conectabilidade expostos."),
                Triple(Screen.Audio, "Audio Lab", "PCM 44,1 kHz, nível RMS dBFS e frequência dominante via FFT local."),
                Triple(Screen.Cameras, "Camera2 Probe", "RAW, controles manuais, OIS, hardware level e resoluções reais."),
                Triple(Screen.Nfc, "NFC Lab", "Reader mode, ID da tag, tecnologias e leitura NDEF Text."),
                Triple(Screen.Network, "Network Lab", "Transportes ativos, validação e largura de banda declarada."),
                Triple(Screen.Compute, "Compute / IA", "Baseline CPU verificável e preparação para benchmark LiteRT por backend."),
            ),
        ) { (screen, title, description) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate(screen) },
                colors = CardDefaults.cardColors(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SensorsScreen(modifier: Modifier, sensors: List<SensorInfo>, motion: MotionSample) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Leitura ao vivo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    KeyValue("Acelerômetro m/s²", "${f(motion.ax)}, ${f(motion.ay)}, ${f(motion.az)}")
                    KeyValue("Giroscópio rad/s", "${f(motion.gx)}, ${f(motion.gy)}, ${f(motion.gz)}")
                    KeyValue("Magnetômetro µT", "${f(motion.mx)}, ${f(motion.my)}, ${f(motion.mz)}")
                }
            }
        }
        item { Text("${sensors.size} sensores expostos", style = MaterialTheme.typography.titleMedium) }
        items(sensors) { sensor ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(sensor.typeName, fontWeight = FontWeight.Bold)
                    Text(sensor.name)
                    KeyValue("Fabricante", sensor.vendor)
                    KeyValue("Resolução", sensor.resolution.toString())
                    KeyValue("Faixa máxima", sensor.maxRange.toString())
                    KeyValue("Consumo declarado", "${sensor.powerMa} mA")
                    KeyValue("Delay mínimo", "${sensor.minDelayUs} µs")
                    KeyValue("FIFO máximo", sensor.fifoMaxEventCount.toString())
                    KeyValue("Wake-up", if (sensor.wakeUp) "sim" else "não")
                }
            }
        }
    }
}

@Composable
private fun GnssScreen(modifier: Modifier, state: GnssState, onStart: () -> Unit, onStop: () -> Unit) {
    StandardLabScreen(modifier, "GNSS") {
        KeyValue("Estado", if (state.running) "capturando" else "parado")
        KeyValue("Satélites visíveis", state.satellitesVisible.toString())
        KeyValue("Usados no fix", state.satellitesUsed.toString())
        KeyValue("Medições brutas no último evento", state.rawMeasurementCount.toString())
        KeyValue("Constelações", state.constellations.joinToString().ifBlank { "N/D" })
        state.lastError?.let { ErrorText(it) }
        ActionButtons(state.running, onStart, onStop)
        Text("Observação: pseudorange, ADR, AGC e múltiplas frequências dependem do chipset/firmware; a próxima versão pode exportar cada GnssMeasurement individualmente.")
    }
}

@Composable
private fun BleScreen(modifier: Modifier, state: BleState, onStart: () -> Unit, onStop: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyValue("Estado", if (state.scanning) "escaneando" else "parado")
                    KeyValue("Dispositivos", state.devices.size.toString())
                    state.lastError?.let { ErrorText(it) }
                    ActionButtons(state.scanning, onStart, onStop)
                }
            }
        }
        items(state.devices, key = { it.key }) { device ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(device.name, fontWeight = FontWeight.Bold)
                    KeyValue("Endereço", device.address)
                    KeyValue("RSSI", "${device.rssi} dBm")
                    KeyValue("Conectável", device.connectable?.let { if (it) "sim" else "não" } ?: "N/D")
                }
            }
        }
    }
}

@Composable
private fun AudioScreen(modifier: Modifier, state: AudioState, onStart: () -> Unit, onStop: () -> Unit) {
    StandardLabScreen(modifier, "Audio Lab") {
        KeyValue("Estado", if (state.running) "capturando" else "parado")
        KeyValue("Sample rate", "${state.sampleRateHz} Hz")
        KeyValue("RMS", String.format(Locale.US, "%.1f dBFS", state.rmsDbFs))
        KeyValue("Frequência dominante", String.format(Locale.US, "%.1f Hz", state.dominantFrequencyHz))
        state.lastError?.let { ErrorText(it) }
        ActionButtons(state.running, onStart, onStop)
        Text("O analisador usa PCM local e FFT de 2048 amostras. Não envia áudio para a rede.")
    }
}

@Composable
private fun CamerasScreen(modifier: Modifier, cameras: List<CameraInfo>) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("${cameras.size} câmeras expostas pela Camera2 HAL") }
        items(cameras, key = { it.id }) { camera ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("ID ${camera.id} — ${camera.facing}", fontWeight = FontWeight.Bold)
                    KeyValue("Hardware level", camera.hardwareLevel)
                    KeyValue("Pixel array", camera.pixelArray)
                    KeyValue("Maior JPEG", camera.maxJpeg)
                    KeyValue("RAW", yesNo(camera.rawSupported))
                    KeyValue("Manual sensor", yesNo(camera.manualSensor))
                    KeyValue("Manual pós", yesNo(camera.manualPostProcessing))
                    KeyValue("Logical multi-camera", yesNo(camera.logicalMultiCamera))
                    KeyValue("OIS modes", camera.oisModes.joinToString().ifBlank { "não exposto" })
                }
            }
        }
    }
}

@Composable
private fun NfcScreen(modifier: Modifier, state: NfcState) {
    StandardLabScreen(modifier, "NFC") {
        KeyValue("Hardware", if (state.available) "disponível" else "ausente")
        KeyValue("Estado", if (state.enabled) "ativado" else "desativado")
        KeyValue("Última tag", state.lastTagIdHex ?: "aproxime uma tag NFC")
        KeyValue("Tecnologias", state.technologies.joinToString().ifBlank { "N/D" })
        KeyValue("NDEF Text", state.ndefText ?: "nenhum texto NDEF lido")
        Text("O Reader Mode fica ativo enquanto o A25 Lab está em primeiro plano.")
    }
}

@Composable
private fun NetworkScreen(modifier: Modifier, state: NetworkState, onRefresh: () -> Unit) {
    StandardLabScreen(modifier, "Rede") {
        KeyValue("Internet declarada", yesNo(state.connected))
        KeyValue("Validada", yesNo(state.validated))
        KeyValue("Medida", yesNo(state.metered))
        KeyValue("Transportes", state.transports.joinToString().ifBlank { "N/D" })
        KeyValue("Downstream declarado", "${state.downstreamKbps} kbps")
        KeyValue("Upstream declarado", "${state.upstreamKbps} kbps")
        Button(onClick = onRefresh) { Text("Atualizar") }
        Text("Os valores de link são estimativas declaradas por NetworkCapabilities, não um speed test.")
    }
}

@Composable
private fun ComputeScreen(
    modifier: Modifier,
    running: Boolean,
    elapsedMs: Long?,
    gflops: Double?,
    onRun: () -> Unit,
) {
    StandardLabScreen(modifier, "Compute / IA") {
        Text("Baseline CPU", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        KeyValue("Matriz", "160×160 FP32")
        KeyValue("Tempo", elapsedMs?.let { "$it ms" } ?: "ainda não medido")
        KeyValue("Estimativa", gflops?.let { String.format(Locale.US, "%.3f GFLOP/s", it) } ?: "N/D")
        Button(onClick = onRun, enabled = !running) { Text(if (running) "Executando…" else "Executar baseline") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("NPU / LiteRT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "O Exynos 1280 possui aceleração de IA no SoC, mas uma API pública não prova que um delegate específico esteja usando a NPU. A validação correta é executar o mesmo modelo LiteRT em backends disponíveis e comparar latência, RAM, energia e temperatura no A25.",
        )
        Text("Esta versão não finge um resultado de NPU: o benchmark por modelo/backend fica explicitamente como validação em dispositivo.")
    }
}

@Composable
private fun StandardLabScreen(modifier: Modifier, title: String, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ActionButtons(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStart, enabled = !running) { Text("Iniciar") }
        Button(onClick = onStop, enabled = running) { Text("Parar") }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

private fun f(value: Float): String = String.format(Locale.US, "%.3f", value)
private fun yesNo(value: Boolean) = if (value) "sim" else "não"
