package com.example.domain.model

data class EqPreset(
    val name: String,
    val bandGains: List<Float>, // Exact size should be 10 for 10-band equalizer
    val preamp: Float = 0.0f
)
