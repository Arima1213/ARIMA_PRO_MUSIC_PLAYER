package com.arima.pro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class AudioLevelExtractor(private val vuMeterAnalyzer: VuMeterAnalyzer) : BaseAudioProcessor() {

    private var channelCount = 2

    // Callback when new levels are calculated
    var onLevelsUpdated: ((VuLevels) -> Unit)? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val limit = inputBuffer.limit()
        val position = inputBuffer.position()
        val size = limit - position
        if (size <= 0) return

        // Extract PCM buffer bytes to analyze
        val pcmData = ByteArray(size)
        val duplicate = inputBuffer.duplicate()
        duplicate.get(pcmData)

        // Run analysis
        try {
            val levels = vuMeterAnalyzer.analyze(pcmData, channelCount)
            onLevelsUpdated?.invoke(levels)
        } catch (e: Exception) {
            android.util.Log.e("AudioLevelExtractor", "Error analyzing PCM: ${e.message}")
        }

        // Pass-through
        val buffer = replaceOutputBuffer(size)
        buffer.put(inputBuffer)
        buffer.flip()
    }
}
