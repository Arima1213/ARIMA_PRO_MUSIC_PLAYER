package com.arima.pro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GainNormalizationAudioProcessor : BaseAudioProcessor() {

    private var isEnabled: Boolean = false
    private var currentGainDb: Float = 0f
    private var currentMultiplier: Float = 1.0f

    fun setNormalizationEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            flush()
        }
    }

    fun setGainDb(gainDb: Float) {
        if (currentGainDb != gainDb) {
            currentGainDb = gainDb
            currentMultiplier = Math.pow(10.0, (gainDb / 20.0)).toFloat()
            android.util.Log.d("GainNormalizationAudioProcessor", "Set gain DB to: $gainDb (multiplier: $currentMultiplier)")
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val limit = inputBuffer.limit()
        val position = inputBuffer.position()
        val size = limit - position
        if (size <= 0) return

        if (!isEnabled || currentMultiplier == 1.0f) {
            // Pass-through
            val buffer = replaceOutputBuffer(size)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(size)
        val tempBuffer = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        
        while (tempBuffer.remaining() >= 2) {
            val sample = tempBuffer.getShort().toFloat()
            val normalizedSample = (sample * currentMultiplier).coerceIn(-32768f, 32767f).toInt().toShort()
            buffer.putShort(normalizedSample)
        }
        
        inputBuffer.position(limit)
        buffer.flip()
    }
}
