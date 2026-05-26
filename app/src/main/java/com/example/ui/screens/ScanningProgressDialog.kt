package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

@Composable
fun ScanningProgressDialog(
    stepText: String,
    filesScanned: Int,
    filesTotal: Int,
    tracksFound: Int,
    progress: Float,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Dialog(onDismissRequest = {}) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BackgroundSurface)
                .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with spin icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scanning",
                        tint = AmberGold,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotation)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "SCANNING DIRECTORY",
                        style = HeadlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Current scanning path panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(BackgroundPrimary)
                        .border(0.5.dp, BorderSubtle, RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = stepText,
                        style = TechnicalSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Processing percentage row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROCESSING",
                        style = LabelCaps.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = String.format("%.0f%%", progress * 100f),
                        style = TechnicalSmall.copy(fontWeight = FontWeight.Black, color = AmberGold)
                    )
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AmberGold,
                    trackColor = BorderDefault
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Telemetry statistics columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "FILES SCANNED", style = LabelCaps.copy(fontSize = 9.sp, color = TextSecondary))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$filesScanned / $filesTotal",
                            style = TechnicalLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "TRACKS DECODED", style = LabelCaps.copy(fontSize = 9.sp, color = TextSecondary))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$tracksFound",
                            style = TechnicalLarge.copy(fontWeight = FontWeight.Bold, color = FlacTeal)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Alert issues banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF281111))
                        .border(0.5.dp, Color(0xFF5A1D1D), RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Issues",
                            tint = VUPeak,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "METADATA CORRUPTIONS",
                            style = LabelCaps.copy(color = VUPeak, fontSize = 9.sp)
                        )
                    }

                    Text(
                        text = "12 files skipped",
                        style = TechnicalSmall.copy(fontWeight = FontWeight.Bold, color = VUPeak)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Cancel Button
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, BorderDefault),
                    onClick = onCancel
                ) {
                    Text("ABORT SCANNING", fontWeight = FontWeight.Bold, style = TechnicalSmall)
                }
            }
        }
    }
}
