package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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
    val isFavorite: Boolean = false
)
