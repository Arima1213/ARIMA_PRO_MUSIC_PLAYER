package com.example.domain.service

import com.example.domain.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.random.Random

class AudioEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var progressJob: Job? = null
    private var vuJob: Job? = null

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
        if (!queue.contains(song)) {
            _playbackQueue.value = queue + song
        }
        _currentSong.value = song
        _currentPosition.value = 0L
        play()
    }

    fun play() {
        if (_currentSong.value == null && _playbackQueue.value.isNotEmpty()) {
            _currentSong.value = _playbackQueue.value.first()
        }
        if (_currentSong.value == null) return

        _isPlaying.value = true
        startProgressLoop()
        startVuLoop()
    }

    fun pause() {
        _isPlaying.value = false
        stopProgressLoop()
        decayVuLevels()
    }

    fun skipToNext() {
        val queue = _playbackQueue.value
        val current = _currentSong.value
        if (queue.isEmpty() || current == null) return

        val index = queue.indexOfFirst { it.id == current.id }
        if (index != -1 && index < queue.size - 1) {
            _currentSong.value = queue[index + 1]
            _currentPosition.value = 0L
        } else {
            // Wrap or stop
            _currentSong.value = queue.first()
            _currentPosition.value = 0L
        }
        if (_isPlaying.value) {
            play()
        }
    }

    fun skipToPrevious() {
        val queue = _playbackQueue.value
        val current = _currentSong.value
        if (queue.isEmpty() || current == null) return

        val index = queue.indexOfFirst { it.id == current.id }
        if (index > 0) {
            _currentSong.value = queue[index - 1]
            _currentPosition.value = 0L
        } else {
            // Wrap-around to end
            _currentSong.value = queue.last()
            _currentPosition.value = 0L
        }
        if (_isPlaying.value) {
            play()
        }
    }

    fun seekTo(position: Long) {
        val song = _currentSong.value ?: return
        val clamped = position.coerceIn(0L, song.duration)
        _currentPosition.value = clamped
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(100)
                val current = _currentPosition.value
                val duration = _currentSong.value?.duration ?: 0L
                if (current + 100 >= duration) {
                    _currentPosition.value = duration
                    withContext(Dispatchers.Main) {
                        skipToNext()
                    }
                } else {
                    _currentPosition.value = current + 100
                }
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun startVuLoop() {
        vuJob?.cancel()
        vuJob = scope.launch {
            var leftPeak = -60.0f
            var rightPeak = -60.0f
            var leftPeakTimer = 0
            var rightPeakTimer = 0

            while (isActive && _isPlaying.value) {
                delay(40) // ~25 FPS animation update

                // Simulate realistic audio waves (RMS dynamics)
                val baseL = Random.nextFloat() * 25f - 22f // range -22 to +3 dB major dynamic
                val baseR = Random.nextFloat() * 25f - 22f

                // LFO simulation for regular audio rhythm peaks
                val timeFactor = (System.currentTimeMillis() % 10000) / 10000f
                val dynamicSwing = kotlin.math.sin(timeFactor * Math.PI * 4).toFloat() * 10f

                val targetL = (baseL + dynamicSwing).coerceIn(-48.0f, -1.5f)
                val targetR = (baseR + dynamicSwing).coerceIn(-48.0f, -2.5f)

                // Smooth interpolation for decay and attack
                val currentL = _vuLevels.value.first
                val currentR = _vuLevels.value.second

                // Fast attack (0.7 coeff), slower release (0.3 coeff)
                val nextL = currentL + (targetL - currentL) * (if (targetL > currentL) 0.6f else 0.25f)
                val nextR = currentR + (targetR - currentR) * (if (targetR > currentR) 0.6f else 0.25f)

                // Manage Peak holds
                if (nextL > leftPeak) {
                    leftPeak = nextL
                    leftPeakTimer = 25 // Hold for 1 second (25 frames)
                } else {
                    if (leftPeakTimer > 0) {
                        leftPeakTimer--
                    } else {
                        leftPeak = max(-60.0f, leftPeak - 1.5f) // slow drop
                    }
                }

                if (nextR > rightPeak) {
                    rightPeak = nextR
                    rightPeakTimer = 25
                } else {
                    if (rightPeakTimer > 0) {
                        rightPeakTimer--
                    } else {
                        rightPeak = max(-60.0f, rightPeak - 1.5f)
                    }
                }

                _vuLevels.value = Pair(nextL, nextR)
                _peakLevels.value = Pair(leftPeak, rightPeak)
            }
        }
    }

    private fun decayVuLevels() {
        vuJob?.cancel()
        vuJob = scope.launch {
            while (isActive) {
                val current = _vuLevels.value
                val peak = _peakLevels.value
                if (current.first <= -59.5f && current.second <= -59.5f) {
                    _vuLevels.value = Pair(-60.0f, -60.0f)
                    _peakLevels.value = Pair(-60.0f, -60.0f)
                    break
                }
                // Smoothly decay to zero when paused
                val nextL = max(-60.0f, current.first - 4.0f)
                val nextR = max(-60.0f, current.second - 4.0f)
                _vuLevels.value = Pair(nextL, nextR)
                _peakLevels.value = Pair(max(-60.0f, peak.first - 3.0f), max(-60.0f, peak.second - 3.0f))
                delay(30)
            }
        }
    }

    fun release() {
        scope.cancel()
    }
}
