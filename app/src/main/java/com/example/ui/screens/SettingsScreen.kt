package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.AudioViewModel
import com.example.ui.components.AppHeader

@Composable
fun SettingsScreen(viewModel: AudioViewModel) {

    // Resampling
    val resamplingVal by viewModel.resamplingRate.collectAsState()
    val ditheringVal by viewModel.ditheringEnabled.collectAsState()
    val volNormVal by viewModel.volumeNormalizationEnabled.collectAsState()
    val gaplessVal by viewModel.gaplessPlaybackEnabled.collectAsState()

    // DAC & Output
    val exclusiveVal by viewModel.dacExclusiveMode.collectAsState()
    val bufferVal by viewModel.usbBufferSize.collectAsState()
    val bitPerfectVal by viewModel.bitPerfectMode.collectAsState()
    val dsdNativeVal by viewModel.dsdNativeMode.collectAsState()

    // Equalizer
    val eqVal by viewModel.equalizerEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(bottom = 80.dp) // space for bottom menu
    ) {
        // Unified App Header
        AppHeader(
            title = "Settings"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding, vertical = 8.dp)
        ) {
            // AUDIO PARAMETERS GROUP
            item {
                SettingsHeader("AUDIO PATHS")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                ) {
                    DropdownSettingRow(
                        label = "Resampling",
                        sub = "Internal hardware DAC resampling filter rate",
                        currentVal = resamplingVal,
                        options = listOf("Bit-perfect", "96 kHz", "192 kHz", "384 kHz"),
                        enabled = !bitPerfectVal,
                        onSelect = { viewModel.resamplingRate.value = it }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    ToggleSettingRow(
                        label = "Dithering",
                        sub = "Applies triangular noise dither shaping",
                        isChecked = ditheringVal,
                        enabled = !bitPerfectVal,
                        onCheckedChange = { viewModel.ditheringEnabled.value = it }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    ToggleSettingRow(
                        label = "Volume Normalization",
                        sub = "ReplayGain standards EBU R128 compliance",
                        isChecked = volNormVal,
                        onCheckedChange = { viewModel.volumeNormalizationEnabled.value = it }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    ToggleSettingRow(
                        label = "Gapless Playback",
                        sub = "Bypasses pipeline latency between queued audio tracks",
                        isChecked = gaplessVal,
                        onCheckedChange = { viewModel.gaplessPlaybackEnabled.value = it }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // DAC & DIRECT OUTPUT PARAMETERS GROUP
            item {
                SettingsHeader("DAC & DIRECT HARDWARE ENGINE")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                ) {
                    ToggleSettingRow(
                        label = "DAC Exclusive Mode",
                        sub = "Completely bypasses OS audio architecture layers",
                        isChecked = exclusiveVal,
                        onCheckedChange = { viewModel.dacExclusiveMode.value = it }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    DropdownSettingRow(
                        label = "USB Buffer Size",
                        sub = "Buffering size for steady digital transfers",
                        currentVal = bufferVal,
                        options = listOf("Min Latency", "Low Latency", "Normal", "Max Latency (Stable)"),
                        onSelect = { viewModel.usbBufferSize.value = it }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    ToggleSettingRow(
                        label = "Bit-perfect Mode",
                        sub = "Matches hardware clocks exactly to source sample rates",
                        isChecked = bitPerfectVal,
                        onCheckedChange = { viewModel.bitPerfectMode.value = it }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    DropdownSettingRow(
                        label = "DSD Native Mode",
                        sub = "Method to stream DSD audio formats",
                        currentVal = dsdNativeVal,
                        options = listOf("DoP Marker", "DSD256 Native", "ASIO Encapsulated"),
                        onSelect = { viewModel.dsdNativeMode.value = it }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // EQUALIZER TUNER SECTION
            item {
                SettingsHeader("DIGITAL EQUALIZERS")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                ) {
                    ToggleSettingRow(
                        label = "Equalizer Active State",
                        sub = "Enable digital sound processing filter network",
                        isChecked = eqVal,
                        enabled = !bitPerfectVal,
                        onCheckedChange = { viewModel.toggleEqualizer(it) }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !bitPerfectVal) { viewModel.showEqualizerPanel.value = true }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Edit 10-Band Equalizer Presets", style = BodyLarge.copy(fontWeight = FontWeight.Bold), color = if (!bitPerfectVal) TextPrimary else TextSecondary)
                            Text("Set band frequencies from 32Hz to 16kHz", style = BodyMedium, color = TextSecondary)
                        }
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Edit", tint = if (!bitPerfectVal) AmberGold else TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // FILE LIBRARY PREFERENCES GROUP
            item {
                SettingsHeader("MEDIA DIRECTORY ENGINE")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                ) {
                    var scanOnStart by remember { mutableStateOf(true) }
                    var autoDetect by remember { mutableStateOf(true) }

                    ToggleSettingRow(
                        label = "Scan on App Start",
                        sub = "Scans registered directory folders for raw changes",
                        isChecked = scanOnStart,
                        onCheckedChange = { scanOnStart = it }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    ToggleSettingRow(
                        label = "Auto-detect Format",
                        sub = "Interprets files headers instead of extensions",
                        isChecked = autoDetect,
                        onCheckedChange = { autoDetect = it }
                    )
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.triggerScan("/storage/music") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scan Full Library Now →",
                            style = TechnicalLarge.copy(color = AmberGold, fontWeight = FontWeight.Bold)
                        )
                    }
                    Divider(color = BorderSubtle, thickness = 0.5.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectLibraryTab("folders"); viewModel.selectTab("library") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Manage Folder Directories ->",
                            style = BodyLarge.copy(color = TextPrimary)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // UI & VISUAL PREFERENCES GROUP
            item {
                SettingsHeader("UI & VISUAL PREFERENCES")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectTab("format_variants") }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Audio Codec Format Badges", style = BodyLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Text("View visual format badge identifiers", style = BodyMedium, color = TextSecondary)
                        }
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "View", tint = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // ELEGAN BRAND ABOUT DETAILS CARD
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ARIMA PRO",
                        style = HeadlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        ),
                        color = AmberGold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "v1.0.0 (Build 4092)",
                        style = TechnicalSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Core Audio Engine 2.4",
                        style = TechnicalSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Crafted for audiophiles. Powered by direct raw PCM Android USB Drivers.",
                        style = BodyMedium.copy(textAlign = TextAlign.Center),
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// --- SUB SETTINGS UI HELPERS ---

@Composable
fun SettingsHeader(text: String) {
    Text(
        text = "— $text",
        style = LabelCaps.copy(color = TextSecondary, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun ToggleSettingRow(
    label: String,
    sub: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = BodyLarge.copy(fontWeight = FontWeight.Bold), color = if (enabled) TextPrimary else TextSecondary)
            Text(text = sub, style = BodyMedium, color = TextSecondary)
        }
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AmberGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = BackgroundPrimary
            )
        )
    }
}

@Composable
fun DropdownSettingRow(
    label: String,
    sub: String,
    currentVal: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = BodyLarge.copy(fontWeight = FontWeight.Bold), color = if (enabled) TextPrimary else TextSecondary)
            Text(text = sub, style = BodyMedium, color = TextSecondary)
        }
        Box {
            Text(
                text = "$currentVal ▼",
                style = TechnicalSmall.copy(fontWeight = FontWeight.Bold, color = if (enabled) AmberGold else TextSecondary)
            )
            if (enabled) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(BackgroundSurface).border(1.dp, BorderDefault)
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = TextPrimary) },
                            onClick = {
                                onSelect(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
