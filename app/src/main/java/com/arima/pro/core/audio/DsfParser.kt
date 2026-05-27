package com.arima.pro.core.audio

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DsfParser {
    var dataStartOffset: Long = 0
    var dataSize: Long = 0
    var channelCount: Int = 2
    var sampleRate: Int = 2822400 // DSD64 base
    
    fun parse(context: Context, uri: Uri): Boolean {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(128)
                // Read DSD chunk
                if (stream.read(buffer, 0, 28) != 28) return false
                val magicDsd = String(buffer, 0, 4)
                if (magicDsd != "DSD ") return false
                
                // Read fmt chunk
                if (stream.read(buffer, 0, 52) != 52) return false
                val magicFmt = String(buffer, 0, 4)
                if (magicFmt != "fmt ") return false
                
                // Extract channels and rate
                channelCount = ByteBuffer.wrap(buffer, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                sampleRate = ByteBuffer.wrap(buffer, 28, 4).order(ByteOrder.LITTLE_ENDIAN).int
                
                // Now read and skip chunks until we find "data" chunk
                var currentSeek = 28L + 52L
                while (true) {
                    val header = ByteArray(12)
                    val read = stream.read(header)
                    if (read < 12) break
                    val chunkId = String(header, 0, 4)
                    val chunkSize = ByteBuffer.wrap(header, 4, 8).order(ByteOrder.LITTLE_ENDIAN).long
                    if (chunkId == "data") {
                        dataStartOffset = currentSeek + 12L
                        dataSize = chunkSize - 12L
                        return true
                    } else {
                        val bytesToSkip = chunkSize - 12L
                        if (bytesToSkip > 0) {
                            stream.skip(bytesToSkip)
                        }
                        currentSeek += chunkSize
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
