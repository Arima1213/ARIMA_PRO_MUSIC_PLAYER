package com.example.domain.service

import android.media.audiofx.Equalizer
import android.util.Log

class EqualizerEngine {
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0
    private var isEnabled: Boolean = false
    private var lastBandGains: List<Float> = List(10) { 0.0f }

    fun setAudioSessionId(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (audioSessionId == currentSessionId && equalizer != null) {
            // Apply again to ensure it takes effect
            applySettings(isEnabled, lastBandGains)
            return
        }
        
        try {
            release()
            currentSessionId = audioSessionId
            val eq = Equalizer(0, audioSessionId)
            eq.enabled = isEnabled
            equalizer = eq
            Log.d("EqualizerEngine", "Equalizer initialized successfully for session: $audioSessionId. Bands: ${eq.numberOfBands}")
            applySettings(isEnabled, lastBandGains)
        } catch (e: Throwable) {
            Log.e("EqualizerEngine", "Error initializing Equalizer: ${e.message}")
            e.printStackTrace()
        }
    }

    fun applySettings(enabled: Boolean, bandGains: List<Float>) {
        isEnabled = enabled
        lastBandGains = bandGains
        val eq = equalizer ?: return
        try {
            eq.enabled = enabled
            if (!enabled) return

            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0] // in millibels
            val maxLevel = eq.bandLevelRange[1] // in millibels

            for (i in 0 until numBands) {
                // Map/interpolate from 10 UI bands to hardware bands
                val uiIndex = if (numBands == 10) {
                    i
                } else {
                    val ratio = 10f / numBands
                    (i * ratio).toInt().coerceIn(0, 9)
                }

                val uiGainDb = bandGains.getOrNull(uiIndex) ?: 0.0f
                val milliBels = (uiGainDb * 110).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
                try {
                    eq.setBandLevel(i.toShort(), milliBels.toShort())
                } catch (e: Exception) {
                    Log.e("EqualizerEngine", "Failed to set band level: $i to $milliBels milliBels")
                }
            }
            Log.d("EqualizerEngine", "Equalizer settings synchronised: enabled=$enabled, bandGains=$bandGains")
        } catch (e: Throwable) {
            Log.e("EqualizerEngine", "Error applying Equalizer settings: ${e.message}")
        }
    }

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (e: Throwable) {
            // Log or ignore
        }
        equalizer = null
        currentSessionId = 0
    }
}
