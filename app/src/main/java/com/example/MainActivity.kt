package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AudioViewModel
import com.example.ui.components.DacHotplugDialog

class MainActivity : ComponentActivity() {

    private val viewModel: AudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var showSplash by remember { mutableStateOf(true) }
                var hasPermissions by remember {
                    mutableStateOf(com.example.ui.screens.hasAllRequiredPermissions(this@MainActivity))
                }

                if (showSplash) {
                    SplashScreen(
                        onSplashCompleted = { showSplash = false }
                    )
                } else if (!hasPermissions) {
                    PermissionsScreen(
                        onPermissionsGranted = { hasPermissions = true }
                    )
                } else {
                    MainAppContent(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: AudioViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val activeSong by viewModel.audioEngine.currentSong.collectAsState()

    val showEqPanel by viewModel.showEqualizerPanel.collectAsState()
    val eqBlocked by viewModel.eqBlockedByBitPerfect.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val showScanningProgress by viewModel.showScanningProgressDialog.collectAsState()

    val scanStepText by viewModel.scanStepText.collectAsState()
    val scanFilesScanned by viewModel.scanFilesScanned.collectAsState()
    val scanFilesTotal by viewModel.scanFilesTotal.collectAsState()
    val scanTracksFound by viewModel.scanTracksFound.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Safely retrieve the Activity from the current Context
    val activity = remember(context) {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is android.app.Activity) {
                return@remember currentContext
            }
            currentContext = currentContext.baseContext
        }
        null
    }

