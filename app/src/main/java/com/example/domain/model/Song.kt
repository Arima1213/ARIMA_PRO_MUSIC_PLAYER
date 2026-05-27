package com.example.domain.model

data class Song(
    val id: Int = 0,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val format: String, // WAV, FLAC, DSD, MP3 etc.
    val sampleRate: String,
    val bitDepth: String,
    val fileSize: String,
    val path: String,
    val folderPath: String,
    val isFavorite: Boolean = false,
    val albumArt: ByteArray? = null
) {
    val durationText: String
        get() {
            val minutes = (duration / 1000) / 60
            val seconds = (duration / 1000) % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
