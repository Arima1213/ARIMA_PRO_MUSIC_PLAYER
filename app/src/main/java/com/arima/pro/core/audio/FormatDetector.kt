package com.arima.pro.core.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.Locale

class FormatDetector(private val context: Context) {

    fun detectFormat(uri: Uri): AudioFormat {
        val extension = getExtension(uri).uppercase(Locale.ROOT)
        
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(12)
                val read = stream.read(header)
                if (read >= 4) {
                    val magic4 = String(header, 0, 4, Charsets.US_ASCII)
                    if (magic4 == "fLaC") return AudioFormat.FLAC
                    if (magic4 == "RIFF" && read >= 12) {
                        val formatWav = String(header, 8, 4, Charsets.US_ASCII)
                        if (formatWav == "WAVE") return AudioFormat.WAV
                    }
                    if (magic4 == "DSD " || magic4 == "FRM8") return AudioFormat.DSD
                    if (magic4.startsWith("ID3")) return AudioFormat.MP3
                    if (magic4 == "FORM" && read >= 12) {
                        val formatAiff = String(header, 8, 4, Charsets.US_ASCII)
                        if (formatAiff == "AIFF" || formatAiff == "AIFC") return AudioFormat.AIFF
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored
        }

        return when {
            extension.contains("WAV") -> AudioFormat.WAV
            extension.contains("FLAC") -> AudioFormat.FLAC
            extension.contains("DSF") || extension.contains("DFF") || extension.contains("DSD") -> AudioFormat.DSD
            extension.contains("MP3") -> AudioFormat.MP3
            extension.contains("M4A") || extension.contains("ALAC") -> AudioFormat.ALAC
            extension.contains("AAC") -> AudioFormat.AAC
            extension.contains("AIF") || extension.contains("AIFF") -> AudioFormat.AIFF
            extension.contains("OGG") -> AudioFormat.OGG
            else -> AudioFormat.UNKNOWN
        }
    }

    fun detectTechnicalProperties(uri: Uri): TechnicalInfo {
        val retriever = MediaMetadataRetriever()
        var sampleRateVal = 44100
        var bitDepthVal = 16
        var channelsVal = 2

        try {
            retriever.setDataSource(context, uri)
            
            val sampleRateStr = try { retriever.extractMetadata(32) } catch (e: Exception) { null }
            val channelsStr = try { retriever.extractMetadata(33) } catch (e: Exception) { null }
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

            sampleRateStr?.toIntOrNull()?.let { sampleRateVal = it }
            channelsStr?.toIntOrNull()?.let { channelsVal = it }

            val bitrate = bitrateStr?.toIntOrNull() ?: 0
            
            val format = detectFormat(uri)
            if (format == AudioFormat.WAV) {
                bitDepthVal = readWavBitDepth(uri) ?: 16
            } else if (format == AudioFormat.FLAC) {
                bitDepthVal = readFlacBitDepth(uri) ?: 24
            } else if (format == AudioFormat.DSD) {
                bitDepthVal = 1
                sampleRateVal = 2822400
            } else {
                bitDepthVal = if (bitrate > 320000) 24 else 16
            }
        } catch (e: Exception) {
            // Fallback
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignored
            }
        }

        return TechnicalInfo(sampleRateVal, bitDepthVal, channelsVal)
    }

    private fun getExtension(uri: Uri): String {
        val path = uri.path ?: return ""
        val lastDot = path.lastIndexOf('.')
        return if (lastDot != -1) path.substring(lastDot + 1) else ""
    }

    private fun readWavBitDepth(uri: Uri): Int? {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(44)
                if (stream.read(buffer) >= 36) {
                    for (i in 12..30) {
                        if (buffer[i] == 'f'.toByte() && buffer[i+1] == 'm'.toByte() && buffer[i+2] == 't'.toByte()) {
                            val bitDepth = (buffer[34].toInt() and 0xFF) or ((buffer[35].toInt() and 0xFF) shl 8)
                            if (bitDepth in 8..32) return bitDepth
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun readFlacBitDepth(uri: Uri): Int? {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(42)
                if (stream.read(buffer) >= 42) {
                    val b32 = buffer[32].toInt() and 0xFF
                    val b33 = buffer[33].toInt() and 0xFF
                    val bitDepth = (((b32 and 0x01) shl 4) or ((b33 and 0xF0) ushr 4)) + 1
                    if (bitDepth in 8..32) return bitDepth
                }
            }
        } catch (e: Exception) {}
        return null
    }
}
