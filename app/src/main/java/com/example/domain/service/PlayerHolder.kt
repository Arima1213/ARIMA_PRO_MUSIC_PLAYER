package com.example.domain.service

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object PlayerHolder {
    var player: ExoPlayer? = null
    val equalizerEngine = EqualizerEngine()
    
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        val active = player
        if (active != null) return active
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val newPlayer = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        
        player = newPlayer
        
        // Listen to audio session ID changes to sync with Equalizer
        newPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizerEngine.setAudioSessionId(audioSessionId)
            }
        })
        
        if (newPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            equalizerEngine.setAudioSessionId(newPlayer.audioSessionId)
        }
        
        return newPlayer
    }
}
