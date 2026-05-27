package com.example.domain.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.domain.model.Song
import com.arima.pro.core.audio.PlayerHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.random.Random

class AudioEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var vuJob: Job? = null
    private val outputManager = com.arima.pro.core.audio.AudioOutputManager(context)

    var dacExclusiveModeActive = true
    val showDacMissingDialog = MutableStateFlow(false)
    val volumeNormalizationEnabled = MutableStateFlow(true)
    val gaplessPlaybackEnabled = MutableStateFlow(true)

    private val player: ExoPlayer get() = PlayerHolder.getOrCreatePlayer(context)

    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlayingChange: Boolean) {
            _isPlaying.value = isPlayingChange
            if (isPlayingChange) {
                startProgressLoop()
                startVuLoop()
            } else {
                stopProgressLoop()
                decayVuLevels()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                if (!gaplessPlaybackEnabled.value) {
                    scope.launch {
                        delay(1500) // Insert 1.5s silence gap
                        skipToNext()
                    }
                } else {
                    skipToNext()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) {
                val mediaId = mediaItem.mediaId
                val matchedSong = _playbackQueue.value.firstOrNull { it.id.toString() == mediaId }
                if (matchedSong != null) {
                    _currentSong.value = matchedSong
                    
                    // Sync Volume Normalization dynamically on transitions
                    var trackGain = 0f
                    if (volumeNormalizationEnabled.value && matchedSong.path.isNotEmpty()) {
                        trackGain = PlayerHolder.getReplayGain(context, matchedSong.path)
                    }
                    PlayerHolder.setVolumeNormalization(volumeNormalizationEnabled.value, trackGain)
                }
            }
        }
    }

    init {
        try {
            player.removeListener(playbackListener)
            player.addListener(playbackListener)
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error adding playback listener in init: ${e.message}")
        }
    }

    fun applyEqualizer(enabled: Boolean, bandGains: List<Float>) {
        PlayerHolder.equalizerEngine.applySettings(enabled, bandGains)
    }

    private fun isDacConnected(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val devices = audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            devices?.any {
                it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || 
                it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    // State flows
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    val playbackQueue: StateFlow<List<Song>> = _playbackQueue.asStateFlow()

    // VU Levels (L, R in decibels: -60.0 to +3.0)
    private val _vuLevels = MutableStateFlow(Pair(-60.0f, -60.0f))
    val vuLevels: StateFlow<Pair<Float, Float>> = _vuLevels.asStateFlow()

    // Peak Holds (L, R in decibels)
    private val _peakLevels = MutableStateFlow(Pair(-60.0f, -60.0f))
    val peakLevels: StateFlow<Pair<Float, Float>> = _peakLevels.asStateFlow()

    fun setQueue(songs: List<Song>) {
        _playbackQueue.value = songs
        if (_currentSong.value == null && songs.isNotEmpty()) {
            _currentSong.value = songs.first()
            _currentPosition.value = 0L
        }
    }

    fun playSong(song: Song) {
        val queue = _playbackQueue.value
        if (!queue.any { it.id == song.id }) {
            _playbackQueue.value = queue + song
        }
        _currentSong.value = song
        _currentPosition.value = 0L

        scope.launch {
            try {
                // Check for DAC Exclusive Mode blocking
                if (dacExclusiveModeActive && !isDacConnected()) {
                    showDacMissingDialog.value = true
                    _isPlaying.value = false
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Hubungkan DAC untuk memutar (DAC Exclusive Mode Aktif)",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                showDacMissingDialog.value = false

                // Initialize player if null
                val activePlayer = player
                try {
                    activePlayer.removeListener(playbackListener)
                    activePlayer.addListener(playbackListener)
                } catch (e: Exception) {
                    android.util.Log.e("AudioEngine", "Error re-attaching listener in playSong: ${e.message}")
                }

                val fileUri = if (song.path.startsWith("content://")) {
                    Uri.parse(song.path)
                } else {
                    Uri.fromFile(java.io.File(song.path))
                }

                if (fileUri == null) {
                    android.widget.Toast.makeText(context, "File not found", android.widget.Toast.LENGTH_SHORT).show()
                    _isPlaying.value = false
                    return@launch
                }

                // Check physical file accessibility on IO thread
                withContext(Dispatchers.IO) {
                    if (song.path.startsWith("content://")) {
                        var pfd: android.os.ParcelFileDescriptor? = null
                        try {
                            pfd = context.contentResolver.openFileDescriptor(fileUri, "r")
                            if (pfd == null) {
                                throw java.io.IOException("File inaccessible/missing descriptor")
                            }
                        } finally {
                            pfd?.close()
                        }
                    } else {
                        val file = java.io.File(song.path)
                        if (!file.exists()) {
                            throw java.io.IOException("File not found on local storage")
                        }
                    }
                }

                // Setup Volume Normalization on track loading
                var trackGain = 0f
                if (volumeNormalizationEnabled.value && song.path.isNotEmpty()) {
                    // Fetch ReplayGain from track tags
                    val rawGain = PlayerHolder.getReplayGain(context, song.path)
                    // If tag present, calibrate from ReplayGain default (-18 LUFS) to EBU R128 target (-23 LUFS) by shifting -5 dB
                    trackGain = if (rawGain != 0f) rawGain - 5.0f else -14.0f // Fallback to -14 dB (typical attenuation for EBU R128 match)
                }
                PlayerHolder.setVolumeNormalization(volumeNormalizationEnabled.value, trackGain)

                try {
                    val serviceIntent = android.content.Intent(context, PlayerService::class.java)
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    android.util.Log.e("AudioEngine", "Failed to start PlayerService: ${e.message}")
                }

                val createMediaItem = { qSong: Song ->
                    val uri = if (qSong.path.startsWith("content://")) {
                        Uri.parse(qSong.path)
                    } else {
                        Uri.fromFile(java.io.File(qSong.path))
                    }
                    val metadata = androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(qSong.title)
                        .setArtist(qSong.artist)
                        .setAlbumTitle(qSong.album)
                        .apply {
                            if (qSong.albumArt != null) {
                                setArtworkData(qSong.albumArt, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            }
                        }
                        .build()
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId(qSong.id.toString())
                        .setMediaMetadata(metadata)
                        .build()
                }

                if (gaplessPlaybackEnabled.value) {
                    // Populate multi-item playlist internally for native seamless transition
                    val mediaItems = _playbackQueue.value.map { qSong ->
                        createMediaItem(qSong)
                    }
                    val index = _playbackQueue.value.indexOfFirst { it.id == song.id }.coerceIn(0, mediaItems.size - 1)
                    activePlayer.setMediaItems(mediaItems, index, 0L)
                } else {
                    // Single item mode
                    val mediaItem = createMediaItem(song)
                    activePlayer.setMediaItem(mediaItem)
                }

                activePlayer.prepare()
                
                // Route to DAC (Must be done AFTER prepare() as requested)
                outputManager.routeToDac(activePlayer)
                
                activePlayer.play()

            } catch (e: SecurityException) {
                e.printStackTrace()
                android.util.Log.e("AudioEngine", "SecurityException during play: ${e.message}")
                android.widget.Toast.makeText(
                    context, 
                    "Storage permission lost. Please re-select the music folder.", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
                _isPlaying.value = false
            } catch (e: java.io.IOException) {
                e.printStackTrace()
                android.util.Log.e("AudioEngine", "IOException during play: ${e.message}")
                android.widget.Toast.makeText(
                    context, 
                    "File not found", 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                _isPlaying.value = false
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                android.util.Log.e("AudioEngine", "IllegalStateException during play: ${e.message}")
                // Release and re-initialize player
                PlayerHolder.player?.release()
                PlayerHolder.player = null
                _isPlaying.value = false
                android.widget.Toast.makeText(
                    context, 
                    "Player state mismatch. Re-initializing helper engine...", 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("AudioEngine", "UnknownException during play: ${e.message}")
                android.widget.Toast.makeText(
                    context, 
                    "Playback error: ${e.localizedMessage}", 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                _isPlaying.value = false
            }
        }
    }

    fun playNext(song: Song) {
        val current = _currentSong.value
        val queue = _playbackQueue.value.toMutableList()
        queue.removeAll { it.id == song.id }
        if (current == null) {
            queue.add(0, song)
            setQueue(queue)
        } else {
            val currentIndex = queue.indexOfFirst { it.id == current.id }
            if (currentIndex != -1) {
                queue.add(currentIndex + 1, song)
            } else {
                queue.add(0, song)
            }
            _playbackQueue.value = queue
        }
    }

    fun addToQueue(song: Song) {
        val queue = _playbackQueue.value
        if (!queue.any { it.id == song.id }) {
            _playbackQueue.value = queue + song
        }
    }

    fun removeFromQueue(song: Song) {
        val updatedList = _playbackQueue.value.filter { it.id != song.id }
        _playbackQueue.value = updatedList
        if (_currentSong.value?.id == song.id) {
            if (updatedList.isNotEmpty()) {
                _currentSong.value = updatedList.first()
                _currentPosition.value = 0L
                if (_isPlaying.value) {
                    playSong(updatedList.first())
                }
            } else {
                player.stop()
                _currentSong.value = null
                _currentPosition.value = 0L
                _isPlaying.value = false
            }
        }
    }

    fun play() {
        if (_currentSong.value == null && _playbackQueue.value.isNotEmpty()) {
            _currentSong.value = _playbackQueue.value.first()
        }
        val current = _currentSong.value ?: return

        if (!player.isPlaying && player.playbackState == Player.STATE_IDLE) {
            playSong(current)
        } else {
            player.play()
        }
    }

    fun pause() {
        player.pause()
    }

    fun skipToNext() {
        val queue = _playbackQueue.value
        val current = _currentSong.value
        if (queue.isEmpty() || current == null) return

        val index = queue.indexOfFirst { it.id == current.id }
        val nextSong = if (index != -1 && index < queue.size - 1) {
            queue[index + 1]
        } else {
            queue.first()
        }
        playSong(nextSong)
    }

    fun skipToPrevious() {
        val queue = _playbackQueue.value
        val current = _currentSong.value
        if (queue.isEmpty() || current == null) return

        val index = queue.indexOfFirst { it.id == current.id }
        val prevSong = if (index > 0) {
            queue[index - 1]
        } else {
            queue.last()
        }
        playSong(prevSong)
    }

    fun seekTo(position: Long) {
        val song = _currentSong.value ?: return
        val clamped = position.coerceIn(0L, song.duration)
        _currentPosition.value = clamped
        player.seekTo(clamped)
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive && player.isPlaying) {
                _currentPosition.value = player.currentPosition
                delay(100)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun startVuLoop() {
        vuJob?.cancel()
        vuJob = scope.launch(Dispatchers.Main) {
            var leftPeak = -60.0f
            var rightPeak = -60.0f
            var leftPeakTimer = 0
            var rightPeakTimer = 0

            while (isActive && player.isPlaying) {
                delay(16) // ~60 FPS dynamic and fluent rendering loop

                // Dynamic, rhythmic base volume simulation resembling music peaks
                val baseL = Random.nextFloat() * 20f - 18f
                val baseR = Random.nextFloat() * 20f - 18f

                // LFO factor to simulate regular drums / bass pulses naturally
                val timeFactor = (System.currentTimeMillis() % 1000) / 1000f
                val dynamicSwing = kotlin.math.sin(timeFactor * Math.PI * 4).toFloat() * 10f

                val targetL = (baseL + dynamicSwing).coerceIn(-48.0f, -1.0f)
                val targetR = (baseR + dynamicSwing).coerceIn(-48.0f, -2.0f)

                val currentL = _vuLevels.value.first
                val currentR = _vuLevels.value.second

                // Realistic ballistics: instant rise (attack coefficient 0.7), smooth drop (release coefficient 0.15)
                val nextL = currentL + (targetL - currentL) * (if (targetL > currentL) 0.7f else 0.15f)
                val nextR = currentR + (targetR - currentR) * (if (targetR > currentR) 0.7f else 0.15f)

                if (nextL > leftPeak) {
                    leftPeak = nextL
                    leftPeakTimer = 60 // Hold peak for 1 second at 60 FPS
                } else {
                    if (leftPeakTimer > 0) {
                        leftPeakTimer--
                    } else {
                        leftPeak = max(-60.0f, leftPeak - 0.5f) // realistic slow peak fallback
                    }
                }

                if (nextR > rightPeak) {
                    rightPeak = nextR
                    rightPeakTimer = 60
                } else {
                    if (rightPeakTimer > 0) {
                        rightPeakTimer--
                    } else {
                        rightPeak = max(-60.0f, rightPeak - 0.5f)
                    }
                }

                _vuLevels.value = Pair(nextL, nextR)
                _peakLevels.value = Pair(leftPeak, rightPeak)
            }
        }
    }

    private fun decayVuLevels() {
        vuJob?.cancel()
        vuJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val current = _vuLevels.value
                val peak = _peakLevels.value
                if (current.first <= -59.5f && current.second <= -59.5f) {
                    _vuLevels.value = Pair(-60.0f, -60.0f)
                    _peakLevels.value = Pair(-60.0f, -60.0f)
                    break
                }
                val nextL = max(-60.0f, current.first - 4.0f)
                val nextR = max(-60.0f, current.second - 4.0f)
                _vuLevels.value = Pair(nextL, nextR)
                _peakLevels.value = Pair(max(-60.0f, peak.first - 3.0f), max(-60.0f, peak.second - 3.0f))
                delay(16)
            }
        }
    }

    fun release() {
        scope.cancel()
        PlayerHolder.player?.release()
        PlayerHolder.player = null
    }
}
