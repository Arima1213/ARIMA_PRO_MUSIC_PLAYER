package com.arima.pro.core.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class VuMeterAnalyzer {

    private var currentPeakL = -60.0f
    private var currentPeakR = -60.0f
    private var peakHoldTimeL = 0L
    private var peakHoldTimeR = 0L

    companion object {
        private const val PEAK_HOLD_MS = 2000L
        private const val DECAY_RATE_DB = 0.5f
    }

    fun analyze(pcmData: ByteArray, channels: Int): VuLevels {
        if (pcmData.isEmpty()) {
            return VuLevels(-60.0f, -60.0f, -60.0f, -60.0f)
        }

        val samplesCount = pcmData.size / 2
        var sumSquareL = 0.0
        var sumSquareR = 0.0
        var countL = 0
        var countR = 0

        for (i in 0 until samplesCount) {
            val byteLow = pcmData[i * 2].toInt() and 0xFF
            val byteHigh = pcmData[i * 2 + 1].toInt()
            val sampleShort = ((byteHigh shl 8) or byteLow).toShort()

            if (channels == 2) {
                if (i % 2 == 0) {
                    sumSquareL += sampleShort * sampleShort
                    countL++
                } else {
                    sumSquareR += sampleShort * sampleShort
                    countR++
                }
            } else {
                val sqVal = sampleShort.toDouble() * sampleShort.toDouble()
                sumSquareL += sqVal
                sumSquareR += sqVal
                countL++
                countR++
            }
        }

        val rmsL = if (countL > 0) sqrt(sumSquareL / countL) else 0.0
        val rmsR = if (countR > 0) sqrt(sumSquareR / countR) else 0.0

        val rawDbL = if (rmsL > 0.0) 20 * log10(rmsL / 32768.0) else -60.0
        val rawDbR = if (rmsR > 0.0) 20 * log10(rmsR / 32768.0) else -60.0

        val leftDb = max(-60.0f, rawDbL.toFloat())
        val rightDb = max(-60.0f, rawDbR.toFloat())

        val currentTime = System.currentTimeMillis()

        if (leftDb >= currentPeakL) {
            currentPeakL = leftDb
            peakHoldTimeL = currentTime + PEAK_HOLD_MS
        } else {
            if (currentTime > peakHoldTimeL) {
                currentPeakL = max(-60.0f, currentPeakL - DECAY_RATE_DB)
            }
        }

        if (rightDb >= currentPeakR) {
            currentPeakR = rightDb
            peakHoldTimeR = currentTime + PEAK_HOLD_MS
        } else {
            if (currentTime > peakHoldTimeR) {
                currentPeakR = max(-60.0f, currentPeakR - DECAY_RATE_DB)
            }
        }

        return VuLevels(
            leftDb = leftDb,
            rightDb = rightDb,
            peakL = currentPeakL,
            peakR = currentPeakR
        )
    }
}
