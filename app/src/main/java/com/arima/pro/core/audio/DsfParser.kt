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
    
    fun parse(context: Context, uri: Uri): AudioFormat {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(128)
                // Need to detect if it's DSF or DFF
                if (stream.read(buffer, 0, 4) != 4) return AudioFormat.UNKNOWN
                val magic = String(buffer, 0, 4)
                
                if (magic == "DSD ") {
                    // DSF File format
                    // Re-read or skip since we consumed 4 bytes
                    // Standard DSF: first 28 bytes are "DSD " chunk. 24 bytes more.
                    if (stream.read(buffer, 4, 24) != 24) return AudioFormat.UNKNOWN
                    
                    // Read fmt chunk (52 bytes)
                    if (stream.read(buffer, 0, 52) != 52) return AudioFormat.UNKNOWN
                    val magicFmt = String(buffer, 0, 4)
                    if (magicFmt != "fmt ") return AudioFormat.UNKNOWN
                    
                    // Extract channels and rate
                    channelCount = ByteBuffer.wrap(buffer, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(1, 8)
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
                            return AudioFormat.DSD
                        } else {
                            val bytesToSkip = chunkSize - 12L
                            if (bytesToSkip > 0) {
                                stream.skip(bytesToSkip)
                            }
                            currentSeek += chunkSize
                        }
                    }
                } else if (magic == "FRM8") {
                    // DFF (DSDIFF) File format
                    // First chunk is "FRM8" (DSD chunk). We already read 4 bytes.
                    // FRM8 has 8 bytes size (Big Endian), followed by 4 bytes form type: "DSD "
                    val extraHeader = ByteArray(12)
                    if (stream.read(extraHeader, 0, 12) != 12) return AudioFormat.UNKNOWN
                    val formType = String(extraHeader, 8, 4)
                    if (formType != "DSD ") return AudioFormat.UNKNOWN
                    
                    // Next chunk is "fmt " chunk. Let's read 12 bytes header: 4 chunkId + 8 size (Big Endian)
                    val fmtHeader = ByteArray(12)
                    if (stream.read(fmtHeader, 0, 12) != 12) return AudioFormat.UNKNOWN
                    val chunkId = String(fmtHeader, 0, 4)
                    if (chunkId != "fmt ") return AudioFormat.UNKNOWN
                    val fmtSize = ByteBuffer.wrap(fmtHeader, 4, 8).order(ByteOrder.BIG_ENDIAN).long
                    if (fmtSize <= 0 || fmtSize > 8192) return AudioFormat.UNKNOWN
                    
                    val fmtPayload = ByteArray(fmtSize.toInt())
                    var totalFmtRead = 0
                    while (totalFmtRead < fmtPayload.size) {
                        val rd = stream.read(fmtPayload, totalFmtRead, fmtPayload.size - totalFmtRead)
                        if (rd == -1) break
                        totalFmtRead += rd
                    }
                    if (totalFmtRead != fmtPayload.size) return AudioFormat.UNKNOWN
                    
                    // Parse sampleRate and channelCount from "fmt " chunk
                    // Auto-detection logic looking for standard DSD rates
                    var detectedRate = 2822400
                    var detectedChannels = 2
                    
                    // Look through standard DSD rates in fmtPayload
                    val possibleOffsets = listOf(28, 4, 0, 8, 12, 16, 20, 24)
                    for (offset in possibleOffsets) {
                        if (offset + 4 <= fmtPayload.size) {
                            val rateBE = ByteBuffer.wrap(fmtPayload, offset, 4).order(ByteOrder.BIG_ENDIAN).int
                            if (isDsdRate(rateBE)) {
                                detectedRate = rateBE
                                break
                            }
                            val rateLE = ByteBuffer.wrap(fmtPayload, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
                            if (isDsdRate(rateLE)) {
                                detectedRate = rateLE
                                break
                            }
                        }
                    }
                    
                    // Auto-detect channels
                    if (fmtPayload.size >= 2) {
                        val chanBE = ByteBuffer.wrap(fmtPayload, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
                        if (chanBE in 1..8) {
                            detectedChannels = chanBE
                        } else if (fmtPayload.size >= 4) {
                            val chanBEInt = ByteBuffer.wrap(fmtPayload, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                            if (chanBEInt in 1..8) {
                                detectedChannels = chanBEInt
                            }
                        }
                    }
                    
                    channelCount = detectedChannels
                    sampleRate = detectedRate
                    
                    // Next chunk is "data" chunk (or in DFF "DSD " is the standard audio data chunk)
                    val dataHeader = ByteArray(12)
                    if (stream.read(dataHeader, 0, 12) != 12) return AudioFormat.UNKNOWN
                    val dataChunkId = String(dataHeader, 0, 4)
                    // The chunk ID can be "DSD " or "data"
                    val dataChunkSize = ByteBuffer.wrap(dataHeader, 4, 8).order(ByteOrder.BIG_ENDIAN).long
                    
                    dataStartOffset = 16L + 12L + fmtSize + 12L
                    dataSize = dataChunkSize
                    
                    return AudioFormat.DSD
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return AudioFormat.UNKNOWN
    }
    
    private fun isDsdRate(rate: Int): Boolean {
        return rate == 2822400 || rate == 5644800 || rate == 11289600 || rate == 22579200
    }
}
