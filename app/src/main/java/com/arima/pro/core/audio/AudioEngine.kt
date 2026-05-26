package com.arima.pro.core.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioEngine(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    private val playbackStateFlow: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val queue = mutableListOf<Track>()

    init {
        try {
            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .build()
                .apply {
                    setWakeMode(C.WAKE_MODE_LOCAL)
                    
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            _playbackState.value = when (state) {
                                Player.STATE_IDLE -> PlaybackState.IDLE
                                Player.STATE_BUFFERING -> PlaybackState.BUFFERING
                                Player.STATE_READY -> {
                                    if (isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
                                }
                                Player.STATE_ENDED -> PlaybackState.IDLE
                                else -> PlaybackState.IDLE
                            }
                        }

                        override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                            if (exoPlayer?.playbackState == Player.STATE_READY) {
                                _playbackState.value = if (isPlayingChange) PlaybackState.PLAYING else PlaybackState.PAUSED
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            mediaItem?.let { item ->
                                val trackId = item.mediaId
                                val track = queue.find { it.id == trackId }
                                if (track != null) {
                                    _currentTrack.value = track
                                }
                            }
                        }
                    })
                }
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.ERROR
        }
    }

    fun play(uri: Uri) {
        val matchingTrack = queue.find { it.uri == uri }
        val finalTrack = matchingTrack ?: Track(
            id = uri.toString(),
            title = uri.lastPathSegment ?: "Unknown Track",
            artist = "Unknown Artist",
            album = "Unknown Album",
            durationMs = 0L,
            uri = uri,
            format = "UNKNOWN",
            sampleRate = 44100,
            bitDepth = 16,
            size = 0L
        )
        if (matchingTrack == null) {
            queue.add(finalTrack)
        }
        _currentTrack.value = finalTrack

        exoPlayer?.let { player ->
            player.setMediaItem(MediaItem.Builder().setUri(uri).setMediaId(finalTrack.id).build())
            player.prepare()
            player.play()
        }
    }

    fun play(uriString: String) {
        play(Uri.parse(uriString))
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        exoPlayer?.stop()
        _playbackState.value = PlaybackState.IDLE
    }

    fun seek(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun getPlaybackState(): StateFlow<PlaybackState> {
        return playbackStateFlow
    }

    fun getCurrentTrack(): Track? {
        return _currentTrack.value
    }

    fun addToQueue(track: Track) {
        if (!queue.contains(track)) {
            queue.add(track)
            exoPlayer?.addMediaItem(MediaItem.Builder().setUri(track.uri).setMediaId(track.id).build())
        }
    }

    fun playNext(track: Track) {
        val currentIndex = queue.indexOf(_currentTrack.value)
        val targetIndex = if (currentIndex == -1) 0 else currentIndex + 1
        queue.add(targetIndex, track)
        exoPlayer?.addMediaItem(targetIndex, MediaItem.Builder().setUri(track.uri).setMediaId(track.id).build())
    }

    fun removeFromQueue(track: Track) {
        val index = queue.indexOf(track)
        if (index != -1) {
            queue.removeAt(index)
            exoPlayer?.removeMediaItem(index)
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
