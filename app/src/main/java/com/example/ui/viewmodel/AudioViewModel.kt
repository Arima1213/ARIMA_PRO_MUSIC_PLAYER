package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.repository.AudioRepository
import com.example.domain.model.EqPreset
import com.example.domain.model.Folder
import com.example.domain.model.Song
import com.example.domain.service.AudioEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AudioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AudioRepository(db)
    val audioEngine = com.example.domain.service.AudioEngine(application)
    private val scanner = com.arima.pro.core.audio.LibraryScanner(application)
    private val dacController = com.arima.pro.core.audio.DacController(application)
    
    private val _dacState = MutableStateFlow<com.arima.pro.core.audio.DacState>(
        com.arima.pro.core.audio.DacState.NotDetected
    )
    val dacState: StateFlow<com.arima.pro.core.audio.DacState> = _dacState.asStateFlow()

    private val _dacHotplugEvent = MutableStateFlow<DacHotplugEvent?>(null)
    val dacHotplugEvent: StateFlow<DacHotplugEvent?> = _dacHotplugEvent.asStateFlow()

    fun clearDacHotplugEvent() {
        _dacHotplugEvent.value = null
    }

    fun setDacHotplugEvent(event: DacHotplugEvent?) {
        _dacHotplugEvent.value = event
    }

    fun refreshDacDetection() {
        _dacState.value = com.arima.pro.core.audio.DacState.Scanning("Detecting USB device...")
        dacController.refreshDetection()
    }

    private var usbReceiver: android.content.BroadcastReceiver? = null

    // --- Tab Navigation States ---
    private val _currentTab = MutableStateFlow("library") // "library", "player", "dac", "settings", "format_variants"
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _currentLibraryTab = MutableStateFlow("songs") // "songs", "albums", "artists", "folders"
    val currentLibraryTab: StateFlow<String> = _currentLibraryTab.asStateFlow()

    // --- Search Query ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // --- Library Data Flows ---
    val allSongs: StateFlow<List<Song>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders: StateFlow<List<Folder>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered songs by search query
    val filteredSongs: StateFlow<List<Song>> = combine(allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Precomputed lists of albums and artists derived from allSongs
    val albumList: StateFlow<List<AlbumItem>> = allSongs.map { songs ->
        songs.groupBy { it.album }.map { (album, songList) ->
            AlbumItem(
                title = album,
                artist = songList.firstOrNull()?.artist ?: "Unknown Artist",
                tacksCount = songList.size,
                formatCode = songList.firstOrNull()?.format ?: "FLAC",
                sampleRate = songList.firstOrNull()?.sampleRate ?: "96kHz"
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artistList: StateFlow<List<ArtistItem>> = allSongs.map { songs ->
        songs.groupBy { it.artist }.map { (artist, songList) ->
            ArtistItem(
                name = artist,
                tracksCount = songList.size,
                albumsCount = songList.map { it.album }.distinct().size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Audio Settings States ---
    val resamplingRate = MutableStateFlow("192 kHz") // "Off (Bit-perfect)", "96 kHz", "192 kHz", "384 kHz"
    val ditheringEnabled = MutableStateFlow(true)
    val volumeNormalizationEnabled = MutableStateFlow(true)
    val gaplessPlaybackEnabled = MutableStateFlow(true)
    val crossfadeDuration = MutableStateFlow("Off") // "Off", "1s", "3s", "5s"

    // --- DAC Settings States ---
    val dacExclusiveMode = MutableStateFlow(true)
    val usbBufferSize = MutableStateFlow("Max Latency (Stable)") // "Safe (Medium)", "Max Dynamic", "Kernel Direct"
    val bitPerfectMode = MutableStateFlow(true)
    val dsdNativeMode = MutableStateFlow("DSD256 Native") // "DoP Marker", "Native DSD256", "Direct Direct"
    val dopModeEnabled = MutableStateFlow(false)

    // --- Equalizer States ---
    val equalizerEnabled = MutableStateFlow(false)
    val currentPreset = MutableStateFlow("FLAT") // "FLAT", "BASS BOOST", "VOCAL FOCUS", "TREBLE AIR", "CUSTOM"
    val preampGain = MutableStateFlow(0.0f) // -12dB to +12dB
    private val _bandGains = MutableStateFlow(List(10) { 0.0f }) // 10 bands
    val bandGains: StateFlow<List<Float>> = _bandGains.asStateFlow()

    // --- Dialogue & Scanning progress states ---
    val isScanning = MutableStateFlow(false)
    val scanProgress = MutableStateFlow(0f)
    val scanStepText = MutableStateFlow("")
    val scanFilesScanned = MutableStateFlow(0)
    val scanFilesTotal = MutableStateFlow(0)
    val scanTracksFound = MutableStateFlow(0)
    val showAddFolderDialog = MutableStateFlow(false)
    val showScanningProgressDialog = MutableStateFlow(false)
    val showEqualizerPanel = MutableStateFlow(false)

    private var scanJob: Job? = null

    init {
        seedInitialDataIfNeeded()
        
        // Collect dacState updates from DacController into our _dacState MutableStateFlow and emit hotplug events on transition
        viewModelScope.launch {
            var lastState: com.arima.pro.core.audio.DacState = com.arima.pro.core.audio.DacState.NotDetected
            dacController.detectDac().collect { state ->
                _dacState.value = state
                if (state is com.arima.pro.core.audio.DacState.Detected && lastState !is com.arima.pro.core.audio.DacState.Detected) {
                    val info = state.dacInfo
                    audioEngine.showDacMissingDialog.value = false
                    _dacHotplugEvent.value = DacHotplugEvent(
                        type = DacHotplugType.DETECTED,
                        dacName = info.name,
                        dacInfo = info
                    )
                } else if (state is com.arima.pro.core.audio.DacState.NotDetected && lastState is com.arima.pro.core.audio.DacState.Detected) {
                    _dacHotplugEvent.value = DacHotplugEvent(
                        type = DacHotplugType.DISCONNECTED,
                        dacName = (lastState as com.arima.pro.core.audio.DacState.Detected).dacInfo.name,
                        dacInfo = null
                    )
                }
                lastState = state
            }
        }

        // Broadcaster for USB Attach/Detach with 500ms stabilization delay and auto output routing / playback pause
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                when (intent.action) {
                    android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            _dacState.value = com.arima.pro.core.audio.DacState.Scanning("Detecting USB device...")
                            delay(500) // Tunggu device stabil
                            dacController.refreshDetection()
                        }
                    }
                    android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            _dacState.value = com.arima.pro.core.audio.DacState.NotDetected
                            if (audioEngine.isPlaying.value) {
                                audioEngine.pause()
                            }
                        }
                    }
                }
            }
        }
        usbReceiver = receiver

        val filter = android.content.IntentFilter().apply {
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        try {
            androidx.core.content.ContextCompat.registerReceiver(
                application,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Throwable) {
            try {
                application.registerReceiver(receiver, filter)
            } catch (ex: Throwable) {
                android.util.Log.e("AudioViewModel", "Failed to register usbReceiver: ${ex.message}")
            }
        }
        // Sync Equalizer with AudioEngine
        viewModelScope.launch {
            combine(equalizerEnabled, bandGains, bitPerfectMode) { enabled, gains, bitPerfect ->
                Triple(enabled && !bitPerfect, gains, bitPerfect)
            }.collect { (enabled, gains, _) ->
                audioEngine.applyEqualizer(enabled, gains)
            }
        }
        // Sync DAC Exclusive Mode
        viewModelScope.launch {
            dacExclusiveMode.collect { enabled ->
                audioEngine.dacExclusiveModeActive = enabled
                dacController.enforceExclusiveMode(enabled)
            }
        }
        // Sync Resampling Rate
        viewModelScope.launch {
            combine(resamplingRate, bitPerfectMode) { rate, bitPerfect ->
                if (bitPerfect) "Bit-perfect" else rate
            }.collect { rate ->
                com.arima.pro.core.audio.PlayerHolder.applyResampling(rate)
            }
        }
        // Sync Dithering Trigger
        viewModelScope.launch {
            combine(ditheringEnabled, bitPerfectMode) { enabled, bitPerfect ->
                enabled && !bitPerfect
            }.collect { enabled ->
                com.arima.pro.core.audio.PlayerHolder.applyDithering(enabled)
            }
        }
        // Sync USB Buffer Size and rebuild AudioTrack
        viewModelScope.launch {
            usbBufferSize.collect { size ->
                com.arima.pro.core.audio.PlayerHolder.applyBufferSize(application, size)
            }
        }
        // Sync DSD Playback Mode
        viewModelScope.launch {
            dsdNativeMode.collect { mode ->
                com.arima.pro.core.audio.PlayerHolder.dopModeActive = 
                    mode.contains("DoP", ignoreCase = true) || mode.contains("Marker", ignoreCase = true)
            }
        }
        // Sync Bit-perfect Active state on changes
        viewModelScope.launch {
            bitPerfectMode.collect { enabled ->
                com.arima.pro.core.audio.PlayerHolder.bitPerfectActive = enabled
                if (enabled) {
                    resamplingRate.value = "Bit-perfect"
                    ditheringEnabled.value = false
                    equalizerEnabled.value = false
                }
            }
        }
        // Restore persistable URI permissions on app restart
        viewModelScope.launch {
            try {
                repository.allFolders.first().forEach { folder ->
                    if (folder.path.startsWith("content://")) {
                        val uri = android.net.Uri.parse(folder.path)
                        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        try {
                            application.contentResolver.takePersistableUriPermission(uri, takeFlags)
                        } catch (e: Exception) {
                            // Already active or error
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        // Monitor USB DAC attachment and auto pause if disconnected while actively streaming
        viewModelScope.launch {
            var lastDacDetected = false
            dacState.collect { state ->
                val isDetected = state is com.arima.pro.core.audio.DacState.Detected
                if (lastDacDetected && !isDetected) {
                    if (audioEngine.isPlaying.value) {
                        audioEngine.pause()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                application,
                                "DAC Terputus! Pemutaran dihentikan otomatis (DAC Exclusive Mode Aktif).",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                lastDacDetected = isDetected
            }
        }
    }

    private fun seedInitialDataIfNeeded() {
        viewModelScope.launch {
            // First check if folders are empty, if so, seed standard premium sample directories
            repository.allFolders.first().let { folders ->
                if (folders.isEmpty()) {
                    repository.insertFolder(Folder("/storage/music/hi-res-flac", 214, "82.4 GB", "4 hrs ago"))
                    repository.insertFolder(Folder("/mnt/nas/dsd_archives", 56, "140.2 GB", "1 day ago"))
                    repository.insertFolder(Folder("/sdcard/Downloads/wav_masters", 12, "4.1 GB", "10 mins ago"))
                }
            }

            // Check if songs are empty, if so, seed standard matching audiophile songs
            repository.allSongs.first().let { songs ->
                if (songs.isEmpty()) {
                    val demoSongs = listOf(
                        Song(
                            title = "Midnight Vultures",
                            artist = "Analog Drift",
                            album = "Resonance",
                            duration = 372000L,
                            format = "WAV",
                            sampleRate = "192kHz",
                            bitDepth = "32-bit",
                            fileSize = "2.41GB",
                            path = "/sdcard/Downloads/wav_masters/midnight_vultures.wav",
                            folderPath = "/sdcard/Downloads/wav_masters"
                        ),
                        Song(
                            title = "Resonance Horizon",
                            artist = "The Tube Amplifiers",
                            album = "Acoustic Horizon",
                            duration = 275000L,
                            format = "WAV",
                            sampleRate = "44.1kHz",
                            bitDepth = "16-bit",
                            fileSize = "450MB",
                            path = "/sdcard/Downloads/wav_masters/resonance_horizon.wav",
                            folderPath = "/sdcard/Downloads/wav_masters"
                        ),
                        Song(
                            title = "Quantum Acoustics",
                            artist = "Dr. Frequency",
                            album = "Quantum Realities",
                            duration = 495000L,
                            format = "DSD",
                            sampleRate = "5.6MHz",
                            bitDepth = "1-bit",
                            fileSize = "4.80GB",
                            path = "/mnt/nas/dsd_archives/quantum_acoustics.dsf",
                            folderPath = "/mnt/nas/dsd_archives"
                        ),
                        Song(
                            title = "City Lights in Hi-Fi",
                            artist = "Neon Quintet",
                            album = "Metropolitan Session",
                            duration = 308000L,
                            format = "FLAC",
                            sampleRate = "96kHz",
                            bitDepth = "24-bit",
                            fileSize = "180MB",
                            path = "/storage/music/hi-res-flac/city_lights.flac",
                            folderPath = "/storage/music/hi-res-flac"
                        ),
                        Song(
                            title = "Ethereal Pulse",
                            artist = "Void Sector",
                            album = "Deep Nebulae",
                            duration = 450000L,
                            format = "FLAC",
                            sampleRate = "192kHz",
                            bitDepth = "24-bit",
                            fileSize = "285MB",
                            path = "/storage/music/hi-res-flac/ethereal_pulse.flac",
                            folderPath = "/storage/music/hi-res-flac"
                        ),
                        Song(
                            title = "Neon Shadows",
                            artist = "Cyber Drift",
                            album = "Analog Drift",
                            duration = 230000L,
                            format = "WAV",
                            sampleRate = "384kHz",
                            bitDepth = "32-bit",
                            fileSize = "312MB",
                            path = "/sdcard/Downloads/wav_masters/neon_shadows.wav",
                            folderPath = "/sdcard/Downloads/wav_masters"
                        ),
                        Song(
                            title = "Obsidian Silence",
                            artist = "Minimalist",
                            album = "Null State",
                            duration = 405000L,
                            format = "FLAC",
                            sampleRate = "176.4kHz",
                            bitDepth = "24-bit",
                            fileSize = "215MB",
                            path = "/storage/music/hi-res-flac/obsidian_silence.flac",
                            folderPath = "/storage/music/hi-res-flac"
                        )
                    )
                    repository.insertSongs(demoSongs)
                    audioEngine.setQueue(demoSongs)
                } else {
                    audioEngine.setQueue(songs)
                }
            }
        }
    }

    // --- Navigation Controls ---
    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    fun selectLibraryTab(tab: String) {
        _currentLibraryTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Song Interaction Controls ---
    fun playSong(song: Song) {
        try {
            val context = getApplication<Application>()
            val intent = android.content.Intent(context, com.example.domain.service.PlayerService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("AudioViewModel", "Failed to start service: ${e.message}")
        }
        audioEngine.playSong(song)
        selectTab("player") // open Now Playing screen immediately
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.updateFavorite(song.id, !song.isFavorite)
        }
    }

    // --- Directory Scanner Logic ---
    fun addNewFolder(path: String) {
        viewModelScope.launch {
            val newFolder = Folder(
                path = path,
                fileCount = 0,
                totalSize = "0 B",
                lastScan = "Never"
            )
            repository.insertFolder(newFolder)
            showAddFolderDialog.value = false
            triggerScan(path)
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
        }
    }

    fun triggerScan(folderPath: String) {
        scanJob?.cancel()
        isScanning.value = true
        showScanningProgressDialog.value = true
        scanProgress.value = 0f
        scanFilesScanned.value = 0
        scanTracksFound.value = 0
        scanFilesTotal.value = 0

        scanJob = viewModelScope.launch {
            try {
                scanner.scanFolder(folderPath).collect { progress ->
                    scanStepText.value = progress.step
                    scanFilesScanned.value = progress.filesScanned
                    scanFilesTotal.value = progress.filesTotal
                    scanTracksFound.value = progress.tracksFound
                    scanProgress.value = progress.progress
                }
            } catch (e: Exception) {
                scanStepText.value = "Scan error: ${e.localizedMessage}"
            } finally {
                delay(1200)
                isScanning.value = false
                showScanningProgressDialog.value = false
                val songs = repository.allSongs.first()
                audioEngine.setQueue(songs)
            }
        }
    }

    // --- Equalizer Controls ---
    fun toggleEqualizer(enabled: Boolean) {
        equalizerEnabled.value = enabled
    }

    fun setPreamp(gain: Float) {
        preampGain.value = gain
    }

    fun setBandGain(index: Int, gain: Float) {
        val current = _bandGains.value.toMutableList()
        current[index] = gain
        _bandGains.value = current
        currentPreset.value = "CUSTOM"
    }

    fun selectPreset(preset: String) {
        currentPreset.value = preset
        when (preset) {
            "FLAT" -> {
                _bandGains.value = List(10) { 0.0f }
                preampGain.value = 0.0f
            }
            "BASS BOOST" -> {
                _bandGains.value = listOf(8.0f, 6.5f, 4.0f, 1.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
                preampGain.value = -3.0f
            }
            "VOCAL FOCUS" -> {
                _bandGains.value = listOf(-2.0f, -1.0f, 1.0f, 2.5f, 4.0f, 3.5f, 2.0f, 1.0f, 0.0f, -1.0f)
                preampGain.value = -1.5f
            }
            "TREBLE AIR" -> {
                _bandGains.value = listOf(-1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.5f, 3.0f, 5.5f, 7.0f, 9.5f)
                preampGain.value = -4.0f
            }
        }
    }

    fun resetEqualizer() {
        selectPreset("FLAT")
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        dacController.unregister()
        usbReceiver?.let {
            try {
                getApplication<Application>().unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

// Support definitions for DAC Hotplug
data class DacHotplugEvent(
    val type: DacHotplugType,
    val dacName: String?,
    val dacInfo: com.arima.pro.core.audio.DacInfo?,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DacHotplugType { DETECTED, DISCONNECTED }

// Support definitions for Album/Artist transformations
data class AlbumItem(
    val title: String,
    val artist: String,
    val tacksCount: Int,
    val formatCode: String,
    val sampleRate: String
)

data class ArtistItem(
    val name: String,
    val tracksCount: Int,
    val albumsCount: Int
)
