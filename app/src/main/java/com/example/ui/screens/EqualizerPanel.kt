package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.AudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerPanel(viewModel: AudioViewModel, onDismiss: () -> Unit) {
    val enabled by viewModel.equalizerEnabled.collectAsState()
    val bitPerfectMode by viewModel.bitPerfectMode.collectAsState()
    val eqBlocked by viewModel.eqBlockedByBitPerfect.collectAsState()
    val rawPreset by viewModel.currentPreset.collectAsState()
    val preamp by viewModel.preampGain.collectAsState()
    val bands by viewModel.bandGains.collectAsState()

    val frequencies = listOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderDefault) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HARDWARE EQUALIZER",
                    style = HeadlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bitPerfectMode) {
                        // Show lock badge instead of switch
                        Box(
                            modifier = Modifier
                                .background(VUPeak.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .border(1.dp, VUPeak, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = VUPeak,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LOCKED (Bit-Perfect Active)",
                                    style = TechnicalSmall.copy(color = VUPeak, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "ACTIVE",
                            style = LabelCaps.copy(color = if (enabled) FlacTeal else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.toggleEqualizer(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = AmberGold,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = BackgroundPrimary
                            )
                        )
                    }
                }
            }

            // Presets Selection row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("FLAT", "BASS BOOST", "VOCAL FOCUS", "TREBLE AIR").forEach { preset ->
                    val isSelected = rawPreset == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) AmberGold else BackgroundCard)
                            .border(1.dp, if (isSelected) AmberGold else BorderSubtle, RoundedCornerShape(4.dp))
                            .clickable(enabled = enabled) { viewModel.selectPreset(preset) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset,
                            style = TechnicalSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else if (enabled) TextPrimary else TextMuted
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DYNAMIC CANVAS GRAPH REPRESENTING FITTING BANDS PATH LINES
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BackgroundPrimary)
                    .border(1.dp, BorderDefault, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    // Draw grid line bars representing dB positions
                    val yZero = h / 2
                    val stepY = h / 24 // scale mapping -12 to 12
                    
                    // Draw preamp zero line helper
                    drawLine(
                        color = BorderDefault,
                        start = androidx.compose.ui.geometry.Offset(0f, yZero),
                        end = androidx.compose.ui.geometry.Offset(w, yZero),
                        strokeWidth = 1f
                    )

                    // Draw +6, -6 markings
                    drawLine(
                        color = BorderSubtle,
                        start = androidx.compose.ui.geometry.Offset(0f, yZero - stepY * 6),
                        end = androidx.compose.ui.geometry.Offset(w, yZero - stepY * 6),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = BorderSubtle,
                        start = androidx.compose.ui.geometry.Offset(0f, yZero + stepY * 6),
                        end = androidx.compose.ui.geometry.Offset(w, yZero + stepY * 6),
                        strokeWidth = 1f
                    )

                    // Trace point lines representing individual gains
                    val points = bands.mapIndexed { index, gain ->
                        val ratioX = index.toFloat() / (bands.size - 1)
                        val x = ratioX * w
                        
                        // Map gain (-12 to 12) to Y
                        val y = (yZero - gain * stepY).coerceIn(0f, h)
                        androidx.compose.ui.geometry.Offset(x, y)
                    }

                    // Paint clean connecting gold paths
                    val path = Path()
                    points.forEachIndexed { i, pt ->
                        if (i == 0) {
                            path.moveTo(pt.x, pt.y)
                        } else {
                            // cubic curves spline
                            val prevPt = points[i - 1]
                            path.cubicTo(
                                prevPt.x + (pt.x - prevPt.x) / 2, prevPt.y,
                                prevPt.x + (pt.x - prevPt.x) / 2, pt.y,
                                pt.x, pt.y
                            )
                        }
                    }

                    drawPath(
                        path = path,
                        color = if (enabled) AmberGold else Color.Gray.copy(alpha = 0.5f),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw node circles
                    points.forEach { pt ->
                        drawCircle(
                            color = if (enabled) AmberGlow else Color.Gray,
                            radius = 4.dp.toPx(),
                            center = pt
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Preamp Slider Controls
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("PREAMPLIFIER", style = LabelCaps.copy(color = TextSecondary))
                    Text(
                        text = String.format(java.util.Locale.US, "%+.1f dB", preamp),
                        style = TechnicalSmall.copy(fontWeight = FontWeight.Bold, color = AmberGold)
                    )
                }
                
                Slider(
                    value = preamp,
                    onValueChange = { viewModel.setPreamp(it) },
                    valueRange = -12.0f..12.0f,
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = AmberGold,
                        activeTrackColor = AmberGold,
                        inactiveTrackColor = BorderDefault
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal Frequencies Sliders Row
            Text(
                text = "10 FREQUENCY CHANNEL EQUALIZERS (dB)",
                style = LabelCaps.copy(color = TextSecondary, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(frequencies) { index, freq ->
                    val gainValue = bands.getOrElse(index) { 0.0f }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(44.dp)
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%+.1f", gainValue),
                            style = TechnicalSmall.copy(fontSize = 10.sp, color = if (enabled) AmberGlow else TextMuted)
                        )
                        
                        Box(
                            modifier = Modifier
                                .height(120.dp)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Vertical slider emulation
                            Slider(
                                value = gainValue,
                                onValueChange = { viewModel.setBandGain(index, it) },
                                valueRange = -12.0f..12.0f,
                                enabled = enabled,
                                modifier = Modifier
                                    .width(120.dp)
                                    .align(Alignment.Center)
                                    .testTag("eq_band_slider_$freq"),
                                colors = SliderDefaults.colors(
                                    thumbColor = AmberGold,
                                    activeTrackColor = AmberGold,
                                    inactiveTrackColor = BorderDefault
                                )
                            )
                        }
                        
                        Text(
                            text = freq,
                            style = TechnicalSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons (Reset All, Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BackgroundCard),
                    onClick = { viewModel.resetEqualizer() },
                    enabled = enabled
                ) {
                    Text("RESET ALL", color = if (enabled) TextPrimary else TextMuted, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                    onClick = onDismiss
                ) {
                    Text("APPLY FILTERS", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
