package com.arima.pro.core.audio

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DffParser {
    data class DffMetadata(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int = 1, // DSD = 1-bit
        val formatType: DffFormatType
    )

    enum class DffFormatType { DSD64, DSD128, DSD256, DSD512 }

    var dataStartOffset = 0L
    var dataSize = 0L
    var sampleRate = 0
    var channelCount = 1

    fun parse(inputStream: InputStream): DffMetadata {
        val marker = ByteArray(10)
        var read = inputStream.read(marker)
        
        // Skip ID3v2 if present
        if (read == 10 && String(marker, 0, 3, Charsets.US_ASCII) == "ID3") {
            val size = ((marker[6].toInt() and 0x7f) shl 21) or
                       ((marker[7].toInt() and 0x7f) shl 14) or
                       ((marker[8].toInt() and 0x7f) shl 7) or
                       (marker[9].toInt() and 0x7f)
            inputStream.skip(size.toLong())
            read = inputStream.read(marker, 0, 4)
        } else if (read == 10) {
            // Not ID3, keep the first 4 bytes
            read = 4
        }
        
        if (read < 4 || String(marker, 0, 4, Charsets.US_ASCII) != "FRM8") {
            throw IllegalArgumentException("Not a valid DFF file: wrong marker")
        }

        val chunkSize = readUint64(inputStream)
        dataStartOffset += 12

        val format = ByteArray(4)
        inputStream.read(format)
        dataStartOffset += 4

        // Read chunks until data chunk
        var fs = 2822400
        var channels = 2
        var bitsPerSample = 1

        while (true) {
            val chunkId = ByteArray(4)
            val readChunks = inputStream.read(chunkId)
            if (readChunks < 4) break
            dataStartOffset += 4

            val cSize = readUint64(inputStream)
            dataStartOffset += 8

            val chunkStr = String(chunkId)
            if (chunkStr == "FVER") {
                skipFully(inputStream, cSize)
                dataStartOffset += cSize
            } else if (chunkStr == "PROP") {
                val propType = ByteArray(4)
                inputStream.read(propType)
                dataStartOffset += 4

                // internal chunks inside PROP
                var remainingPropSize = cSize - 4
                while (remainingPropSize > 0) {
                    val pChunkId = ByteArray(4)
                    inputStream.read(pChunkId)
                    val pChunkSize = readUint64(inputStream)
                    dataStartOffset += 12
                    remainingPropSize -= 12

                    if (String(pChunkId) == "FS  ") {
                        fs = readUint32(inputStream)
                        dataStartOffset += 4
                        remainingPropSize -= 4
                        // skip padding if any
                        val skipAmount = pChunkSize - 4
                        if (skipAmount > 0) {
                            skipFully(inputStream, skipAmount)
                            dataStartOffset += skipAmount
                            remainingPropSize -= skipAmount
                        }
                    } else if (String(pChunkId) == "CHNL") {
                        channels = readUint16(inputStream).coerceIn(1, 8)
                        dataStartOffset += 2
                        remainingPropSize -= 2
                        val skipAmount = pChunkSize - 2
                        if (skipAmount > 0) {
                            skipFully(inputStream, skipAmount)
                            dataStartOffset += skipAmount
                            remainingPropSize -= skipAmount
                        }
                    } else {
                        skipFully(inputStream, pChunkSize)
                        dataStartOffset += pChunkSize
                        remainingPropSize -= pChunkSize
                    }
                }
            } else if (chunkStr == "DSD ") {
                dataSize = cSize
                break // Stop when we reach data
            } else {
                skipFully(inputStream, cSize)
                dataStartOffset += cSize
            }

            // DFF chunks are 2-byte aligned
            if (cSize % 2 != 0L) {
                skipFully(inputStream, 1)
                dataStartOffset += 1
            }
        }

        this.sampleRate = fs
        this.channelCount = channels

        val formatType = when (fs) {
            2822400 -> DffFormatType.DSD64
            5644800 -> DffFormatType.DSD128
            11289600 -> DffFormatType.DSD256
            22579200 -> DffFormatType.DSD512
            else -> DffFormatType.DSD64
        }

        return DffMetadata(sampleRate = fs, channels = channels, bitsPerSample = bitsPerSample, formatType = formatType)
    }

    private fun skipFully(inputStream: InputStream, amount: Long) {
        var skipped = 0L
        while (skipped < amount) {
            val s = inputStream.skip(amount - skipped)
            if (s <= 0) {
                // Try reading one byte to see if we're really at EOF
                if (inputStream.read() == -1) break
                skipped += 1
            } else {
                skipped += s
            }
        }
    }

    private fun readUint64(inputStream: InputStream): Long {
        val bytes = ByteArray(8)
        inputStream.read(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).long
    }

    private fun readUint32(inputStream: InputStream): Int {
        val bytes = ByteArray(4)
        inputStream.read(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun readUint16(inputStream: InputStream): Int {
        val bytes = ByteArray(2)
        inputStream.read(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).short.toInt()
    }

    private fun readUint8(inputStream: InputStream): Int {
        return inputStream.read()
    }
}
