package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.launch
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
            NavigationBar(
                containerColor = BackgroundSurface,
                contentColor = TextPrimary,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                val items = listOf(
                    Triple("library", "Library", Icons.Default.Home),
                    Triple("dac", "DAC", Icons.Default.Info),
                    Triple("settings", "Settings", Icons.Default.Settings),
                    Triple("format_variants", "Codecs", Icons.Default.Star)
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
                            indicatorColor = AmberGold.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("nav_item_$tabId")
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
                "library" -> LibraryScreen(
                    viewModel = viewModel
                )
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
