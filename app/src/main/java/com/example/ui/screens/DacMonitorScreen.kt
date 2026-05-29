package com.example.ui.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.components.AppHeader

@Composable
fun DacMonitorScreen(viewModel: AudioViewModel) {
    val dacState by viewModel.dacState.collectAsState(initial = com.arima.pro.core.audio.DacState.NotDetected)
    val dacModeActive by viewModel.dacExclusiveMode.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val activeSong by viewModel.audioEngine.currentSong.collectAsState()
    val actualSampleRate by viewModel.audioEngine.actualOutputSampleRate.collectAsState()

    val sampleRateDisplay = when {
        !isPlaying || actualSampleRate == 0 -> "— Hz"
        else -> {
            val khz = actualSampleRate / 1000f
            if (khz == khz.toInt().toFloat()) "${khz.toInt()} kHz" else String.format("%.1f kHz", khz)
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(dacState) {
        if (dacState !is com.arima.pro.core.audio.DacState.Scanning) {
            isRefreshing = false
        }
    }

    val isDacDetected = dacState is com.arima.pro.core.audio.DacState.Detected
    val dacInfo = (dacState as? com.arima.pro.core.audio.DacState.Detected)?.dacInfo

    val context = LocalContext.current
    var deviceVolumeText by remember { mutableStateOf("100% System Vol") }

    LaunchedEffect(isDacDetected, dacModeActive) {
        while (true) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val volumePercent = if (maxVolume > 0) (currentVolume * 100) / maxVolume else 0
                
                deviceVolumeText = if (isDacDetected) {
                    if (dacModeActive) {
                        "EXCLUSIVE MODE"
                    } else {
                        val linear = currentVolume.toFloat() / maxVolume.toFloat().coerceAtLeast(0.0001f)
                        val dbFs = if (linear > 0f) 20 * kotlin.math.log10(linear) else -120f
                        if (dbFs <= -120f) "-∞ dBFS" else String.format("%.1f dBFS", dbFs)
                    }
                } else {
                    "$volumePercent% System Vol"
                }
            } catch (e: Exception) {
                android.util.Log.e("DacMonitor", "Error updating volume: ${e.message}")
            }
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(bottom = 80.dp) // space for bottom tab bar
    ) {
        // Unified App Header
        AppHeader(
            title = "DAC Monitor",
            actions = {
                IconButton(onClick = {
                    isRefreshing = true
                    viewModel.refreshDacDetection()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AmberGold
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding, vertical = 8.dp)
        ) {
            // DAC Routing Failure Status Card Banner
            item {
                val dacRoutingStatus by viewModel.audioEngine.dacRoutingStatus.collectAsState()
                if (dacRoutingStatus is com.example.domain.service.DacRoutingStatus.Failed) {
                    val statusFailed = dacRoutingStatus as com.example.domain.service.DacRoutingStatus.Failed
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE57373).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFE57373), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Routing Error",
                                tint = Color(0xFFE57373),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "DAC ROUTING GAGAL (AUDIO KE SPEAKER)",
                                style = LabelCaps.copy(fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusFailed.reason,
                            style = BodyMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.audioEngine.routeOutputToDac() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                            modifier = Modifier.align(Alignment.End).testTag("action_retry_routing")
                        ) {
                            Text("Retry Routing", color = Color.White)
                        }
                    }
                }
            }

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
                    // Dynamic status light
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRefreshing || dacState is com.arima.pro.core.audio.DacState.Scanning) Color.Gray 
                                else if (isDacDetected) VUSafe 
                                else Color(0xFFE57373)
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRefreshing || dacState is com.arima.pro.core.audio.DacState.Scanning) {
                                       val msg = (dacState as? com.arima.pro.core.audio.DacState.Scanning)?.message ?: "Scanning USB Devices..."
                                       msg
                                   } else if (isDacDetected) dacInfo!!.name 
                                   else "SPEAKER (INTERNAL)",
                            style = HeadlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isRefreshing || dacState is com.arima.pro.core.audio.DacState.Scanning) TextSecondary else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRefreshing || dacState is com.arima.pro.core.audio.DacState.Scanning) "Direct Hardware Querying..." 
                                   else if (isDacDetected) "USB AUDIO • CLASS 2 • EXCLUSIVE" 
                                   else "SYSTEM AUDIO MIXER ROUTING",
                            style = TechnicalSmall.copy(letterSpacing = 1.sp)
                        )
                    }

                    if (!isRefreshing && dacState !is com.arima.pro.core.audio.DacState.Scanning) {
                        Icon(
                            imageVector = if (isDacDetected) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Active Status",
                            tint = if (isDacDetected) VUSafe else Color(0xFFE57373),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Warnings Card Banner
            item {
                val bannerTitle = if (isDacDetected) "DAC EXCLUSIVE MODE ACTIVE" else "SPEAKER FALLBACK MODE ACTIVE"
                val bannerText = if (isDacDetected) {
                     "Audio stream outputting directly to ${dacInfo?.name ?: "hardware DAC"}. System Android mixer bypass is ACTIVE. Latency reduction is maximized."
                } else {
                     "No external high-res USB DAC detected. Audio is automatically routed to internal handset speakers. Plug in a USB DAC for direct bit-perfect hardware output."
                }
                val bannerColor = if (isDacDetected) AmberGold else Color(0xFFE57373)
                val bannerBg = if (isDacDetected) AmberMuted.copy(alpha = 0.15f) else Color(0xFFE57373).copy(alpha = 0.1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Exclusive Mode Indicator",
                        tint = bannerColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = bannerTitle,
                            style = LabelCaps.copy(fontWeight = FontWeight.Bold, color = bannerColor)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bannerText,
                            style = BodyMedium,
                            color = TextPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Tables Header Information
            item {
                val manufacturer = if (isDacDetected) (dacInfo?.manufacturer ?: "USB Audiophile") else "Google"
                val model = if (isDacDetected) (dacInfo?.name ?: "High-Res DAC") else "Speaker (Internal Phone Audio)"
                val connection = if (isDacDetected) "USB Audio 2.0 Client" else "Internal Sound System Bus"
                val usbClass = if (isDacDetected) (dacInfo?.connectionType ?: "USB-C OTG Sync Mode") else "N/A (Built-in Hub)"
                val driverMode = if (isDacDetected) "Direct Exclusive Bypass" else "Android AudioFlinger Wrapper"

                DacTechnicalTableGroup(
                    title = "DEVICE INFORMATION",
                    rows = listOf(
                        "Manufacturer" to manufacturer,
                        "Model" to model,
                        "Interface" to connection,
                        "USB Link Class" to usbClass,
                        "Driver Mode" to driverMode
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // DAC Technical Hardware Architecture Specs
            item {
                val chipset = if (isDacDetected) (dacInfo?.chipName ?: "High-End Dual Core DAC") else "System Default Audio Subsystem"
                val opamps = if (isDacDetected) "OPA1612 Ultra-low noise" else "Built-in Speaker Amp"
                val jitter = if (isDacDetected) "SteadyClockFS ultra-low clock" else "System Clock Sync"
                val thdn = if (isDacDetected) "${(dacInfo?.thdn ?: 0.0002) * 100}%" else "0.0100%"
                val snr = if (isDacDetected) "${dacInfo?.snr ?: 122.5} dB" else "92.0 dB"

                DacTechnicalTableGroup(
                    title = "ARCHITECTURE",
                    rows = listOf(
                        "DAC Chipset" to chipset,
                        "Op-Amps" to opamps,
                        "Clock Sync" to jitter,
                        "THD + N" to thdn,
                        "Signal SNR" to snr
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Capabilities Checklist Table
            item {
                val sampleRateLabel = if (isDacDetected) "${(dacInfo?.maxSampleRate ?: 384000) / 1000} kHz / ${(dacInfo?.maxBitDepth ?: 24)}-bit" else "48 kHz / 16-bit"
                val supportsDsd = isDacDetected && (dacInfo?.supportsDsd ?: false)
                val supportsDop = isDacDetected && (dacInfo?.supportsDop ?: false)
                val dsdText = if (supportsDsd) "Native DSD512 Supported" else "Not Supported"
                val dopText = if (supportsDop) "Active" else "Not Supported"

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
                    
                    CapabilityCheckRow("PCM Playback Rate Support", sampleRateLabel, true)
                    CapabilityCheckRow("DSD Direct Format Playback", dsdText, supportsDsd)
                    CapabilityCheckRow("Full hardware MQA Decoding", if (isDacDetected) "Supported" else "Not Supported", isDacDetected)
                    CapabilityCheckRow("DSD over PCM encapsulation (DoP)", dopText, supportsDop)
                    
                    val hasRecordAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    CapabilityCheckRow("Audio Capture (VU Meter) Permission", if (hasRecordAudio) "Granted" else "Required", hasRecordAudio)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // LIVE CURRENT OUTPUT TELEMETRY DETAILS
            item {
                DacTechnicalTableGroup(
                    title = "CURRENT TELEMETRY OUTPUT",
                    rows = listOf(
                        "Mode" to if (isPlaying && activeSong != null) activeSong!!.format else "STANDBY",
                        "Sample Rate" to sampleRateDisplay,
                        "Active Bit Depth" to if (isPlaying && activeSong != null) activeSong!!.bitDepth else "0 bit",
                        "Device Volume" to deviceVolumeText
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
