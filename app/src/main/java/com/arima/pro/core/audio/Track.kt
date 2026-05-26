package com.arima.pro.core.audio

import android.net.Uri

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val format: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val size: Long
)
