package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val path: String,
    val fileCount: Int,
    val totalSize: String,
    val lastScan: String
)
