package com.k410sh4.a25lab.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import android.nfc.NfcManager
import android.nfc.Tag
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.k410sh4.a25lab.data.AudioAnalyzer
import com.k410sh4.a25lab.data.BleRepository
import com.k410sh4.a25lab.data.CameraProbe
import com.k410sh4.a25lab.data.ComputeBenchmark
import com.k410sh4.a25lab.data.GnssRepository
import com.k410sh4.a25lab.data.HardwareProbe
import com.k410sh4.a25lab.data.NetworkProbe
import com.k410sh4.a25lab.data.NfcParser
import com.k410sh4.a25lab.data.SensorRepository
import com.k410sh4.a25lab.model.AudioState
import com.k410sh4.a25lab.model.BleState
import com.k410sh4.a25lab.model.CameraInfo
import com.k410sh4.a25lab.model.DeviceSnapshot
import com.k410sh4.a25lab.model.GnssState
import com.k410sh4.a25lab.model.MotionSample
import com.k410sh4.a25lab.model.NetworkState
import com.k410sh4.a25lab.model.NfcState
import com.k410sh4.a25lab.model.SensorInfo
import com.k410sh4.a25lab.model.SuperpowerSensorState
import com.k410sh4.a25lab.model.SystemFeatureInfo
import com.k410sh4.a25lab.util.ReportFormatter
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val nfcExecutor = Executors.newSingleThreadExecutor()
    private val computeExecutor = Executors.newSingleThreadExecutor()

    private val sensorsRepository = SensorRepository(application.applicationContext)
    private val cameraProbe = CameraProbe(application.applicationContext)
    private val hardwareProbe = HardwareProbe(application.applicationContext)
    private val gnssRepository = GnssRepository(application.applicationContext)
    private val bleRepository = BleRepository(application.applicationContext)
    private val audioAnalyzer = AudioAnalyzer(application.applicationContext)
    private val networkProbe = NetworkProbe(application.applicationContext)
    private val computeBenchmark = ComputeBenchmark()

    private val clipboard = application.getSystemService(ClipboardManager::class.java)
    private val snapshotFile = File(
        application.noBackupFilesDir,
        "a25lab_last_internal_specs.txt",
    )
    private val reportGeneration = AtomicLong(0L)
    private val nfcReadGeneration = AtomicLong(0L)
    private val computeGeneration = AtomicLong(0L)

    @Volatile
    private var cleared = false

    private var inForeground = false
    private var gnssRequested = false
    private var bleRequested = false
    private var audioRequested = false
    private var pendingAutomaticReport: Pair<String, Boolean>? = null
    private var computeFuture: Future<*>? = null

    var screen by mutableStateOf(Screen.Dashboard)
        private set
    var device by mutableStateOf<DeviceSnapshot?>(null)
        private set
    var sensors by mutableStateOf<List<SensorInfo>>(emptyList())
        private set
    var systemFeatures by mutableStateOf<List<SystemFeatureInfo>>(emptyList())
        private set
    var motion by mutableStateOf(MotionSample())
        private set
    var superpowers by mutableStateOf(SuperpowerSensorState())
        private set
    var gnss by mutableStateOf(GnssState())
        private set
    var ble by mutableStateOf(BleState())
        private set
    var cameras by mutableStateOf<List<CameraInfo>>(emptyList())
        private set
    var cameraProbeErrors by mutableStateOf<List<String>>(emptyList())
        private set
    var inventoryWarnings by mutableStateOf<List<String>>(emptyList())
        private set
    var audio by mutableStateOf(AudioState())
        private set
    var network by mutableStateOf(NetworkState())
        private set
    var nfc by mutableStateOf(NfcState())
        private set
    var computeResult by mutableStateOf<ComputeBenchmark.Result?>(null)
        private set
    var computeRunning by mutableStateOf(false)
        private set
    var computeError by mutableStateOf<String?>(null)
        private set
    var refreshRunning by mutableStateOf(false)
        private set
    var copyStatus by mutableStateOf("Preparando inventário interno…")
        private set

    init {
        refreshAllAndCopy()
    }

    fun navigate(target: Screen) {
        if (screen == target) return

        val superpowerScreens = setOf(
            Screen.Superpowers,
            Screen.Environment,
        )
        if (screen in superpowerScreens && target in superpowerScreens) {
            screen = target
            return
        }

        val leavingNfc = screen == Screen.Nfc && target != Screen.Nfc
        val leavingBle =
            screen == Screen.Bluetooth && target != Screen.Bluetooth
        val leavingGnss =
            screen == Screen.Gnss && target != Screen.Gnss
        val leavingAudio =
            screen == Screen.Audio && target != Screen.Audio
        val leavingMotion =
            screen == Screen.Sensors && target != Screen.Sensors
        val leavingSuperpowers =
            (screen == Screen.Superpowers || screen == Screen.Environment) &&
                target != Screen.Superpowers &&
                target != Screen.Environment
        val leavingCompute =
            screen == Screen.Compute && target != Screen.Compute

        if (leavingCompute) {
            cancelCpuBaseline()
        }
        stopLiveModules(clearUserRequests = true)
        if (leavingNfc) {
            nfcReadGeneration.incrementAndGet()
            val current = currentNfcState()
            nfc = NfcState(
                available = current.available,
                enabled = current.enabled,
            )
        }
        if (leavingBle) {
            ble = BleState()
        }
        if (leavingGnss) {
            gnss = idleGnssState()
        }
        if (leavingAudio) {
            audio = AudioState()
        }
        if (leavingMotion) {
            motion = MotionSample()
        }
        if (leavingSuperpowers) {
            superpowers = SuperpowerSensorState()
        }
        screen = target

        if (target == Screen.Network) refreshNetwork()
        if (target == Screen.Nfc) refreshNfcState()
        if (inForeground) startAutomaticModulesForCurrentScreen()
    }

    fun onAppBackground() {
        inForeground = false
        nfcReadGeneration.incrementAndGet()
        cancelCpuBaseline()
        stopLiveModules(clearUserRequests = false)

        ble = BleState()
        gnss = idleGnssState()
        audio = AudioState()
        motion = MotionSample()
        superpowers = SuperpowerSensorState()
        nfc = NfcState(
            available = nfc.available,
            enabled = nfc.enabled,
        )
    }

    fun onAppForeground() {
        inForeground = true

        pendingAutomaticReport?.let { (text, fileSaved) ->
            pendingAutomaticReport = null
            copyReportToClipboardAndPublishStatus(
                text = text,
                fileSaved = fileSaved,
                automatic = true,
            )
        }

        startAutomaticModulesForCurrentScreen()

        when (screen) {
            Screen.Gnss -> if (gnssRequested) startGnssInternal()
            Screen.Bluetooth -> if (bleRequested) startBleInternal()
            Screen.Audio -> if (audioRequested) startAudioInternal()
            else -> Unit
        }
    }

    fun refreshAllAndCopy() {
        if (refreshRunning) return

        refreshRunning = true
        copyStatus = "Lendo capacidades do aparelho…"
        val nfcSnapshot = currentNfcState()

        ioExecutor.execute {
            val warnings = mutableListOf<String>()

            val sensorList = runCatching {
                sensorsRepository.listSensors()
            }.getOrElse { error ->
                warnings += probeFailure("Sensores", error)
                emptyList()
            }

            val cameraResult = runCatching {
                cameraProbe.probe()
            }.getOrElse { error ->
                warnings += probeFailure("Camera2", error)
                com.k410sh4.a25lab.model.CameraProbeResult(
                    errors = listOf("Falha global no Camera2 probe."),
                )
            }

            val features = runCatching {
                hardwareProbe.systemFeatures()
            }.getOrElse { error ->
                warnings += probeFailure("System features", error)
                emptyList()
            }

            val networkSnapshot = runCatching {
                networkProbe.snapshot()
            }.getOrElse { error ->
                val warning = probeFailure("Rede", error)
                warnings += warning
                NetworkState(lastError = warning)
            }

            val deviceSnapshot = runCatching {
                hardwareProbe.snapshot(
                    cameraCount = cameraResult.totalIds,
                    sensorCount = sensorList.size,
                    featureCount = features.size,
                )
            }.getOrElse { error ->
                warnings += probeFailure("Dispositivo/build", error)
                null
            }

            val text = ReportFormatter.build(
                snapshot = deviceSnapshot,
                sensors = sensorList,
                cameras = cameraResult.cameras,
                cameraProbeErrors = cameraResult.errors,
                probeWarnings = warnings,
                systemFeatures = features,
                network = networkSnapshot,
                nfc = nfcSnapshot,
            )

            val fileResult = runCatching {
                if (deviceSnapshot != null) snapshotFile.writeText(text)
            }

            post {
                sensors = sensorList
                cameras = cameraResult.cameras
                cameraProbeErrors = cameraResult.errors
                inventoryWarnings = warnings.toList()
                systemFeatures = features
                network = networkSnapshot
                nfc = nfcSnapshot
                device = deviceSnapshot
                refreshRunning = false

                if (deviceSnapshot == null) {
                    copyStatus = "Snapshot indisponível; consulte os detalhes do laboratório."
                } else if (inForeground) {
                    copyReportToClipboardAndPublishStatus(
                        text = text,
                        fileSaved = fileResult.isSuccess,
                        automatic = true,
                    )
                } else {
                    pendingAutomaticReport =
                        text to fileResult.isSuccess
                    copyStatus =
                        "Inventário salvo; a cópia automática aguardará o retorno ao app."
                }
            }
        }
    }

    fun refreshStaticProbe() = refreshAllAndCopy()

    fun refreshNetwork() {
        network = runCatching {
            networkProbe.snapshot()
        }.getOrElse { error ->
            NetworkState(
                lastError = probeFailure("Rede", error),
            )
        }
    }

    fun copySpecificationsToClipboard() {
        if (device == null) {
            copyStatus = "Não foi possível copiar: snapshot indisponível."
            return
        }

        val text = report()
        val generation = reportGeneration.incrementAndGet()
        val clipboardResult = runCatching {
            clipboard.setPrimaryClip(reportClip(text))
        }

        copyStatus = if (clipboardResult.isSuccess) {
            "Relatório copiado; atualizando snapshot privado…"
        } else {
            "Falha ao copiar para a área de transferência."
        }

        ioExecutor.execute {
            val fileSaved = runCatching {
                snapshotFile.writeText(text)
            }.isSuccess

            post {
                if (reportGeneration.get() != generation) return@post
                copyStatus = when {
                    clipboardResult.isSuccess && fileSaved ->
                        "Relatório copiado e snapshot privado atualizado."
                    clipboardResult.isSuccess ->
                        "Relatório copiado; falha ao atualizar o snapshot privado."
                    fileSaved ->
                        "Falha no clipboard; snapshot privado foi atualizado."
                    else ->
                        "Falha ao copiar e ao salvar o snapshot privado."
                }
            }
        }
    }

    fun startMotion() {
        if (!inForeground) return
        motion = MotionSample()
        sensorsRepository.startMotion { sample ->
            post { motion = sample }
        }
    }

    fun startSuperpowers() {
        if (!inForeground) return
        superpowers = SuperpowerSensorState()
        sensorsRepository.startSuperpowers { state ->
            post { superpowers = state }
        }
    }

    fun startGnss() {
        gnssRequested = true
        if (inForeground && screen == Screen.Gnss) startGnssInternal()
    }

    fun stopGnss() {
        gnssRequested = false
        gnssRepository.stop()
        gnss = idleGnssState()
    }

    fun startBle() {
        bleRequested = true
        if (inForeground && screen == Screen.Bluetooth) startBleInternal()
    }

    fun stopBle() {
        bleRequested = false
        bleRepository.stop()
        ble = ble.copy(scanning = false)
    }

    fun startAudio() {
        audioRequested = true
        if (inForeground && screen == Screen.Audio) startAudioInternal()
    }

    fun stopAudio() {
        audioRequested = false
        audioAnalyzer.stop()
        audio = AudioState()
    }

    fun onNfcTag(tag: Tag) {
        if (!inForeground || screen != Screen.Nfc || cleared) return

        val state = currentNfcState()
        val readGeneration = nfcReadGeneration.incrementAndGet()

        try {
            nfcExecutor.execute {
                val parsed = NfcParser.parse(
                    tag = tag,
                    available = state.available,
                    enabled = state.enabled,
                )
                post {
                    if (
                        nfcReadGeneration.get() == readGeneration &&
                        inForeground &&
                        screen == Screen.Nfc
                    ) {
                        nfc = parsed
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            if (!cleared) {
                nfc = nfc.copy(
                    lastError = "Leitor NFC interno indisponível.",
                )
            }
        }
    }

    fun refreshNfcState() {
        val current = currentNfcState()
        nfc = nfc.copy(
            available = current.available,
            enabled = current.enabled,
        )
    }

    fun clearNfcFailure() {
        if (nfc.lastError != null) {
            nfc = nfc.copy(lastError = null)
        }
    }

    fun reportNfcFailure(
        operation: String,
        error: Throwable,
    ) {
        if (cleared) return
        nfc = nfc.copy(
            lastError = "$operation: ${error::class.java.simpleName}",
        )
    }

    fun runCpuBaseline() {
        if (computeRunning || cleared) return

        computeRunning = true
        computeResult = null
        computeError = null
        val generation = computeGeneration.incrementAndGet()

        try {
            computeFuture = computeExecutor.submit {
                val attempt = runCatching {
                    computeBenchmark.run()
                }

                post {
                    if (computeGeneration.get() != generation) {
                        return@post
                    }

                    val error = attempt.exceptionOrNull()
                    if (error !is CancellationException) {
                        computeResult = attempt.getOrNull()
                        computeError = error?.let {
                            "Benchmark falhou: ${it::class.java.simpleName}"
                        }
                    }
                    computeRunning = false
                    computeFuture = null
                }
            }
        } catch (_: RejectedExecutionException) {
            if (computeGeneration.get() == generation) {
                computeRunning = false
                computeError = "Executor de benchmark indisponível."
            }
        }
    }

    fun cancelCpuBaseline() {
        computeGeneration.incrementAndGet()
        computeFuture?.cancel(true)
        computeFuture = null
        computeRunning = false
        computeError = null
    }

    fun report(): String = ReportFormatter.build(
        snapshot = device,
        sensors = sensors,
        cameras = cameras,
        cameraProbeErrors = cameraProbeErrors,
        probeWarnings = inventoryWarnings,
        systemFeatures = systemFeatures,
        network = network,
        nfc = nfc,
    )

    override fun onCleared() {
        cleared = true
        nfcReadGeneration.incrementAndGet()
        stopLiveModules(clearUserRequests = true)
        audioAnalyzer.close()
        cancelCpuBaseline()
        ioExecutor.shutdownNow()
        nfcExecutor.shutdownNow()
        computeExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        pendingAutomaticReport = null
        super.onCleared()
    }

    private fun startAutomaticModulesForCurrentScreen() {
        when (screen) {
            Screen.Sensors -> startMotion()
            Screen.Superpowers,
            Screen.Environment,
            -> startSuperpowers()
            else -> Unit
        }
    }

    private fun startGnssInternal() {
        gnssRepository.start { state ->
            post { gnss = state }
        }
    }

    private fun startBleInternal() {
        bleRepository.start { state ->
            post { ble = state }
        }
    }

    private fun startAudioInternal() {
        audioAnalyzer.start { state ->
            post { audio = state }
        }
    }

    private fun stopLiveModules(clearUserRequests: Boolean) {
        sensorsRepository.stopMotion()
        gnssRepository.stop()
        bleRepository.stop()
        audioAnalyzer.stop()

        gnss = gnss.copy(running = false)
        ble = ble.copy(scanning = false)
        audio = audio.copy(running = false, starting = false)

        if (clearUserRequests) {
            gnssRequested = false
            bleRequested = false
            audioRequested = false
        }
    }

    private fun probeFailure(
        component: String,
        error: Throwable,
    ): String = "$component: ${error::class.java.simpleName}"

    private fun idleGnssState(): GnssState = GnssState(
        locationEnabled = gnss.locationEnabled,
        gpsProviderEnabled = gnss.gpsProviderEnabled,
        rawMeasurementsSupported = gnss.rawMeasurementsSupported,
    )

    private fun currentNfcState(): NfcState {
        val adapter = getApplication<Application>()
            .getSystemService(NfcManager::class.java)
            ?.defaultAdapter
        return NfcState(
            available = adapter != null,
            enabled = adapter?.isEnabled == true,
        )
    }

    private fun copyReportToClipboardAndPublishStatus(
        text: String,
        fileSaved: Boolean,
        automatic: Boolean,
    ) {
        val generation = reportGeneration.incrementAndGet()
        val clipboardResult = runCatching {
            clipboard.setPrimaryClip(reportClip(text))
        }

        if (reportGeneration.get() != generation) return

        copyStatus = when {
            clipboardResult.isSuccess && fileSaved && automatic ->
                "Inventário atualizado, copiado automaticamente e salvo localmente."
            clipboardResult.isSuccess && fileSaved ->
                "Relatório copiado e salvo localmente."
            clipboardResult.isSuccess ->
                "Relatório copiado; falha ao salvar o snapshot privado."
            fileSaved ->
                "Snapshot salvo; falha ao copiar para o clipboard."
            else ->
                "Falha ao copiar e ao salvar o inventário."
        }
    }

    private fun reportClip(text: String): ClipData =
        ClipData.newPlainText(
            "A25 Lab — especificações internas",
            text,
        ).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(
                    "android.content.extra.IS_SENSITIVE",
                    true,
                )
            }
        }

    private fun post(block: () -> Unit) {
        if (cleared) return

        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!cleared) block()
        } else {
            mainHandler.post {
                if (!cleared) block()
            }
        }
    }
}

enum class Screen(val title: String) {
    Dashboard("A25 Lab"),
    Perception("Percepção"),
    Connectivity("Conectividade"),
    Lab("Laboratório"),
    Superpowers("Movimento & 3D"),
    Environment("Ambiente"),
    Sensors("Sensores"),
    Gnss("GNSS"),
    Bluetooth("Bluetooth LE"),
    Audio("Áudio"),
    Cameras("Camera2"),
    Nfc("NFC"),
    Network("Rede"),
    Compute("Compute / IA"),
}
