package com.arima.pro.core.audio

data class ScanProgress(
    val step: String,
    val filesScanned: Int,
    val filesTotal: Int,
    val tracksFound: Int,
    val progress: Float
)
