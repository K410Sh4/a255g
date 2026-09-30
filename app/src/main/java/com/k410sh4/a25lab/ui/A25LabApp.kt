package com.k410sh4.a25lab.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.k410sh4.a25lab.ui.theme.A25LabTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A25LabApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    var pendingPermissionAction by remember {
        mutableStateOf<(() -> Unit)?>(null)
    }
    var permissionRequestInFlight by remember {
        mutableStateOf(false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // O módulo de destino valida a permissão novamente e produz um erro
        // legível se o usuário negou ou concedeu apenas acesso aproximado.
        val action = pendingPermissionAction
        pendingPermissionAction = null
        permissionRequestInFlight = false
        action?.invoke()
    }

    fun runWithPermissions(
        permissions: Array<String>,
        action: () -> Unit,
    ) {
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission,
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            action()
        } else if (!permissionRequestInFlight) {
            pendingPermissionAction = action
            permissionRequestInFlight = true
            runCatching {
                permissionLauncher.launch(permissions)
            }.onFailure {
                pendingPermissionAction = null
                permissionRequestInFlight = false
            }
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
                    if (viewModel.screen !in rootScreens) {
                        TopAppBar(
                            title = {
                                Text(viewModel.screen.title)
                            },
                            navigationIcon = {
                                TextButton(
                                    onClick = {
                                        viewModel.navigate(parent)
                                    },
                                ) {
                                    Text("‹ Voltar")
                                }
                            },
                        )
                    }
                },
                bottomBar = {
                    PremiumBottomBar(
                        current = viewModel.screen,
                        onNavigate = viewModel::navigate,
                    )
                },
            ) { padding ->
                BackHandler(
                    enabled = viewModel.screen != Screen.Dashboard,
                ) {
                    val backTarget = if (
                        viewModel.screen == Screen.Perception ||
                        viewModel.screen == Screen.Connectivity ||
                        viewModel.screen == Screen.Lab
                    ) {
                        Screen.Dashboard
                    } else {
                        parent
                    }
                    viewModel.navigate(backTarget)
                }

                when (viewModel.screen) {
                    Screen.Dashboard -> PremiumDashboardScreen(
                        modifier = Modifier.padding(padding),
                        device = viewModel.device,
                        copyStatus = viewModel.copyStatus,
                        refreshRunning = viewModel.refreshRunning,
                        inventoryWarnings = viewModel.inventoryWarnings,
                        onRefresh = viewModel::refreshAllAndCopy,
                        onCopy = viewModel::copySpecificationsToClipboard,
                        onNavigate = viewModel::navigate,
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "Relatório A25 Lab",
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    viewModel.report(),
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    "Compartilhar relatório",
                                ),
                            )
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

                    Screen.Sensors -> PremiumSensorsScreen(
                        modifier = Modifier.padding(padding),
                        sensors = viewModel.sensors,
                        motion = viewModel.motion,
                    )

                    Screen.Gnss -> PremiumGnssScreen(
                        modifier = Modifier.padding(padding),
                        state = viewModel.gnss,
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

                    Screen.Bluetooth -> PremiumBleScreen(
                        modifier = Modifier.padding(padding),
                        state = viewModel.ble,
                        onStart = {
                            runWithPermissions(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                ),
                                viewModel::startBle,
                            )
                        },
                        onStop = viewModel::stopBle,
                    )

                    Screen.Audio -> PremiumAudioScreen(
                        modifier = Modifier.padding(padding),
                        state = viewModel.audio,
                        onStart = {
                            runWithPermissions(
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                ),
                                viewModel::startAudio,
                            )
                        },
                        onStop = viewModel::stopAudio,
                    )

                    Screen.Cameras -> PremiumCamerasScreen(
                        modifier = Modifier.padding(padding),
                        totalIds = viewModel.device?.cameraCount
                            ?: viewModel.cameras.size,
                        cameras = viewModel.cameras,
                        errors = viewModel.cameraProbeErrors,
                    )

                    Screen.Nfc -> PremiumNfcScreen(
                        modifier = Modifier.padding(padding),
                        state = viewModel.nfc,
                    )

                    Screen.Network -> PremiumNetworkScreen(
                        modifier = Modifier.padding(padding),
                        state = viewModel.network,
                        onRefresh = viewModel::refreshNetwork,
                    )

                    Screen.Compute -> PremiumComputeScreen(
                        modifier = Modifier.padding(padding),
                        running = viewModel.computeRunning,
                        elapsedMs = viewModel.computeResult?.elapsedMs,
                        gflops = viewModel.computeResult?.estimatedGflops,
                        error = viewModel.computeError,
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
    val selectedRoot = when (current) {
        Screen.Dashboard -> Screen.Dashboard
        Screen.Perception,
        Screen.Connectivity,
        Screen.Lab,
        -> current
        else -> parentScreen(current)
    }

    NavigationBar {
        listOf(
            Triple(Screen.Dashboard, "⌂", "Início"),
            Triple(Screen.Perception, "◈", "Percepção"),
            Triple(Screen.Connectivity, "⌁", "Conexões"),
            Triple(Screen.Lab, "⌘", "Lab"),
        ).forEach { (screen, icon, label) ->
            NavigationBarItem(
                modifier = Modifier.testTag("nav-${screen.name}"),
                selected = selectedRoot == screen,
                onClick = {
                    onNavigate(screen)
                },
                icon = {
                    Text(
                        icon,
                        modifier = Modifier.clearAndSetSemantics { },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                label = {
                    Text(label)
                },
            )
        }
    }
}

private fun parentScreen(screen: Screen): Screen = when (screen) {
    Screen.Superpowers,
    Screen.Environment,
    Screen.Audio,
    -> Screen.Perception

    Screen.Gnss,
    Screen.Bluetooth,
    Screen.Nfc,
    Screen.Network,
    -> Screen.Connectivity

    Screen.Sensors,
    Screen.Cameras,
    Screen.Compute,
    -> Screen.Lab

    Screen.Dashboard,
    Screen.Perception,
    Screen.Connectivity,
    Screen.Lab,
    -> screen
}
