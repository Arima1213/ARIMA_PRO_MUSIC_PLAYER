package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.ui.viewmodel.AudioViewModel

@Composable
fun EqualizerPanel(viewModel: AudioViewModel, onDismiss: () -> Unit) {
    val enabled by viewModel.equalizerEnabled.collectAsState()
    val rawPreset by viewModel.currentPreset.collectAsState()
    val preamp by viewModel.preampGain.collectAsState()
    val bands by viewModel.bandGains.collectAsState()
    val bitPerfectMode by viewModel.bitPerfectMode.collectAsState()

    val frequencies = listOf("32Hz", "64Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
    val presets = listOf("FLAT", "ROCK", "JAZZ", "CLASSICAL", "POP")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000)) // rgba(0,0,0,0.7) backdrop overlay
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                radius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EQUALIZER",
                            style = HeadlineSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = AmberGold
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }
                    
                    if (bitPerfectMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .glassSurface(8.dp, borderColor = VUPeak.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked", tint = VUPeak, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "EQ locked during Bit-Perfect mode",
                                    style = TechnicalSmall.copy(color = VUPeak, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    // Power Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ENABLE EQUALIZER",
                            style = TechnicalSmall.copy(fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.toggleEqualizer(it) },
                            enabled = !bitPerfectMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AmberGold,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = Color(0x1AFFFFFF),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }

                    // Presets
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(presets) { _, preset ->
                            val isSelected = rawPreset.equals(preset, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) AmberGold else Color.Transparent)
                                    .border(1.dp, if (isSelected) AmberGold else Color(0x1AFFFFFF), CircleShape)
                                    .clickable(enabled = enabled && !bitPerfectMode) { viewModel.selectPreset(preset) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset,
                                    style = TechnicalSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    // 10-Band EQ
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        itemsIndexed(frequencies) { index, freq ->
                            val gainValue = bands.getOrElse(index) { 0.0f }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%+d", gainValue.toInt()),
                                    style = TechnicalSmall.copy(fontSize = 9.sp, color = if (enabled) AmberGold else TextSecondary)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .height(140.dp)
                                        .width(6.dp)
                                        .glassSurface(12.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(fraction = ((gainValue + 12f) / 24f).coerceIn(0f, 1f))
                                            .background(if (enabled) AmberGold else TextSecondary)
                                    )
                                }
                                
                                // Slider Overlay
                                Slider(
                                    value = gainValue,
                                    onValueChange = { viewModel.setBandGain(index, it) },
                                    valueRange = -12f..12f,
                                    enabled = enabled && !bitPerfectMode,
                                    modifier = Modifier
                                        .width(140.dp)
                                        .padding(horizontal = 0.dp)
                                        .graphicsLayer {
                                            rotationZ = -90f
                                            translationX = -140f / 2 + 10f
                                            translationY = -140f / 2 + 10f
                                        }
                                        .testTag("eq_band_slider_$freq"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Transparent,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent,
                                        disabledThumbColor = Color.Transparent,
                                        disabledActiveTrackColor = Color.Transparent,
                                        disabledInactiveTrackColor = Color.Transparent
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = freq,
                                    style = TechnicalSmall.copy(fontSize = 10.sp, color = TextSecondary)
                                )
                            }
                        }
                    }

                    // Preamp
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PREAMP",
                            style = TechnicalSmall.copy(fontSize = 11.sp, color = TextSecondary),
                            modifier = Modifier.width(60.dp)
                        )
                        Slider(
                            value = preamp,
                            onValueChange = { viewModel.setPreamp(it) },
                            valueRange = -12f..12f,
                            enabled = enabled && !bitPerfectMode,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberGold,
                                activeTrackColor = AmberGold,
                                inactiveTrackColor = Color(0x1AFFFFFF),
                                disabledThumbColor = TextMuted,
                                disabledActiveTrackColor = TextMuted,
                                disabledInactiveTrackColor = Color(0x0AFFFFFF)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%+d dB", preamp.toInt()),
                            style = TechnicalSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enabled) AmberGold else TextSecondary),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(50.dp)
                        )
                    }

                    // Toggles (Bass Boost & Virtualizer Placeholders for UI as requested)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Bass Boost", style = TechnicalSmall.copy(fontSize = 12.sp, color = TextPrimary))
                            Switch(
                                checked = false, onCheckedChange = { }, enabled = false,
                                colors = SwitchDefaults.colors(uncheckedTrackColor = Color(0x1AFFFFFF), uncheckedThumbColor = TextSecondary)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Virtualizer", style = TechnicalSmall.copy(fontSize = 12.sp, color = TextPrimary))
                            Switch(
                                checked = false, onCheckedChange = { }, enabled = false,
                                colors = SwitchDefaults.colors(uncheckedTrackColor = Color(0x1AFFFFFF), uncheckedThumbColor = TextSecondary)
                            )
                        }
                    }

                    // Footer Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.resetEqualizer() }) {
                            Text(text = "RESET", color = TextSecondary, style = TechnicalSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "APPLY", color = Color.Black, style = TechnicalSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

