package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AudioViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(
                        onSplashCompleted = { showSplash = false }
                    )
                } else {
                    MainAppContent(viewModel)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: AudioViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val activeSong by viewModel.audioEngine.currentSong.collectAsState()

    val showEqPanel by viewModel.showEqualizerPanel.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val showScanningProgress by viewModel.showScanningProgressDialog.collectAsState()

    val scanStepText by viewModel.scanStepText.collectAsState()
    val scanFilesScanned by viewModel.scanFilesScanned.collectAsState()
    val scanFilesTotal by viewModel.scanFilesTotal.collectAsState()
    val scanTracksFound by viewModel.scanTracksFound.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // High-fidelity integrated Bottom Navigation Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mini Player HUD shows on any screen except Player tab when a song is loaded
                if (currentTab != "player" && activeSong != null) {
                    MiniPlayerHUD(
                        song = activeSong!!,
                        isPlaying = isPlaying,
                        onPlayPauseClick = {
                            if (isPlaying) viewModel.audioEngine.pause() else viewModel.audioEngine.play()
                        },
                        onMiniPlayerClick = { viewModel.selectTab("player") }
                    )
                }

                NavigationBar(
                    containerColor = BackgroundSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("app_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == "library",
                        onClick = { viewModel.selectTab("library") },
                        modifier = Modifier.testTag("nav_library"),
                        icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                        label = { Text("Library", style = TechnicalSmall.copy(fontSize = 11.sp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AmberGold
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == "player",
                        onClick = { viewModel.selectTab("player") },
                        modifier = Modifier.testTag("nav_player"),
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Player") },
                        label = { Text("Player", style = TechnicalSmall.copy(fontSize = 11.sp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AmberGold
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == "dac",
                        onClick = { viewModel.selectTab("dac") },
                        modifier = Modifier.testTag("nav_dac"),
                        icon = { Icon(Icons.Default.Info, contentDescription = "Dac") },
                        label = { Text("DAC Monitor", style = TechnicalSmall.copy(fontSize = 11.sp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AmberGold
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == "settings",
                        onClick = { viewModel.selectTab("settings") },
                        modifier = Modifier.testTag("nav_settings"),
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", style = TechnicalSmall.copy(fontSize = 11.sp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AmberGold
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == "format_variants",
                        onClick = { viewModel.selectTab("format_variants") },
                        modifier = Modifier.testTag("nav_variants"),
                        icon = { Icon(Icons.Default.Star, contentDescription = "Variants") },
                        label = { Text("Codecs", style = TechnicalSmall.copy(fontSize = 11.sp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AmberGold,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AmberGold
                        )
                    )
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
            when (currentTab) {
                "library" -> LibraryScreen(viewModel)
                "player" -> PlayerScreen(viewModel)
                "dac" -> DacMonitorScreen(viewModel)
                "settings" -> SettingsScreen(viewModel)
                "format_variants" -> FormatBadgeVariantsScreen(viewModel)
            }
        }

        // Sheet components overlay layers

        if (showEqPanel) {
            EqualizerPanel(
                viewModel = viewModel,
                onDismiss = { viewModel.showEqualizerPanel.value = false }
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
    }
}

@Composable
fun MiniPlayerHUD(
    song: com.example.domain.model.Song,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onMiniPlayerClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMiniPlayerClick() }
            .background(BackgroundSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BackgroundCard)
        ) {
            VinylArtVector(modifier = Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = BodyLarge.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormatBadge(format = song.format)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = song.artist,
                    style = BodyMedium.copy(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.testTag("mini_play_pause_button")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = AmberGold
            )
        }
    }
    Divider(color = BorderSubtle, thickness = 0.5.dp)
}
