package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Colors
val GlassBackgroundPrimary = Color(0xFF0D0D0D)
val GlassSurfaceColor = Color(0xFF141414)
val GlassAmberGold = Color(0xFFC8982A)
val GlassTextPrimary = Color(0xFFF0EDE6)
val GlassTextSecondary = Color(0xFF8A8078)
val GlassBorderColor = Color(0x19FFFFFF) // rgba(255,255,255,0.10)

fun Modifier.customShadow(
    color: Color = Color(0x66000000), // rgba(0,0,0,0.4)
    radius: Dp = 16.dp,
    blurRadius: Dp = 20.dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp
) = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = android.graphics.Color.TRANSPARENT
        frameworkPaint.setShadowLayer(
            blurRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            color.toArgb()
        )
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            radiusX = radius.toPx(),
            radiusY = radius.toPx(),
            paint = paint
        )
    }
}

fun Modifier.glassSurface(
    radius: Dp = 16.dp,
    borderColor: Color = GlassBorderColor
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .graphicsLayer {
            clip = true
            this.shape = shape
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                    20f,
                    20f,
                    android.graphics.Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
        }
        .background(Color(0x0FFFFFFF)) // rgba(255, 255, 255, 0.06)
        .background(
            brush = Brush.verticalGradient(
                0.0f to Color(0x1EFFFFFF), // rgba(255, 255, 255, 0.12)
                0.6f to Color.Transparent
            )
        )
        .border(1.dp, borderColor, shape)
        .clip(shape)
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.glassSurface(radius),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .customShadow(
                color = Color(0x66000000), // shadow level-1 (rgba(0,0,0,0.4))
                radius = radius,
                blurRadius = 20.dp,
                offsetY = 4.dp,
                offsetX = 0.dp
            )
            .glassSurface(radius),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .glassSurface(radius = radius, borderColor = GlassAmberGold)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = contentAlignment,
        content = content
    )
}

