package com.k410sh4.a25lab.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.nfc.NfcAdapter
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
import com.k410sh4.a25lab.model.SystemFeatureInfo
import com.k410sh4.a25lab.util.ReportFormatter
import java.io.File
import java.util.concurrent.Executors

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val background = Executors.newSingleThreadExecutor()
    private val sensorsRepository = SensorRepository(context)
    private val cameraProbe = CameraProbe(context)
    private val hardwareProbe = HardwareProbe(context)
    private val gnssRepository = GnssRepository(context)
    private val bleRepository = BleRepository(context)
    private val audioAnalyzer = AudioAnalyzer(context)
    private val networkProbe = NetworkProbe(context)
    private val computeBenchmark = ComputeBenchmark()
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val snapshotFile = File(context.filesDir, "a25lab_last_internal_specs.txt")

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
    var gnss by mutableStateOf(GnssState())
        private set
    var ble by mutableStateOf(BleState())
        private set
    var cameras by mutableStateOf<List<CameraInfo>>(emptyList())
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
    var copyStatus by mutableStateOf("Preparando inventário interno…")
        private set

    init {
        refreshAllAndCopy()
    }

    fun navigate(target: Screen) {
        if (screen == target) return
        stopLiveModules()
        screen = target
        if (target == Screen.Sensors) startMotion()
        if (target == Screen.Network) refreshNetwork()
    }

    fun refreshAllAndCopy() {
        val sensorList = runCatching { sensorsRepository.listSensors() }.getOrDefault(emptyList())
        val cameraList = runCatching { cameraProbe.probe() }.getOrDefault(emptyList())
        val features = runCatching { hardwareProbe.systemFeatures() }.getOrDefault(emptyList())

        sensors = sensorList
        cameras = cameraList
        systemFeatures = features
        network = runCatching { networkProbe.snapshot() }.getOrDefault(NetworkState())

        val adapter = NfcAdapter.getDefaultAdapter(context)
        nfc = nfc.copy(
            available = adapter != null,
            enabled = adapter?.isEnabled == true,
        )

        device = runCatching {
            hardwareProbe.snapshot(
                cameraCount = cameraList.size,
                sensorCount = sensorList.size,
                featureCount = features.size,
            )
        }.getOrNull()

        copySpecificationsToClipboard()
    }

    fun refreshStaticProbe() = refreshAllAndCopy()

    fun refreshNetwork() {
        network = runCatching { networkProbe.snapshot() }.getOrDefault(NetworkState())
    }

    fun copySpecificationsToClipboard() {
        val text = report()
        if (device == null) {
            copyStatus = "Não foi possível copiar: snapshot indisponível."
            return
        }

        runCatching {
            clipboard.setPrimaryClip(
                ClipData.newPlainText("A25 Lab — especificações internas", text),
            )
            snapshotFile.writeText(text)
        }.onSuccess {
            copyStatus = "Especificações internas copiadas automaticamente (${text.length} caracteres) e salvas localmente."
        }.onFailure { error ->
            copyStatus = "Falha ao copiar especificações: ${error.message ?: "erro desconhecido"}"
        }
    }

    fun startMotion() {
        sensorsRepository.startMotion { sample -> post { motion = sample } }
    }

    fun startGnss() {
        gnssRepository.start { state -> post { gnss = state } }
    }

    fun stopGnss() {
        gnssRepository.stop()
        gnss = gnss.copy(running = false)
    }

    fun startBle() {
        bleRepository.start { state -> post { ble = state } }
    }

    fun stopBle() {
        bleRepository.stop()
        ble = ble.copy(scanning = false)
    }

    fun startAudio() {
        audioAnalyzer.start { state -> post { audio = state } }
    }

    fun stopAudio() {
        audioAnalyzer.stop()
        audio = audio.copy(running = false)
    }

    fun onNfcTag(tag: Tag) {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        background.execute {
            val parsed = NfcParser.parse(tag, adapter != null, adapter?.isEnabled == true)
            post { nfc = parsed }
        }
    }

    fun refreshNfcState() {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        nfc = nfc.copy(available = adapter != null, enabled = adapter?.isEnabled == true)
    }

    fun runCpuBaseline() {
        if (computeRunning) return
        computeRunning = true
        computeResult = null
        background.execute {
            val result = runCatching { computeBenchmark.run() }.getOrNull()
            post {
                computeResult = result
                computeRunning = false
            }
        }
    }

    fun report(): String = ReportFormatter.build(
        snapshot = device,
        sensors = sensors,
        cameras = cameras,
        systemFeatures = systemFeatures,
        network = network,
        nfc = nfc,
    )

    fun stopLiveModules() {
        sensorsRepository.stopMotion()
        gnssRepository.stop()
        bleRepository.stop()
        audioAnalyzer.stop()
        gnss = gnss.copy(running = false)
        ble = ble.copy(scanning = false)
        audio = audio.copy(running = false)
    }

    override fun onCleared() {
        stopLiveModules()
        audioAnalyzer.close()
        background.shutdownNow()
        super.onCleared()
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}

enum class Screen(val title: String) {
    Dashboard("A25 Lab"),
    Sensors("Sensores"),
    Gnss("GNSS"),
    Bluetooth("Bluetooth LE"),
    Audio("Áudio"),
    Cameras("Camera2"),
    Nfc("NFC"),
    Network("Rede"),
    Compute("Compute / IA"),
}
