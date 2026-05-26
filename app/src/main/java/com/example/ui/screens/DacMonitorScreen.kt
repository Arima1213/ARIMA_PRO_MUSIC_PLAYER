package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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

@Composable
fun DacMonitorScreen(viewModel: AudioViewModel) {
    val dacModeActive by viewModel.dacExclusiveMode.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val activeSong by viewModel.audioEngine.currentSong.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            kotlinx.coroutines.delay(1000)
            isRefreshing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .statusBarsPadding()
            .padding(bottom = 80.dp) // space for bottom tab bar
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.selectTab("library") }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "DAC MONITOR",
                style = TechnicalLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                color = TextSecondary
            )
            IconButton(onClick = { isRefreshing = true }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = AmberGold
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Active DAC Connected Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Solid green indicator pill
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isRefreshing) Color.Gray else VUSafe)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRefreshing) "Scanning USB Devices..." else "RME ADI-2 DAC fs",
                            style = HeadlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isRefreshing) TextSecondary else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRefreshing) "Direct Hardware Querying..." else "USB AUDIO • CLASS 2",
                            style = TechnicalSmall.copy(letterSpacing = 1.sp)
                        )
                    }

                    if (!isRefreshing) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = VUSafe,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Warnings Card Banner
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmberMuted.copy(alpha = 0.15f))
                        .border(1.dp, AmberGold.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Exclusive Mode Indicator",
                        tint = AmberGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DAC EXCLUSIVE MODE ACTIVE",
                            style = LabelCaps.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Audio stream outputting directly to RME hardware. System Android mixer bypass is ACTIVE. Latency reduction is maximized.",
                            style = BodyMedium,
                            color = TextPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Tables Header Information
            item {
                DacTechnicalTableGroup(
                    title = "DEVICE INFORMATION",
                    rows = listOf(
                        "Manufacturer" to "RME",
                        "Model" to "ADI-2 DAC",
                        "Interface" to "USB Audio Client",
                        "USB Class" to "2.0 (High Speed)",
                        "Driver Mode" to "Direct Kernel PCM"
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // DAC Technical Hardware Architecture Specs
            item {
                DacTechnicalTableGroup(
                    title = "ARCHITECTURE",
                    rows = listOf(
                        "DAC Chipset" to "Dual AKM AK4493EQ",
                        "Op-Amps" to "OPA1612 Ultra-low noise",
                        "Clock Jitter" to "SteadyClock FS (< 1ps)",
                        "THD + N" to "-120 dB (0.0001%)",
                        "Signal SNR" to "124 dB"
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Capabilities Checklist Table
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "HARDWARE CAPABILITIES",
                        style = LabelCaps.copy(color = TextSecondary, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    CapabilityCheckRow("PCM Playback Rate Support", "768 kHz / 32-bit", true)
                    CapabilityCheckRow("DSD Direct Format Playback", "DSD512 Direct", true)
                    CapabilityCheckRow("Full hardware MQA Decoding", "Supported", true)
                    CapabilityCheckRow("DSD over PCM encapsulation (DoP)", "DoP256 Output", true)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // LIVE CURRENT OUTPUT TELEMETRY DETAILS
            item {
                DacTechnicalTableGroup(
                    title = "CURRENT TELEMETRY OUTPUT",
                    rows = listOf(
                        "Mode" to if (isPlaying && activeSong != null) activeSong!!.format else "STANDBY",
                        "Sample Rate" to if (isPlaying && activeSong != null) "${activeSong!!.sampleRate} Real-time" else "0 Hz",
                        "Active Bit Depth" to if (isPlaying && activeSong != null) activeSong!!.bitDepth else "0 bit",
                        "Device Volume" to "-6.0 dBFS"
                    ),
                    highlightValueColor = true
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun DacTechnicalTableGroup(
    title: String,
    rows: List<Pair<String, String>>,
    highlightValueColor: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BackgroundSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = LabelCaps.copy(color = TextSecondary, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        rows.forEachIndexed { index, (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = key,
                    style = BodyMedium.copy(color = TextSecondary)
                )
                Text(
                    text = value,
                    style = TechnicalSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (highlightValueColor) AmberGold else TextPrimary
                    )
                )
            }
            if (index < rows.size - 1) {
                Divider(color = BorderSubtle, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun CapabilityCheckRow(label: String, value: String, isSupported: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = BodyMedium.copy(color = TextPrimary.copy(alpha = 0.9f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = TechnicalSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSupported) FlacTeal else VUPeak
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (isSupported) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isSupported) FlacTeal else VUPeak,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
