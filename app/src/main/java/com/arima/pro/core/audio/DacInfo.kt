package com.arima.pro.core.audio

data class DacInfo(
    val name: String,
    val manufacturer: String,
    val vendorId: Int,
    val productId: Int,
    val chipName: String?,
    val maxSampleRate: Int,
    val maxBitDepth: Int,
    val supportsDsd: Boolean,
    val supportsDop: Boolean,
    val thdn: Double?,
    val snr: Double?,
    val connectionType: String
)
