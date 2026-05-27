package com.arima.pro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class DitheringAudioProcessor : BaseAudioProcessor() {

    private var isEnabled: Boolean = false
    private val random = Random(System.currentTimeMillis())

    // Store state of previous two random values to generate triangular dither noise
    private var prevRand1 = 0f
    private var prevRand2 = 0f

    fun setDitheringEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            flush()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Only process 16-bit PCM
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

        if (!isEnabled) {
            // Pass-through when disabled
            val buffer = replaceOutputBuffer(size)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(size)
        val tempBuffer = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        
        while (tempBuffer.remaining() >= 2) {
            val sample = tempBuffer.getShort().toInt()
            
            // Generate triangular noise: the sum of two independent uniform random numbers in [-1, 1]
            // We scale it to approximately 1 LSB of 16-bit PCM (which is +/- 1.0)
            val r1 = random.nextFloat() * 2.0f - 1.0f
            val r2 = random.nextFloat() * 2.0f - 1.0f
            val ditherPower = r1 - r2 // Triangular PDF in range [-2, 2]
            
            // Add triangular noise and clamp to peak amplitude limit of signed 16-bit int
            val ditheredSample = (sample + ditherPower).toInt().coerceIn(-32768, 32767)
            buffer.putShort(ditheredSample.toShort())
        }
        
        inputBuffer.position(limit)
        buffer.flip()
    }
}
