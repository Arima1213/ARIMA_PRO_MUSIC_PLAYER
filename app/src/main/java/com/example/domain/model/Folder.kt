package com.example.domain.model

data class Folder(
    val path: String,
    val fileCount: Int,
    val totalSize: String,
    val lastScan: String
)
