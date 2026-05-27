package com.arima.pro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResamplingAudioProcessor : BaseAudioProcessor() {

    private var targetSampleRate: Int = 0 // 0 means no resampling (Bit-perfect)

    fun setTargetSampleRate(sampleRate: Int) {
        if (targetSampleRate != sampleRate) {
            targetSampleRate = sampleRate
            flush() // Reset processor state
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val outputSampleRate = if (targetSampleRate > 0) targetSampleRate else inputAudioFormat.sampleRate
        
        return AudioProcessor.AudioFormat(
            outputSampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_16BIT
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val limit = inputBuffer.limit()
        val position = inputBuffer.position()
        val size = limit - position
        if (size <= 0) return

        val inputRate = inputAudioFormat.sampleRate
        val outputRate = outputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount

        if (inputRate == outputRate) {
            // Pass-through
            val buffer = replaceOutputBuffer(size)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val bytesPerFrame = channels * 2
        val inputFramesCount = size / bytesPerFrame
        if (inputFramesCount <= 0) {
            inputBuffer.position(limit)
            return
        }

        val outputFramesCount = (inputFramesCount.toDouble() * outputRate / inputRate).toInt()
        val outputSize = outputFramesCount * bytesPerFrame
        val buffer = replaceOutputBuffer(outputSize)

        val inputShorts = ShortArray(inputFramesCount * channels)
        val tempBuffer = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        for (i in 0 until inputShorts.size) {
            if (tempBuffer.remaining() >= 2) {
                inputShorts[i] = tempBuffer.getShort()
            }
        }
        inputBuffer.position(limit)

        for (fOut in 0 until outputFramesCount) {
            val srcIndexDouble = fOut.toDouble() * inputRate / outputRate
            val srcIndexFloor = srcIndexDouble.toInt()
            val fraction = (srcIndexDouble - srcIndexFloor).toFloat()

            for (ch in 0 until channels) {
                val idx1 = srcIndexFloor * channels + ch
                val idx2 = (srcIndexFloor + 1) * channels + ch

                val s1 = if (idx1 < inputShorts.size && idx1 >= 0) inputShorts[idx1] else 0
                val s2 = if (idx2 < inputShorts.size && idx2 >= 0) inputShorts[idx2] else s1

                val interpolatedSample = (s1 * (1.0f - fraction) + s2 * fraction).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(interpolatedSample)
            }
        }

        buffer.flip()
    }
}
