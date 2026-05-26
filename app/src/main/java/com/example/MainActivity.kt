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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF141414),
                drawerTonalElevation = 0.dp,
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = "ARIMA PRO",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = AmberGold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "High-Res Audio Engine",
                        style = BodyMedium.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }

                Divider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Items
                val menuItems = listOf(
                    Triple("library", "Library", Icons.Default.Home),
                    Triple("player", "Now Playing", Icons.Default.PlayArrow),
                    Triple("dac", "DAC Monitor", Icons.Default.Info),
                    Triple("settings", "Settings", Icons.Default.Settings),
                    Triple("format_variants", "Audio Codecs", Icons.Default.Star)
                )

                menuItems.forEach { (tabId, label, icon) ->
                    val isSelected = currentTab == tabId
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = label,
                                style = BodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) Color.Black else TextSecondary
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            viewModel.selectTab(tabId)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = AmberGold,
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.Black,
                            unselectedTextColor = TextSecondary,
                            selectedIconColor = Color.Black,
                            unselectedIconColor = TextSecondary
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("drawer_nav_$tabId")
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                        viewModel = viewModel,
                        onOpenMenu = { scope.launch { drawerState.open() } }
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
}
