package com.arima.pro.core.audio

data class VuLevels(
    val leftDb: Float,
    val rightDb: Float,
    val peakL: Float,
    val peakR: Float
)
