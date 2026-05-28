package com.arima.pro.core.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object PlayerHolder {
    var player: ExoPlayer? = null
    val equalizerEngine = com.example.domain.service.EqualizerEngine()
    
    // Audio Path Processors
    val resamplingAudioProcessor = ResamplingAudioProcessor()
    val ditheringAudioProcessor = DitheringAudioProcessor()
    val gainNormalizationAudioProcessor = GainNormalizationAudioProcessor()
    val vuMeterAnalyzer = VuMeterAnalyzer()
    val audioLevelExtractor = AudioLevelExtractor(vuMeterAnalyzer)
    
    // Control States
    var bitPerfectActive = false
    var bufferMs = 200 // Default: Max Latency (Stable)
    var dopModeActive = false

    // Step 1 requested API methods:
    fun applyResamplingSettings(targetSampleRate: Int) {
        if (bitPerfectActive) {
            resamplingAudioProcessor.setTargetSampleRate(0)
            return
        }
        resamplingAudioProcessor.setTargetSampleRate(targetSampleRate)
        android.util.Log.d("PlayerHolder", "Applied resampling rate (Hz): $targetSampleRate")
    }

    fun applyDitheringSettings(enabled: Boolean) {
        val activeValue = enabled && !bitPerfectActive
        ditheringAudioProcessor.setDitheringEnabled(activeValue)
    }

    fun applyNormalizationSettings(enabled: Boolean, gainDb: Float) {
        val activeValue = enabled && !bitPerfectActive
        gainNormalizationAudioProcessor.setNormalizationEnabled(activeValue)
        gainNormalizationAudioProcessor.setGainDb(gainDb)
    }

    // Compatibility functions for currently existing codebase:
    fun applyResampling(resamplingRateStr: String) {
        if (bitPerfectActive) {
            resamplingAudioProcessor.setTargetSampleRate(0)
            return
        }
        val sampleRate = when (resamplingRateStr) {
            "96 kHz" -> 96000
            "192 kHz" -> 192000
            "384 kHz" -> 384000
            else -> 0 // Bit-perfect / no resampling
        }
        applyResamplingSettings(sampleRate)
    }

    fun applyDithering(enabled: Boolean) {
        applyDitheringSettings(enabled)
    }

    fun setVolumeNormalization(enabled: Boolean, gainDb: Float) {
        applyNormalizationSettings(enabled, gainDb)
    }

    fun applyBufferSize(context: Context, sizeStr: String) {
        val newMs = when {
            sizeStr.contains("Min Latency") || sizeStr.contains("Direct Direct") -> 2
            sizeStr.contains("Low Latency") || sizeStr.contains("Max Dynamic") || sizeStr.contains("Max Latency") -> 10
            sizeStr.contains("Normal") || sizeStr.contains("Safe (Medium)") -> 50
            else -> 200 // Max Latency (Stable)
        }
        if (bufferMs != newMs) {
            bufferMs = newMs
            android.util.Log.d("PlayerHolder", "Changing buffer size to: $sizeStr ($bufferMs ms)")
            recreatePlayer(context)
        }
    }

    fun recreatePlayer(context: Context) {
        val oldPlayer = player
        if (oldPlayer != null) {
            val position = oldPlayer.currentPosition
            val isPlaying = oldPlayer.isPlaying
            val currentMediaItem = oldPlayer.currentMediaItem
            
            oldPlayer.stop()
            oldPlayer.release()
            player = null
            
            val newPlayer = getOrCreatePlayer(context)
            if (currentMediaItem != null) {
                newPlayer.setMediaItem(currentMediaItem, position)
                newPlayer.prepare()
                if (isPlaying) {
                    newPlayer.play()
                }
            }
        }
    }

    fun getReplayGain(context: Context, path: String): Float {
        try {
            if (!path.startsWith("content://")) {
                val file = java.io.File(path)
                if (file.exists()) {
                    val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                    val tag = audioFile.tag
                    if (tag != null) {
                        var gainStr = tag.getFirst("REPLAYGAIN_TRACK_GAIN")
                        if (gainStr.isNullOrEmpty()) {
                            gainStr = tag.getFirst("R128_TRACK_GAIN")
                        }
                        if (gainStr.isNullOrEmpty()) {
                            gainStr = tag.getFirst("replaygain_track_gain")
                        }
                        if (!gainStr.isNullOrEmpty()) {
                            val parsed = gainStr.replace("dB", "").trim().toFloatOrNull()
                            if (parsed != null) return parsed
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerHolder", "Error reading metadata ReplayGain: ${e.message}")
        }
        return 0f
    }

    fun getOrCreatePlayer(context: Context): ExoPlayer {
        val active = player
        if (active != null) return active
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context.applicationContext) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(audioLevelExtractor, resamplingAudioProcessor, ditheringAudioProcessor, gainNormalizationAudioProcessor))
                    .setAudioTrackBufferSizeProvider(object : androidx.media3.exoplayer.audio.DefaultAudioSink.AudioTrackBufferSizeProvider {
                        override fun getBufferSizeInBytes(
                            minBufferSizeInBytes: Int,
                            encoding: Int,
                            outputMode: Int,
                            pcmFrameSize: Int,
                            sampleRate: Int,
                            bitrate: Int,
                            maxPlaybackSpeed: Double
                        ): Int {
                            val ms = bufferMs
                            if (ms > 0) {
                                val bytesPerSample = if (encoding == C.ENCODING_PCM_16BIT) 2 else 4
                                val channels = 2
                                val calculatedSize = (sampleRate * channels * bytesPerSample * ms) / 1000
                                return calculatedSize.coerceAtLeast(minBufferSizeInBytes)
                            }
                            return minBufferSizeInBytes
                        }
                    })
                    .build()
            }
        }
            
        // Use custom DataSource to enable DsdDataSource on-the-fly transcoding
        val customDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            val currentUri = player?.currentMediaItem?.localConfiguration?.uri
            val uriStr = currentUri?.toString() ?: ""
            if (uriStr.endsWith(".dsf", ignoreCase = true) || uriStr.endsWith(".dff", ignoreCase = true)) {
                DsdDataSource(context.applicationContext, useDoP = dopModeActive)
            } else if (uriStr.startsWith("content://", ignoreCase = true)) {
                androidx.media3.datasource.ContentDataSource(context.applicationContext)
            } else if (uriStr.startsWith("http://", ignoreCase = true) || uriStr.startsWith("https://", ignoreCase = true)) {
                androidx.media3.datasource.DefaultHttpDataSource.Factory().createDataSource()
            } else {
                androidx.media3.datasource.DefaultDataSource(context.applicationContext, true)
            }
        }
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(customDataSourceFactory)

        val newPlayer = ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        
        player = newPlayer
        
        newPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (!bitPerfectActive) {
                    equalizerEngine.setAudioSessionId(audioSessionId)
                }
            }
        })
        
        if (newPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET && !bitPerfectActive) {
            equalizerEngine.setAudioSessionId(newPlayer.audioSessionId)
        }
        
        return newPlayer
    }
}
