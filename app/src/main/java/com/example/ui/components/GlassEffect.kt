package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BackgroundSurface

fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(20.dp),
    surfaceColor: Color = BackgroundSurface,
    borderColor: Color = BorderSubtle,
    borderWidth: Dp = 1.dp
) = composed {
    this
        .clip(shape)
        .background(surfaceColor)
        .border(borderWidth, borderColor, shape)
}
