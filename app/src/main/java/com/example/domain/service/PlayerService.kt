package com.example.domain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.arima.pro.core.audio.PlayerHolder

class PlayerService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "arima_playback_channel"
        
        var sharedPlayer: ExoPlayer? = null
            private set
            
        var sharedSession: MediaSession? = null
            private set

        fun updateSharedPlayer(newPlayer: ExoPlayer) {
            sharedPlayer = newPlayer
            try {
                sharedSession?.player = newPlayer
            } catch (e: Exception) {
                android.util.Log.e("PlayerService", "Error updating MediaSession player: ${e.message}")
            }
        }
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        val player = PlayerHolder.getOrCreatePlayer(this)
        sharedPlayer = player
        
        mediaSession = MediaSession.Builder(this, player).build()
        sharedSession = mediaSession
        
        createNotificationChannel()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = sharedPlayer
        val isPlaying = if (player != null) {
            try { player.isPlaying } catch (e: Exception) { false }
        } else {
            false
        }
        if (player != null && isPlaying) {
            android.util.Log.d("PlayerService", "Task removed, player playing. Surviving task removal.")
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            mediaSession?.run {
                try {
                    player.release()
                } catch (e: Exception) {
                    android.util.Log.e("PlayerService", "Error releasing player in onDestroy: ${e.message}")
                }
                try {
                    release()
                } catch (e: Exception) {
                    android.util.Log.e("PlayerService", "Error releasing session in onDestroy: ${e.message}")
                }
                mediaSession = null
                sharedSession = null
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerService", "Error in onDestroy(): ${e.message}")
        }
        PlayerHolder.player = null
        sharedPlayer = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-Res audio playback controls"
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
