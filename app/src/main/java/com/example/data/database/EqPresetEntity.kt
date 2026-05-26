package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "eq_presets")
data class EqPresetEntity(
    @PrimaryKey val name: String,
    val bandGains: String, // Comma-separated floats e.g., "0.0,0.0,3.5,-2.0,..."
    val preamp: Float = 0.0f
)
