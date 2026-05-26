package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Centralized high-end audiophile typographies
val DisplayLarge = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 38.sp, // Adjusted slightly for safety on narrower mobile screens
    fontWeight = FontWeight.Bold,
    lineHeight = 44.sp,
    color = TextPrimary
)

val DisplayMedium = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 28.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 36.sp,
    color = TextPrimary
)

val HeadlineSmall = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 22.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 28.sp,
    color = TextPrimary
)

val TechnicalLarge = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 22.sp,
    letterSpacing = 0.05.sp,
    color = TextPrimary
)

val TechnicalSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 16.sp,
    color = TextSecondary
)

val BodyLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 16.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 24.sp,
    color = TextPrimary
)

val BodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 20.sp,
    color = TextSecondary
)

val LabelCaps = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 12.sp,
    color = AmberGold
)

val Typography = Typography(
    displayLarge = DisplayLarge,
    displayMedium = DisplayMedium,
    headlineSmall = HeadlineSmall,
    bodyLarge = BodyLarge,
    bodyMedium = BodyMedium,
    labelSmall = LabelCaps
)
