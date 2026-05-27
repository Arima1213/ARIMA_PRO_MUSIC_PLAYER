package com.arima.pro.core.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DsdDataSource(private val context: Context, private val useDoP: Boolean) : DataSource {

    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var parser: DsfParser = DsfParser()
    
    // Playback state
    private var isPlayingDsd = false
    private var currentPosition = 0L
    private var dsdDataLeftBytes = 0L
    
    // Buffer for block reads
    private var blockBufferL = ByteArray(4096)
    private var blockBufferR = ByteArray(4096)
    private var blockBufferIndex = 0
    private var currentBlockSize = 0
    
    // Decimation / decoding states
    private var movingAvgWindow = IntArray(64)
    private var windowIndex = 0
    private var dopMarker = 0x05.toByte()

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        // Optional
    }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        isPlayingDsd = false
        val uriStr = dataSpec.uri.toString()

        if (uriStr.endsWith(".dsf", ignoreCase = true) || uriStr.endsWith(".dff", ignoreCase = true)) {
            if (parser.parse(context, dataSpec.uri)) {
                isPlayingDsd = true
                dsdDataLeftBytes = parser.dataSize
                currentPosition = 0L
                blockBufferIndex = 0
                currentBlockSize = 0
                windowIndex = 0
                dopMarker = 0x05.toByte()
                movingAvgWindow.fill(0)
                
                // Open real stream and skip to data chunk start offset
                val stream = context.contentResolver.openInputStream(dataSpec.uri)
                if (stream != null) {
                    stream.skip(parser.dataStartOffset)
                    inputStream = stream
                    
                    // Return simulated PCM layout: 176.4kHz, 2 channels, 16-bit (2 bytes) or 24-bit (3 bytes)
                    val sampleRate = 176400
                    val channels = 2
                    val bytesPerSample = if (useDoP) 3 else 2
                    val totalDurationSec = parser.dataSize / (parser.sampleRate / 8 * parser.channelCount).toFloat()
                    val totalSimulatedBytes = (sampleRate * channels * bytesPerSample * totalDurationSec).toLong()
                    
                    return totalSimulatedBytes
                }
            }
        }
        
        // Standard non-DSD fallback
        val stream = context.contentResolver.openInputStream(dataSpec.uri)
            ?: throw java.io.IOException("Unable to open stream")
        inputStream = stream
        if (dataSpec.position > 0) {
            stream.skip(dataSpec.position)
        }
        return dataSpec.length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = inputStream ?: return C.RESULT_END_OF_INPUT
        
        if (!isPlayingDsd) {
            // General WAV / FLAC stream passthrough
            val readBytes = stream.read(buffer, offset, length)
            return if (readBytes == -1) C.RESULT_END_OF_INPUT else readBytes
        }
        
        // Read DSD blocks and transcode to PCM or DoP on-the-fly
        var bytesWritten = 0
        val bytesPerSample = if (useDoP) 3 else 2
        val bytesPerFrame = bytesPerSample * 2 // stereo L & R
        
        while (bytesWritten + bytesPerFrame <= length) {
            if (blockBufferIndex >= currentBlockSize) {
                // Read next block of DSD64 data
                if (dsdDataLeftBytes <= 0) break
                
                val blockSizeToRead = parser.channelCount * 4096
                val rawBlock = ByteArray(blockSizeToRead)
                var totalRead = 0
                while (totalRead < blockSizeToRead) {
                    val read = stream.read(rawBlock, totalRead, blockSizeToRead - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                
                if (totalRead <= 0) break
                dsdDataLeftBytes -= totalRead
                
                // Separate Left and Right block
                currentBlockSize = totalRead / parser.channelCount
                System.arraycopy(rawBlock, 0, blockBufferL, 0, currentBlockSize)
                if (parser.channelCount > 1) {
                    System.arraycopy(rawBlock, currentBlockSize, blockBufferR, 0, currentBlockSize)
                } else {
                    System.arraycopy(rawBlock, 0, blockBufferR, 0, currentBlockSize)
                }
                blockBufferIndex = 0
            }
            
            if (useDoP) {
                // DoP Mode: Wrap DSD bits directly with DoP alternating markers: 0x05 and 0xFA
                if (blockBufferIndex + 2 <= currentBlockSize) {
                    // Left element
                    val dsdBitsL0 = blockBufferL[blockBufferIndex].toInt() and 0xFF
                    val dsdBitsL1 = blockBufferL[blockBufferIndex + 1].toInt() and 0xFF
                    
                    // Right element
                    val dsdBitsR0 = blockBufferR[blockBufferIndex].toInt() and 0xFF
                    val dsdBitsR1 = blockBufferR[blockBufferIndex + 1].toInt() and 0xFF
                    
                    blockBufferIndex += 2
                    
                    // alternate marker
                    dopMarker = if (dopMarker == 0x05.toByte()) 0xFA.toByte() else 0x05.toByte()
                    
                    // Write Left Frame: 3 bytes
                    buffer[offset + bytesWritten] = dsdBitsL0.toByte()
                    buffer[offset + bytesWritten + 1] = dsdBitsL1.toByte()
                    buffer[offset + bytesWritten + 2] = dopMarker
                    
                    // Write Right Frame: 3 bytes
                    buffer[offset + bytesWritten + 3] = dsdBitsR0.toByte()
                    buffer[offset + bytesWritten + 4] = dsdBitsR1.toByte()
                    buffer[offset + bytesWritten + 5] = dopMarker
                    
                    bytesWritten += 6
                } else {
                    blockBufferIndex = currentBlockSize
                }
            } else {
                // Down-convert PCM Mode: Decode 1-bit DSD stream into 16-bit standard PCM using sliding window decimation
                if (blockBufferIndex < currentBlockSize) {
                    val byteL = blockBufferL[blockBufferIndex].toInt() and 0xFF
                    val byteR = blockBufferR[blockBufferIndex].toInt() and 0xFF
                    blockBufferIndex++
                    
                    // Process 8 bits for Left and Right sequentially
                    for (bit in 0 until 8) {
                        val bitL = (byteL ushr (7 - bit)) and 1
                        val bitR = (byteR ushr (7 - bit)) and 1
                        
                        movingAvgWindow[windowIndex] = bitL
                        movingAvgWindow[(windowIndex + 32) % 64] = bitR
                        windowIndex = (windowIndex + 1) % 32
                    }
                    
                    // Moving sum of L and R elements
                    var sumL = 0
                    var sumR = 0
                    for (i in 0 until 32) {
                        sumL += movingAvgWindow[i]
                        sumR += movingAvgWindow[i + 32]
                    }
                    
                    // Map range [0..32] to [-32768..32767] PCM 16-bit
                    val pcmL = ((sumL / 32.0f * 65535.0f) - 32768f).toInt().coerceIn(-32768, 32767).toShort()
                    val pcmR = ((sumR / 32.0f * 65535.0f) - 32768f).toInt().coerceIn(-32768, 32767).toShort()
                    
                    // Write to target buffer
                    buffer[offset + bytesWritten] = (pcmL.toInt() and 0xFF).toByte()
                    buffer[offset + bytesWritten + 1] = ((pcmL.toInt() ushr 8) and 0xFF).toByte()
                    buffer[offset + bytesWritten + 2] = (pcmR.toInt() and 0xFF).toByte()
                    buffer[offset + bytesWritten + 3] = ((pcmR.toInt() ushr 8) and 0xFF).toByte()
                    
                    bytesWritten += 4
                }
            }
        }
        
        return if (bytesWritten == 0) C.RESULT_END_OF_INPUT else bytesWritten
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        inputStream?.close()
        inputStream = null
        isPlayingDsd = false
    }
}
