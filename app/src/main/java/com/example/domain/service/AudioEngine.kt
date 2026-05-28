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

    private var visualizer: android.media.audio.PsychoVisualizer? = null

    init {
        try {
            player.removeListener(playbackListener)
            player.addListener(playbackListener)
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error adding playback listener in init: ${e.message}")
        }

        // Listen to the custom AudioProcessor real-time levels
        PlayerHolder.audioLevelExtractor.onLevelsUpdated = { levels ->
            // Fallback strategy: only use when PsychoVisualizer is not active
            if (visualizer == null) {
                _vuLevels.value = Pair(levels.leftDb, levels.rightDb)
                _peakLevels.value = Pair(levels.peakL, levels.peakR)
            }
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

    private val _dacRoutingStatus = MutableStateFlow<DacRoutingStatus>(DacRoutingStatus.Idle)
    val dacRoutingStatus: StateFlow<DacRoutingStatus> = _dacRoutingStatus.asStateFlow()

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

                startPlayerService()

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
                try {
                    player.stop()
                } catch (e: Exception) {
                    android.util.Log.e("AudioEngine", "Error stopping player in removeFromQueue: ${e.message}")
                }
                _currentSong.value = null
                _currentPosition.value = 0L
                _isPlaying.value = false
            }
        }
    }

    private fun startPlayerService() {
        try {
            val serviceIntent = android.content.Intent(context, PlayerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Failed to start PlayerService: ${e.message}")
        }
    }

    fun play() {
        if (_currentSong.value == null && _playbackQueue.value.isNotEmpty()) {
            _currentSong.value = _playbackQueue.value.first()
        }
        val current = _currentSong.value ?: return

        startPlayerService()

        try {
            val isPlaying = try { player.isPlaying } catch (e: Exception) { false }
            val state = try { player.playbackState } catch (e: Exception) { Player.STATE_IDLE }
            if (!isPlaying && state == Player.STATE_IDLE) {
                playSong(current)
            } else {
                player.play()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error in play(): ${e.message}")
        }
    }

    fun pause() {
        try {
            player.pause()
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error in pause(): ${e.message}")
        }
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
        try {
            player.seekTo(clamped)
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error in seekTo(): ${e.message}")
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            try {
                while (isActive) {
                    val isPlaying = try { player.isPlaying } catch (e: Exception) { false }
                    if (!isPlaying) break
                    
                    val pos = try { player.currentPosition } catch (e: Exception) { 0L }
                    _currentPosition.value = pos
                    delay(100)
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioEngine", "Error in progress loop: ${e.message}")
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun startVuLoop() {
        vuJob?.cancel()

        try {
            visualizer?.release()
            visualizer = null
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error releasing visualizer: ${e.message}")
        }

        val audioSessionId = try { player.audioSessionId } catch (e: Exception) { androidx.media3.common.C.AUDIO_SESSION_ID_UNSET }
        if (audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            try {
                // Check for RECORD_AUDIO permission first
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    android.util.Log.w("AudioEngine", "Visualizer unavailable (permission denied), using AudioProcessor VU")
                    visualizer = null
                } else {
                    val vis = android.media.audio.PsychoVisualizer(audioSessionId)
                    val captureSizeRange = android.media.audiofx.Visualizer.getCaptureSizeRange()
                    if (captureSizeRange != null && captureSizeRange.size >= 2) {
                        vis.captureSize = captureSizeRange[1] // Use max capture size
                    } else {
                        vis.captureSize = 1024
                    }
                    vis.setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: android.media.audiofx.Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            if (waveform != null && waveform.isNotEmpty()) {
                                var sum = 0.0
                                for (b in waveform) {
                                    val value = (b.toInt() and 0xFF) - 128
                                    sum += value * value
                                }
                                val rms = kotlin.math.sqrt(sum / waveform.size)
                                val rawDb = if (rms > 0.0) 20 * kotlin.math.log10(rms / 128.0) else -60.0
                                val leftDb = max(-60.0f, rawDb.toFloat())
                                val rightDb = max(-60.0f, (rawDb * 0.95f).toFloat())

                                // Dynamic Ballistics
                                val currentL = _vuLevels.value.first
                                val nextL = currentL + (leftDb - currentL) * (if (leftDb > currentL) 0.7f else 0.15f)
                                val currentR = _vuLevels.value.second
                                val nextR = currentR + (rightDb - currentR) * (if (rightDb > currentR) 0.7f else 0.15f)

                                val peak = _peakLevels.value
                                val peakL = max(peak.first - 0.5f, nextL)
                                val peakR = max(peak.second - 0.5f, nextR)

                                _vuLevels.value = Pair(nextL, nextR)
                                _peakLevels.value = Pair(peakL, peakR)
                            }
                        }

                        override fun onFftDataCapture(v: android.media.audiofx.Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                    }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, true, false)
                    vis.enabled = true
                    visualizer = vis
                }
            } catch (e: SecurityException) {
                android.util.Log.w("AudioEngine", "Visualizer unavailable (permission denied), using AudioProcessor VU")
                visualizer = null
            } catch (e: IllegalArgumentException) {
                android.util.Log.w("AudioEngine", "Visualizer not supported on this device, using AudioProcessor VU")
                visualizer = null
            } catch (e: Exception) {
                android.util.Log.e("AudioEngine", "Failed to start PsychoVisualizer: ${e.message}")
                visualizer = null
            }
        }

        vuJob = scope.launch(Dispatchers.Main) {
            try {
                while (isActive) {
                    val isPlaying = try { player.isPlaying } catch (e: Exception) { false }
                    if (!isPlaying) break
                    delay(32)
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioEngine", "Error in VU loop: ${e.message}")
            }
        }
    }

    private fun decayVuLevels() {
        vuJob?.cancel()
        try {
            visualizer?.release()
            visualizer = null
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error releasing visualizer in decay: ${e.message}")
        }

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

    fun routeOutputToDac() {
        _dacRoutingStatus.value = DacRoutingStatus.Routing
        val success = try {
            outputManager.routeToDac(player)
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "routeOutputToDac exception: ${e.message}")
            false
        }
        if (success) {
            val dacName = outputManager.getConnectedDacDevice()?.productName?.toString() ?: "USB DAC"
            _dacRoutingStatus.value = DacRoutingStatus.Success(dacName)
        } else {
            _dacRoutingStatus.value = DacRoutingStatus.Failed("Device busy or enumeration failed — audio routed to speaker")
        }
    }

    fun release() {
        scope.cancel()
        try {
            visualizer?.release()
            visualizer = null
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error releasing visualizer in release: ${e.message}")
        }

        // If PlayerService is actively running (sharedPlayer is non-null) and playing, do NOT release the player
        val servicePlayer = PlayerService.sharedPlayer
        try {
            val isPlaying = if (servicePlayer != null) {
                try { servicePlayer.isPlaying } catch (e: Exception) { false }
            } else {
                false
            }
            if (servicePlayer != null && isPlaying) {
                android.util.Log.d("AudioEngine", "AudioEngine released but PlayerService is active. Keeping player alive.")
            } else {
                val p = PlayerHolder.player
                if (p != null) {
                    try {
                        p.release()
                    } catch (e: Exception) {
                        android.util.Log.e("AudioEngine", "Error releasing PlayerHolder.player: ${e.message}")
                    }
                }
                PlayerHolder.player = null
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioEngine", "Error in release(): ${e.message}")
            PlayerHolder.player = null
        }
    }
}

sealed class DacRoutingStatus {
    object Idle : DacRoutingStatus()
    object Routing : DacRoutingStatus()
    data class Success(val deviceName: String) : DacRoutingStatus()
    data class Failed(val reason: String) : DacRoutingStatus()
}