    // Intercept back actions
    BackHandler(enabled = true) {
        if (currentTab != "library") {
            viewModel.selectTab("library")
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                activity?.moveTaskToBack(true)
            } else {
                lastBackPressTime = currentTime
                android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentTab != "player") {
                Column {
                    activeSong?.let { song ->
                        MiniPlayerPill(
                            activeSong = song,
                            isPlaying = isPlaying,
                            viewModel = viewModel
                        )
                    }
                    NavigationBar(
                        containerColor = Color.Transparent,
                        contentColor = TextPrimary,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier
                            .background(BackgroundSurface) // Simulates glass blur bg
                            .border(1.dp, BorderSubtle)
                    ) {
                        val items = listOf(
                            Triple("library", "Library", Icons.Default.Home),
                            Triple("dac", "DAC", Icons.Default.Info),
                            Triple("settings", "Settings", Icons.Default.Settings)
                        )

                        items.forEach { (tabId, label, icon) ->
                            val isSelected = currentTab == tabId
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(tabId) },
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AmberGold,
                                    selectedTextColor = AmberGold,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = AmberGold.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.testTag("nav_item_$tabId")
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundPrimary)
                .padding(innerPadding)
        ) {
            androidx.compose.animation.Crossfade(
                targetState = currentTab,
                animationSpec = androidx.compose.animation.core.tween(300),
                label = "MainScreenTransition"
            ) { tab ->
                when (tab) {
                    "library" -> LibraryScreen(
                        viewModel = viewModel
                    )
                    "player" -> PlayerScreen(viewModel)
                    "dac" -> DacMonitorScreen(viewModel)
                    "settings" -> SettingsScreen(viewModel)
                    "format_variants" -> FormatBadgeVariantsScreen(viewModel)
                    "album_detail" -> {
                        val album = viewModel.selectedAlbum.value
                        val albumSongs = viewModel.allSongs.value.filter { it.album == album?.title }
                        if (album != null) {
                            com.example.ui.screens.AlbumDetailScreen(
                                album = album,
                                songs = albumSongs.sortedBy { it.path },
                                viewModel = viewModel,
                                onBack = { viewModel.selectTab("library"); viewModel.clearSelection() }
                            )
                        }
                    }
                    "artist_detail" -> {
                        val artist = viewModel.selectedArtist.value
                        val artistSongs = viewModel.allSongs.value.filter { it.artist == artist?.name }
                        if (artist != null) {
                            com.example.ui.screens.ArtistDetailScreen(
                                artist = artist,
                                songs = artistSongs.sortedWith(compareBy({ it.album }, { it.path })),
                                viewModel = viewModel,
                                onBack = { viewModel.selectTab("library"); viewModel.clearSelection() }
                            )
                        }
                    }
                }
            }
        }

        // Sheet components overlay layers
        if (showEqPanel) {
            EqualizerPanel(
                viewModel = viewModel,
                onDismiss = { viewModel.showEqualizerPanel.value = false }
            )
        }

        if (eqBlocked) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissEqBlockedDialog() },
                containerColor = BackgroundSurface,
                title = {
                    Text(
                        text = "EQ Tidak Tersedia",
                        style = HeadlineSmall.copy(color = AmberGold)
                    )
                },
                text = {
                    Text(
                        text = "Equalizer aktif butuh system audio mixer.\n\n" +
                            "Ini akan menonaktifkan BIT-PERFECT MODE.\n\n" +
                            "Matikan BIT-PERFECT MODE di Settings untuk menggunakan EQ.",
                        style = BodyMedium.copy(color = TextSecondary)
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                        onClick = {
                            viewModel.bitPerfectMode.value = false
                            viewModel.equalizerEnabled.value = true
                            viewModel.dismissEqBlockedDialog()
                        }
                    ) {
                        Text("Aktifkan EQ", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEqBlockedDialog() }) {
                        Text("Batal", color = TextSecondary)
                    }
                }
            )
        }

        if (showScanningProgress) {
            ScanningProgressDialog(
                stepText = scanStepText,
                filesScanned = scanFilesScanned,
                filesTotal = scanFilesTotal,
                tracksFound = scanTracksFound,
                progress = scanProgress,
                onCancel = { viewModel.showScanningProgressDialog.value = false; viewModel.isScanning.value = false }
            )
        }

        val showDacMissingDialog by viewModel.audioEngine.showDacMissingDialog.collectAsState()
        if (showDacMissingDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.audioEngine.showDacMissingDialog.value = false },
                title = { Text("DAC Tidak Terdeteksi", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) },
                text = { Text("DAC Exclusive Mode aktif tetapi tidak ada USB DAC yang terhubung. Silakan hubungkan USB DAC untuk melanjutkan pemutaran.") },
                confirmButton = {
                    Button(onClick = { viewModel.audioEngine.showDacMissingDialog.value = false }) {
                        Text("Tutup")
                    }
                }
            )
        }

        // Display DAC hotplug notification dialog or toast alerts on event emissions
        val dacHotplugEvent by viewModel.dacHotplugEvent.collectAsState()

        // Observe DAC Routing STATUS Changes and alert the user
        val dacRoutingStatus by viewModel.audioEngine.dacRoutingStatus.collectAsState()
        LaunchedEffect(dacRoutingStatus) {
            when (val status = dacRoutingStatus) {
                is com.example.domain.service.DacRoutingStatus.Success -> {
                    android.widget.Toast.makeText(
                        context,
                        "Playback successfully routed to: ${status.deviceName}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                is com.example.domain.service.DacRoutingStatus.Failed -> {
                    android.widget.Toast.makeText(
                        context,
                        "Routing Error: ${status.reason}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                else -> {}
            }
        }

        dacHotplugEvent?.let { event ->
            if (event.type == com.example.ui.viewmodel.DacHotplugType.DETECTED && event.dacInfo != null) {
                DacHotplugDialog(
                    dacInfo = event.dacInfo,
                    onDismiss = { viewModel.clearDacHotplugEvent() },
                    onEnableExclusiveMode = {
                        viewModel.dacExclusiveMode.value = true
                        viewModel.audioEngine.routeOutputToDac()
                        viewModel.clearDacHotplugEvent()
                    },
                    onPlayAnyway = {
                        viewModel.dacExclusiveMode.value = false
                        viewModel.clearDacHotplugEvent()
                    }
                )
            } else if (event.type == com.example.ui.viewmodel.DacHotplugType.DISCONNECTED) {
                LaunchedEffect(event.timestamp) {
                    android.widget.Toast.makeText(
                        context,
                        "USB DAC Terputus: ${event.dacName ?: "Device"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    viewModel.clearDacHotplugEvent()
                }
            }
        }
    }
}
