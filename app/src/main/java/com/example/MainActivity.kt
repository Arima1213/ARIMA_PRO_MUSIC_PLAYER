package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    val isScanning by viewModel.isScanning.collectAsState()
    val showScanningProgress by viewModel.showScanningProgressDialog.collectAsState()

    val scanStepText by viewModel.scanStepText.collectAsState()
    val scanFilesScanned by viewModel.scanFilesScanned.collectAsState()
    val scanFilesTotal by viewModel.scanFilesTotal.collectAsState()
    val scanTracksFound by viewModel.scanTracksFound.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    // Back button: one back press navigates to library tab instead of exiting app
    val navItems = listOf(
        Triple("library", "Library", Icons.Default.Home),
        Triple("dac", "DAC", Icons.Default.Info),
        Triple("settings", "Settings", Icons.Default.Settings),
        Triple("format_variants", "Codecs", Icons.Default.Star)
    )

    // Back button: tap once → go to library. Tap again → exit app
    var interceptedBackOnce by remember { mutableStateOf(false) }
    BackHandler {
        if (currentTab != "library") {
            viewModel.selectTab("library")
            interceptedBackOnce = true
        } else if (!interceptedBackOnce) {
            interceptedBackOnce = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = BackgroundSurface,
                contentColor = TextPrimary,
                tonalElevation = 0.dp,
                modifier = Modifier.height(64.dp)
            ) {
                navItems.forEach { (tabId, label, icon) ->
                    val isSelected = currentTab == tabId
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tabId) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmberGold,
                            unselectedIconColor = TextMuted,
                            selectedTextColor = AmberGold,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.testTag("bottom_nav_$tabId")
                    )
                }
            }
        },
        containerColor = BackgroundPrimary
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "library" -> LibraryScreen(
                    viewModel = viewModel,
                    onOpenMenu = { /* drawer removed */ }
                )
                "player" -> PlayerScreen(viewModel)
                "dac" -> DacMonitorScreen(viewModel)
                "settings" -> SettingsScreen(viewModel)
                "format_variants" -> FormatBadgeVariantsScreen(viewModel)
            }

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
}
