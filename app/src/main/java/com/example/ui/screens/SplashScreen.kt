package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashCompleted: () -> Unit) {
    // Elegant fade-out trigger
    var startFadeProgress by remember { mutableStateOf(false) }

    val alphaAnimation by animateFloatAsState(
        targetValue = if (startFadeProgress) 0f else 1f,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "SplashFade"
    )

    // Gold dial angle pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "SplashRing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    val sweepAnglePhasing by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Sweep"
    )

    LaunchedEffect(Unit) {
        delay(2200)
        startFadeProgress = true
        delay(800)
        onSplashCompleted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 40.dp)
        ) {
            // Analog Waveform Ring Logo Drawn on Canvas
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sizePx = size.width
                    val center = sizePx / 2
                    val radius = (sizePx / 2) * 0.85f * pulseScale

                    // Draw thin golden audiophile outer ring
                    drawCircle(
                        color = AmberGold,
                        radius = radius,
                        style = Stroke(width = 1.8.dp.toPx())
                    )

                    // Draw thin sweeping indicator line representing an analog scan
                    val length = radius
                    val rad = Math.toRadians(sweepAnglePhasing.toDouble())
                    val endX = center + length * Math.cos(rad).toFloat()
                    val endY = center + length * Math.sin(rad).toFloat()
                    
                    drawLine(
                        color = AmberMuted.copy(alpha = 0.35f),
                        start = androidx.compose.ui.geometry.Offset(center, center),
                        end = androidx.compose.ui.geometry.Offset(endX, endY),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw the primary and secondary custom sine waves
                    val wavePath1 = Path()
                    val wavePath2 = Path()

                    val waveWidth = radius * 1.5f
                    val startX = center - waveWidth / 2
                    val endXWave = center + waveWidth / 2

                    var x = startX
                    var isFirst1 = true
                    var isFirst2 = true

                    while (x <= endXWave) {
                        val progress = (x - startX) / waveWidth
                        // Sine wave math
                        val yOffset1 = Math.sin(progress * Math.PI * 3.5).toFloat() * 14.dp.toPx()
                        val yOffset2 = Math.cos(progress * Math.PI * 3.5).toFloat() * 11.dp.toPx()

                        // Smooth windowing to taper the waves at edges
                        val window = Math.sin(progress * Math.PI).toFloat()
                        val finalY1 = center + yOffset1 * window
                        val finalY2 = center + yOffset2 * window

                        if (isFirst1) {
                            wavePath1.moveTo(x, finalY1)
                            isFirst1 = false
                        } else {
                            wavePath1.lineTo(x, finalY1)
                        }

                        if (isFirst2) {
                            wavePath2.moveTo(x, finalY2)
                            isFirst2 = false
                        } else {
                            wavePath2.lineTo(x, finalY2)
                        }

                        x += 1f
                    }

                    // Secondary phase-shifted wave
                    drawPath(
                        path = wavePath2,
                        color = AmberMuted,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Primary wave
                    drawPath(
                        path = wavePath1,
                        color = AmberGlow,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Serif Title Text
            Text(
                text = "ARIMA",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                ),
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Technical subtitle
            Text(
                text = "PRO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = AmberGold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Decorative separator divider
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                AmberMuted.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "AUDIOPHILE MUSIC PLAYER",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 3.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light,
                    color = TextSecondary.copy(alpha = 0.8f)
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
