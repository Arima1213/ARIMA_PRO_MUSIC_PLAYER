package com.arima.pro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ResamplingAudioProcessor : BaseAudioProcessor() {

    private var targetSampleRate: Int = 0 // 0 means no resampling (Bit-perfect)
    private var lastInputSampleRate = 0
    private var lastInputChannelCount = 0

    fun setTargetSampleRate(sampleRate: Int) {
        if (targetSampleRate != sampleRate) {
            targetSampleRate = sampleRate
            flush() // Reset processor state
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        
        val outputSampleRate = if (targetSampleRate > 0) targetSampleRate else inputAudioFormat.sampleRate
        
        // Passthrough if no resampling needed
        if (outputSampleRate == inputAudioFormat.sampleRate) {
            return AudioProcessor.AudioFormat(
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount,
                C.ENCODING_PCM_16BIT
            )
        }
        
        lastInputSampleRate = inputAudioFormat.sampleRate
        lastInputChannelCount = inputAudioFormat.channelCount
        
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

        if (channels <= 0) {
            inputBuffer.position(limit)
            return
        }

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

        val inputShortsSize = inputFramesCount * channels
        val finalShortsSize = inputShortsSize - (inputShortsSize % channels)
        if (finalShortsSize <= 0) {
            inputBuffer.position(limit)
            return
        }

        val inputShorts = ShortArray(finalShortsSize)
        val tempBuffer = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        for (i in 0 until inputShorts.size) {
            if (tempBuffer.remaining() >= 2) {
                inputShorts[i] = tempBuffer.getShort()
            } else {
                inputShorts[i] = 0
            }
        }
        inputBuffer.position(limit)

        for (fOut in 0 until outputFramesCount) {
            val srcIndexDouble = fOut.toDouble() * inputRate / outputRate
            val srcIndexFloor = srcIndexDouble.toInt()
            val fraction = (srcIndexDouble - srcIndexFloor).toFloat()

            for (ch in 0 until channels) {
                val idx0 = ((srcIndexFloor - 1) * channels + ch).coerceIn(0, inputShorts.size - 1)
                val idx1 = (srcIndexFloor * channels + ch).coerceIn(0, inputShorts.size - 1)
                val idx2 = ((srcIndexFloor + 1) * channels + ch).coerceIn(0, inputShorts.size - 1)
                val idx3 = ((srcIndexFloor + 2) * channels + ch).coerceIn(0, inputShorts.size - 1)

                val s0 = inputShorts[idx0].toFloat()
                val s1 = inputShorts[idx1].toFloat()
                val s2 = inputShorts[idx2].toFloat()
                val s3 = inputShorts[idx3].toFloat()

                // 4-point Hermite Spline Interpolation
                val c0 = s1
                val c1 = 0.5f * (s2 - s0)
                val c2 = s0 - 2.5f * s1 + 2.0f * s2 - 0.5f * s3
                val c3 = 0.5f * (s3 - s0) + 1.5f * (s1 - s2)
                
                val interpolatedSample = (((c3 * fraction + c2) * fraction + c1) * fraction + c0).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(interpolatedSample)
            }
        }

        buffer.flip()
    }
}
