package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arima.pro.core.audio.VuLevels
import kotlin.math.max

@Composable
fun VuMeterComponent(
    leftDb: Float,    // -60f to 0f
    rightDb: Float,   // -60f to 0f
    peakL: Float,
    peakR: Float,
    modifier: Modifier = Modifier
) {
    // 1. Smooth falloff logic with lerp (0.3f) for analog ballistics
    var currentLeftDb by remember { mutableStateOf(-60f) }
    var currentRightDb by remember { mutableStateOf(-60f) }

    LaunchedEffect(leftDb) {
        currentLeftDb = if (leftDb > currentLeftDb) {
            leftDb // instant attack
        } else {
            currentLeftDb + (leftDb - currentLeftDb) * 0.3f // smooth decay
        }
    }

    LaunchedEffect(rightDb) {
        currentRightDb = if (rightDb > currentRightDb) {
            rightDb // instant attack
        } else {
            currentRightDb + (rightDb - currentRightDb) * 0.3f // smooth decay
        }
    }

    // 2. Twin-smooth animation over 50ms
    val animatedLeftDb by animateFloatAsState(
        targetValue = currentLeftDb,
        animationSpec = tween(durationMillis = 50, easing = LinearEasing),
        label = "animatedLeftDb"
    )

    val animatedRightDb by animateFloatAsState(
        targetValue = currentRightDb,
        animationSpec = tween(durationMillis = 50, easing = LinearEasing),
        label = "animatedRightDb"
    )

    // Normalize dB values to segment counts (0..30)
    fun dbToSegmentCount(db: Float): Int {
        if (db.isNaN() || db.isInfinite()) return 0
        val normalized = ((db + 60f) / 60f).coerceIn(0f, 1f)
        return (normalized * 30).toInt().coerceIn(0, 30)
    }

    val activeSegmentsL = dbToSegmentCount(animatedLeftDb)
    val activeSegmentsR = dbToSegmentCount(animatedRightDb)

    // Peak holds: Use provided ones or fallback to calculated ones
    val peakSegmentL = dbToSegmentCount(max(peakL, currentLeftDb))
    val peakSegmentR = dbToSegmentCount(max(peakR, currentRightDb))

    // Responsive Box with Constraints
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141414), shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        val containerWidth = maxWidth

        // Sizing thresholds:
        // Small: <360dp -> barWidth = 140dp
        // Medium: 360dp - 420dp -> barWidth = 180dp
        // Large: >420dp -> max screen/box width split symmetrically
        val barWidth = when {
            containerWidth < 360.dp -> 140.dp
            containerWidth < 420.dp -> 180.dp
            else -> (containerWidth - 24.dp - 32.dp) / 2 // Dynamic split with 24.dp spacing
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT CHANNEL COLUMN
            VuChannelColumn(
                label = "L",
                dbValue = leftDb,
                activeSegments = activeSegmentsL,
                peakSegment = peakSegmentL,
                barWidth = barWidth
            )

            // RIGHT CHANNEL COLUMN
            VuChannelColumn(
                label = "R",
                dbValue = rightDb,
                activeSegments = activeSegmentsR,
                peakSegment = peakSegmentR,
                barWidth = barWidth
            )
        }
    }
}

@Composable
private fun VuChannelColumn(
    label: String,
    dbValue: Float,
    activeSegments: Int,
    peakSegment: Int,
    barWidth: Dp
) {
    Column(
        modifier = Modifier.width(barWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label & value row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
            val dbText = if (dbValue <= -60f) "-∞ dB" else "${String.format(java.util.Locale.US, "%.1f", dbValue)} dB"
            Text(
                text = dbText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFFAAAAAA)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // LED segment drawing canvas
        Canvas(
            modifier = Modifier
                .width(barWidth)
                .height(18.dp) // extra space for little tick indicators
        ) {
            val totalSpacingPx = 29 * 2.dp.toPx()
            val segmentWidthPx = kotlin.math.max(0f, (size.width - totalSpacingPx) / 30f)
            val segmentHeightPx = 12.dp.toPx()

            // Draw segments
            for (i in 0 until 30) {
                val left = i * (segmentWidthPx + 2.dp.toPx())
                val segmentColor = when (i) {
                    in 0..19 -> Color(0xFF2ECC71) // Hijau (0-20)
                    in 20..24 -> Color(0xFFF39C12) // Kuning (21-25)
                    else -> Color(0xFFE74C3C) // Merah (26-30)
                }

                val isActive = i < activeSegments
                val isPeak = i == (peakSegment - 1)

                val finalColor = when {
                    isPeak -> segmentColor
                    isActive -> segmentColor
                    else -> segmentColor.copy(alpha = 0.15f) // dim background segments
                }

                if (segmentWidthPx > 0f) {
                    drawRoundRect(
                        color = finalColor,
                        topLeft = Offset(left, 0f),
                        size = Size(segmentWidthPx, segmentHeightPx),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }

            // Draw ticks below the segments inside the same canvas
            val ticks = listOf(0f, -3f, -6f, -12f, -24f)
            for (tickDb in ticks) {
                val normalizedPosition = ((tickDb + 60f) / 60f).coerceIn(0f, 1f)
                val tickXPx = normalizedPosition * size.width
                drawLine(
                    color = Color(0x44FFFFFF),
                    start = Offset(tickXPx, 14.dp.toPx()),
                    end = Offset(tickXPx, 18.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // dB scale markers below
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(14.dp)
        ) {
            val dbs = listOf(
                -24 to 0.60f,
                -12 to 0.80f,
                -6 to 0.90f,
                -3 to 0.95f,
                0 to 1.00f
            )
            dbs.forEach { (dbVal, positionPart) ->
                // Account for label width to center under its tick position
                val labelOffset = barWidth * positionPart
                Text(
                    text = if (dbVal == 0) "0" else "$dbVal",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0x66FFFFFF),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = labelOffset - 8.dp)
                )
            }
        }
    }
}
