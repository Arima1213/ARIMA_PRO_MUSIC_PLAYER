# Arima Pro Player — Fix Prompts for AI Engineer

**Repo**: `arima-pro-player` (cloned to `C:\Users\ASUS\Downloads\arima-pro-player`)
**Latest Commit**: `2871c5a` — `feat(audio): implement automatic USB DAC routing`
**Generated**: 2026-05-29

---

## BUGFIX #1 (KRITICAL) — DAC Hotplug: No User Notification + No Exclusive Mode Request

**Files affected**:
- `app/src/main/java/com/example/ui/viewmodel/AudioViewModel.kt`
- `app/src/main/java/com/example/ui/screens/DacMonitorScreen.kt`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/arima/pro/core/audio/DacController.kt`

**Root cause**: Saat USB DAC attach via BroadcastReceiver, app call `refreshDetection()` + `routeOutputToDac()` tapi **NOLAH tidak ada notifikasi ke user**. User nggak tahu DAC sudah terhubung. Tidak ada dialog / bottom sheet yang muncul otomatis tanpa user harus buka DAC Monitor screen.

**Fix requirements**:

### 1. AudioViewModel — Tambahkan DacHotplugEvent StateFlow

```kotlin
// Tambahkan di AudioViewModel.kt:

private val _dacHotplugEvent = MutableStateFlow<DacHotplugEvent?>(null)
val dacHotplugEvent: StateFlow<DacHotplugEvent?> = _dacHotplugEvent.asStateFlow()

data class DacHotplugEvent(
    val type: DacHotplugType,
    val dacName: String?,
    val dacInfo: com.arima.pro.core.audio.DacInfo?,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DacHotplugType { DETECTED, DISCONNECTED }
```

### 2. AudioViewModel.init BroadcastReceiver — emit event on detect

Di `ACTION_USB_DEVICE_ATTACHED` block, setelah DAC detected:

```kotlin
// Di AudioViewModel.kt init block, inside BroadcastReceiver onReceive:
if (dacState.value is com.arima.pro.core.audio.DacState.Detected) {
    val info = (dacState.value as com.arima.pro.core.audio.DacState.Detected).dacInfo
    _dacHotplugEvent.value = DacHotplugEvent(
        type = DacHotplugType.DETECTED,
        dacName = info.name,
        dacInfo = info
    )
}
```

### 3. MainActivity — tampilkan DacHotplugDialog saat event emit

```kotlin
// Di MainActivity atau MainApp composable, collect dacHotplugEvent:

val dacHotplugEvent by viewModel.dacHotplugEvent.collectAsState()

dacHotplugEvent?.let { event ->
    DacHotplugDialog(
        dacInfo = event.dacInfo!!,
        onDismiss = { viewModel.dacHotplugEvent.value = null },
        onEnableExclusiveMode = {
            viewModel.dacExclusiveMode.value = true
            viewModel.audioEngine.routeOutputToDac()
            viewModel.dacHotplugEvent.value = null
        },
        onPlayAnyway = {
            viewModel.dacExclusiveMode.value = false
            viewModel.dacHotplugEvent.value = null
        }
    )
}
```

### 4. DacHotplugDialog.kt — Compose Dialog Component

Create new file: `app/src/main/java/com/example/ui/components/DacHotplugDialog.kt`

```kotlin
@Composable
fun DacHotplugDialog(
    dacInfo: com.arima.pro.core.audio.DacInfo,
    onDismiss: () -> Unit,
    onEnableExclusiveMode: () -> Unit,
    onPlayAnyway: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("USB DAC Terdeteksi!", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column {
                Text(
                    dacInfo.name ?: "Unknown DAC",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    dacInfo.chip ?: "Unknown Chip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Max ${dacInfo.maxSampleRate / 1000}kHz / ${dacInfo.bitDepth}-bit",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Mau enable DAC EXCLUSIVE MODE untuk bit-perfect output?",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onEnableExclusiveMode) {
                Text("Exclusive Mode")
            }
        },
        dismissButton = {
            TextButton(onClick = onPlayAnyway) {
                Text("Nggak dulu")
            }
        }
    )
}
```

**Constraint**:
- Dialog HARUS muncul otomatis tanpa user harus buka screen tertentu
- Jika DAC disconnect (ACTION_USB_DEVICE_DETACHED), emit event untuk show "DAC Terputus" snackbar
- Dialog tidak blocking — user bisa dismiss dengan tap outside

---

## BUGFIX #2 (KRITICAL) — DacMonitorScreen Refresh Button is FAKE

**Files affected**:
- `app/src/main/java/com/example/ui/screens/DacMonitorScreen.kt`
- `app/src/main/java/com/example/ui/viewmodel/AudioViewModel.kt`

**Root cause**: Refresh button di `DacMonitorScreen.kt:42-47` cuma set `isRefreshing = true` → delay 1 detik → set `false`. **Nggak pernah call `dacController.refreshDetection()`**.

```kotlin
// Current BROKEN code:
LaunchedEffect(isRefreshing) {
    if (isRefreshing) {
        kotlinx.coroutines.delay(1000)
        isRefreshing = false // ← cuma animation, TIDAK call refreshDetection()
    }
}
```

**Fix requirements**:

### 1. AudioViewModel — Tambahkan refresh method

```kotlin
fun refreshDacDetection() {
    dacController.refreshDetection()
}
```

### 2. DacMonitorScreen — Fix refresh button call actual method

```kotlin
// Ganti IconButton onClick:
IconButton(onClick = {
    isRefreshing = true
    viewModel.refreshDacDetection()
}) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }

// Ganti LaunchedEffect — wait until dacState changes, bukan fixed delay:
LaunchedEffect(dacState) {
    isRefreshing = false
}

// Opsional: polling with timeout untuk robustness:
fun refreshDacDetectionWithPolling() {
    viewModelScope.launch {
        repeat(15) { attempt ->
            dacController.refreshDetection()
            if (dacState.value is com.arima.pro.core.audio.DacState.Detected) {
                isRefreshing = false
                return@launch
            }
            delay(200)
        }
        isRefreshing = false // timeout — detection failed
    }
}
```

**Constraint**:
- Refresh button harus melakukan DETECTION, bukan cuma animasi
- `isRefreshing` harus jadi `false` ketika dacState berubah, bukan fixed 1 detik
- Handle case: DAC tidak terdeteksi setelah retry (tampilkan error state)

---

## BUGFIX #3 (TINGGI) — Single-Shot 500ms Delay → DAC Detection Miss

**Files affected**:
- `app/src/main/java/com/arima/pro/core/audio/DacController.kt`
- `app/src/main/java/com/example/ui/viewmodel/AudioViewModel.kt`
- `app/src/main/java/com/example/ui/screens/DacMonitorScreen.kt`

**Root cause**: `delay(500)` sebelum detection terlalu singkat. USB OTG enumeration di Android butuh **500ms–2000ms** tergantung device dan cable. Kalau enumeration belum selesai, `audioManager.getDevices(GET_DEVICES_OUTPUTS)` return empty — DAC tidak terdeteksi.

**Fix requirements**:

### 1. DacController — Robust Detection dengan Polling + Retry

```kotlin
// Di DacController.kt, tambah method baru:

fun updateDacDetectionWithPolling(maxRetries: Int = 10, retryDelayMs: Long = 250) {
    // Retry detection dengan polling — emit result FINAL hanya setelah:
    // (a) 2x consecutive detection sama → stable, atau
    // (b) maxRetries tercapai → emit last result
    // CUMA EMIT SATU KALI — saat result stable
}

// Contoh implementasi:
fun updateDacDetectionWithPolling(maxRetries: Int = 10, retryDelayMs: Long = 250) {
    var lastResult: DacState = DacState.NotDetected
    var consecutiveMatches = 0

    for (attempt in 0 until maxRetries) {
        val result = performDacDetection() // logic detection internal
        if (result == lastResult) {
            consecutiveMatches++
            if (consecutiveMatches >= 2) {
                // Stable — emit final result
                _dacState.value = result
                return
            }
        } else {
            consecutiveMatches = 0
            lastResult = result
        }
        Thread.sleep(retryDelayMs)
    }
    // Timeout — emit last result
    _dacState.value = lastResult
}
```

### 2. DacState — Tambahkan Scanning State

```kotlin
// Di com.arima.pro.core.audio.DacState:

sealed class DacState {
    object NotDetected : DacState()
    data class Scanning(val message: String) : DacState() // ← NEW
    data class Detected(val dacInfo: DacInfo) : DacState()
}
```

### 3. BroadcastReceiver — Emit Scanning state dulu, baru async polling

```kotlin
// Di DacController init block, USB receiver:
ACTION_USB_DEVICE_ATTACHED -> {
    _dacState.value = DacState.Scanning("Detecting USB device...")
    updateDacDetectionWithPolling() // async, emit final result when stable
}
```

### 4. DacMonitorScreen — Show Scanning State di UI

```kotlin
// Di CURRENT STATUS section, handle DacState.Scanning:
when (val state) {
    is com.arima.pro.core.audio.DacState.Scanning -> {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFFFC107)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFC107)
            )
        }
    }
    // ... existing NotDetected and Detected handling
}
```

**Constraint**:
- Jangan emit PENDING state — langsung emit `Scanning` dengan message
- Polling interval 250ms, max 10 retries (2.5 detik timeout)
- Final result baru di-emit setelah stable (2x consecutive sama)
- Jika timeout, display "DAC tidak terdeteksi" dengan retry button

---

## BUGFIX #4 (TINGGI) — RECORD_AUDIO Permission Missing → Visualizer Always Fail

**Files affected**:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/ui/screens/PermissionsScreen.kt`
- `app/src/main/java/com/example/domain/service/AudioEngine.kt`

**Root cause**: `AudioEngine.startVuLoop()` pakai Android Visualizer API yang butuh `android.permission.RECORD_AUDIO`. Permission ini **nggak declared di AndroidManifest**, jadi Visualizer gagal silently setiap kali — VU meter fallback ke AudioLevelExtractor tanpa user tahu.

**Fix requirements**:

### 1. AndroidManifest.xml — Tambahkan RECORD_AUDIO permission

```xml
<!-- Di dalam <manifest> tag, setelah <uses-permission> yang lain: -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 2. PermissionsScreen.kt — Add RECORD_AUDIO ke permission list

```kotlin
// Di composePermissionList atau wherever permissions di-request:

val permissionsToRequest = remember {
    listOf(
        Manifest.permission.RECORD_AUDIO,  // ← ADD THIS
        Manifest.permission.BLUETOOTH_CONNECT, // if applicable
        Manifest.permission.BLUETOOTH_SCAN
    )
}
```

### 3. AudioEngine.startVuLoop() — Handle Visualizer Failure Gracefully

Visualizer sudah ada fallback via AudioLevelExtractor di PlayerHolder — ini good, nggak perlu ubah logic utama. Tapi perlu:

```kotlin
// Di AudioEngine.startVuLoop(), saat inisialisasi Visualizer:
private fun initVisualizer() {
    try {
        val sessionId = player?.audioSessionId ?: return
        visualizer = PsychoVisualizer(0).apply {
            setEnabled(true)
        }
        // Note: PsychoVisualizer needs audio session, not audio session ID
        // If using android.media.audiofx.Visualizer:
        val v = android.media.audiofx.Visualizer(sessionId)
        v.setDataCaptureListener(
            android.media.audiofx.Visualizer.OnDataCaptureListener { waveform, samplingRate ->
                val rms = calculateRmsFromWaveform(waveform)
                _vuLevels.value = Pair(rms, rms) // L/R sama dari waveform
            },
            android.media.audiofx.Visualizer.getMaxCaptureRate(),
            false,
            true // true = waveform, false = FFT
        )
        v.setEnabled(true)
    } catch (e: SecurityException) {
        // Permission not granted — use AudioLevelExtractor fallback
        Log.w(TAG, "Visualizer unavailable (permission denied), using AudioProcessor VU")
        visualizer = null
    } catch (e: IllegalArgumentException) {
        // Device doesn't support Visualizer
        Log.w(TAG, "Visualizer not supported on this device, using AudioProcessor VU")
        visualizer = null
    }
}
```

### 4. DacMonitorScreen — Show RECORD_AUDIO Permission Status di UI

Tambah line di HARDWARE CAPABILITIES section:

```kotlin
// Di DacMonitorScreen, HARDWARE CAPABILITIES table:
"VU Meter (Real Audio)" to if (hasRecordAudioPermission) "Granted" else "Permission Required"
```

**Constraint**:
- RECORD_AUDIO adalah "dangerous" permission — harus request runtime, bukan cuma declare di manifest
- User harus bisa lihat permission status di DAC Monitor screen
- Fallback ke AudioLevelExtractor (AudioProcessor chain) tetap jalan kalau Visualizer gagal

---

## BUGFIX #5 (TINGGI) — routeToDac() Gagal Diam-diam → Audio ke Speaker

**Files affected**:
- `app/src/main/java/com/arima/pro/core/audio/AudioOutputManager.kt`
- `app/src/main/java/com/example/domain/service/AudioEngine.kt`
- `app/src/main/java/com/example/ui/viewmodel/AudioViewModel.kt`
- `app/src/main/java/com/example/ui/screens/DacMonitorScreen.kt`

**Root cause**: `AudioOutputManager.routeToDac()` menjalankan `setPreferredAudioDevice()` dengan retry 3x. Kalau semua gagal → cuma `Log.e()`. Player tetap jalan, audio keluar dari speaker, user nggak tahu.

**Fix requirements**:

### 1. AudioOutputManager — Return Result<Boolean>

```kotlin
// Di AudioOutputManager.kt, ubah signature:
fun routeToDac(player: ExoPlayer?, context: Context): Boolean {
    // return true kalau berhasil routing, false kalau gagal
    // Ini sudah ada logic routing di dalam, tinggal return boolean
    var success = false
    val preferredDevice = getConnectedDacDevice(context)
    if (preferredDevice != null) {
        player?.let {
            val audioDeviceInfoArray = it.audioSessionId
            // ... existing routing logic ...
            // Kalau semua retry gagal:
            if (!success) {
                Log.e(TAG, "DAC routing failed after retries — device busy or enumeration issue")
            }
        }
    } else {
        Log.w(TAG, "No DAC device connected")
    }
    return success
}
```

### 2. AudioEngine — Propagate Result + DacRoutingStatus StateFlow

```kotlin
// Di AudioEngine.kt, tambah routing status:

private val _dacRoutingStatus = MutableStateFlow<DacRoutingStatus>(DacRoutingStatus.Idle)
val dacRoutingStatus: StateFlow<DacRoutingStatus> = _dacRoutingStatus.asStateFlow()

sealed class DacRoutingStatus {
    object Idle : DacRoutingStatus()
    object Routing : DacRoutingStatus()
    data class Success(val deviceName: String) : DacRoutingStatus()
    data class Failed(val reason: String) : DacRoutingStatus()
}

fun routeOutputToDac() {
    _dacRoutingStatus.value = DacRoutingStatus.Routing
    val success = try {
        outputManager.routeToDac(player, context)
    } catch (e: Exception) {
        Log.e(TAG, "routeOutputToDac exception: ${e.message}")
        false
    }
    if (success) {
        _dacRoutingStatus.value = DacRoutingStatus.Success(
            outputManager.getConnectedDacDevice(context)?.productName?.toString() ?: "USB DAC"
        )
    } else {
        _dacRoutingStatus.value = DacRoutingStatus.Failed("Device busy or enumeration failed — audio routed to speaker")
    }
}
```

### 3. DacMonitorScreen — Display Routing Status Real-Time

Tambah row di CURRENT TELEMETRY OUTPUT:

```kotlin
// Di telemetry output table, tambah:
"Route Status" to when (val status = viewModel.audioEngine.dacRoutingStatus.value) {
    is com.arima.pro.core.audio.DacRoutingStatus.Idle -> "—"
    is com.arima.pro.core.audio.DacRoutingStatus.Routing -> "ROUTING..."
    is com.arima.pro.core.audio.DacRoutingStatus.Success -> "ACTIVE → ${status.deviceName}"
    is com.arima.pro.core.audio.DacRoutingStatus.Failed -> "FAILED\n${status.reason}"
}
```

Dan retry button kalau Failed:

```kotlin
// Di routing status Failed state, tambah button:
if (status is com.arima.pro.core.audio.DacRoutingStatus.Failed) {
    OutlinedButton(
        onClick = { viewModel.audioEngine.routeOutputToDac() },
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text("Retry Routing")
    }
}
```

### 4. AudioViewModel — Auto-show Snackbar on Routing Failure

```kotlin
// Di AudioViewModel, collect routing status:
LaunchedEffect(audioEngine.dacRoutingStatus) {
    when (val status = audioEngine.dacRoutingStatus.value) {
        is com.arima.pro.core.audio.DacRoutingStatus.Failed -> {
            snackbarHostState.showSnackbar(
                message = "DAC routing failed: ${status.reason}. Audio playing via speaker.",
                duration = SnackbarDuration.Long
            )
        }
        is com.arima.pro.core.audio.DacRoutingStatus.Success -> {
            snackbarHostState.showSnackbar(
                message = "DAC routing active: ${status.deviceName}",
                duration = SnackbarDuration.Short
            )
        }
        else -> {}
    }
}
```

**Constraint**:
- Kalau routing gagal, MAKAN audio harus tetap play (Jangan pause/destroy player)
- Snackbar muncul otomatis tanpa user harus buka DAC Monitor
- Retry button tersedia di screen DAC Monitor

---

## BUGFIX #6 (TINGGI) — VU Meter Pakai Fake Random Data (Bukan Real Audio)

**Files affected**:
- `app/src/main/java/com/example/domain/service/AudioEngine.kt:411–466`
- `app/src/main/java/com/arima/pro/core/audio/VuMeterAnalyzer.kt` (exists but NOT used)
- `app/src/main/java/com/arima/pro/core/audio/PlayerHolder.kt`

**Root cause**: VU meter di `AudioEngine.kt:411-466` pakai `Random.nextFloat()` + `sin()` buat simulasi animasi sinus wave, bukan real PCM amplitude. `VuMeterAnalyzer.kt` yang bagus nggak pernah dipanggil.

```kotlin
// Current BROKEN code — fake data:
val baseL = Random.nextFloat() * 20f - 18f
val baseR = Random.nextFloat() * 20f - 18f
val timeFactor = (System.currentTimeMillis() % 1000) / 1000f
val dynamicSwing = kotlin.math.sin(timeFactor * Math.PI * 4).toFloat() * 10f
```

**Fix requirements**:

### Option A (RECOMMENDED — Simpler, pakai Android Visualizer API)

```kotlin
// Di AudioEngine.kt, ganti startVuLoop() logic:

private var visualizer: android.media.audiofx.Visualizer? = null

private fun initVisualizer() {
    try {
        val sessionId = player?.audioSessionId ?: return
        
        val visualizerObj = android.media.audiofx.Visualizer(sessionId)
        visualizerObj.setDataCaptureListener(
            object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                    visualizer: android.media.audiofx.Visualizer?,
                    waveform: ByteArray?,
                    samplingRate: Int
                ) {
                    waveform?.let { data ->
                        val rmsL = calculateRmsL(data)
                        val rmsR = calculateRmsR(data)
                        _vuLevels.value = Pair(rmsL, rmsR)
                    }
                }
                override fun onFftDataCapture(
                    visualizer: android.media.audiofx.Visualizer?,
                    fft: ByteArray?,
                    samplingRate: Int
                ) {}
            },
            android.media.audiofx.Visualizer.getMaxCaptureRate(),
            false,  // waveform
            true    // FFT
        )
        visualizerObj.enabled = true
        visualizer = visualizerObj
    } catch (e: SecurityException) {
        Log.w(TAG, "Visualizer permission denied — fallback to AudioLevelExtractor")
        // Fallback: AudioLevelExtractor already wired in PlayerHolder, no action needed
    }
}

private fun calculateRmsL(waveform: ByteArray): Float {
    // Extract left channel from interleaved PCM
    var sum = 0.0
    for (i in 0 until waveform.size step 4) { // step 4 = 2 samples (L+R) * 2 bytes
        val sample = ((waveform[i].toInt() and 0xFF) or (waveform[i+1].toInt() shl 8)).toShort()
        sum += sample.toDouble() * sample.toDouble()
    }
    val rms = kotlin.math.sqrt(sum / (waveform.size / 4))
    return (20 * kotlin.math.log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f)
}

private fun calculateRmsR(waveform: ByteArray): Float {
    // Extract right channel — offset by 2 bytes
    var sum = 0.0
    for (i in 2 until waveform.size step 4) {
        val sample = ((waveform[i].toInt() and 0xFF) or (waveform[i+1].toInt() shl 8)).toShort()
        sum += sample.toDouble() * sample.toDouble()
    }
    val rms = kotlin.math.sqrt(sum / (waveform.size / 4))
    return (20 * kotlin.math.log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f)
}

override fun onRelease() {
    visualizer?.enabled = false
    visualizer?.release()
    visualizer = null
}
```

### Option B (AudioProcessor injection via VuMeterAnalyzer)

```kotlin
// Di PlayerHolder.kt, saat build DefaultAudioSink:

val audioLevelExtractor = AudioLevelExtractor { leftDb, rightDb, peakL, peakR ->
    // Callback dari AudioProcessor — feed ke AudioEngine via shared reference
    AudioEngine.instance?.updateVuLevels(leftDb, rightDb, peakL, peakR)
}

// Di AudioProcessor chain:
val audioProcessors = listOf(
    ResamplingAudioProcessor(),
    DitheringAudioProcessor(),
    NormalizationAudioProcessor(),
    audioLevelExtractor  // ← extract PCM levels, pass through
)
```

**Constraint**:
- JANGAN hapus VuMeterAnalyzer.kt yang sudah ada
- VU meter update rate: ~30fps, jangan block audio thread
- Handle Visualizer permission (RECORD_AUDIO) — sudah di-handle di BugFix #4

---

## BUGFIX #7 (TINGGI) — DsdDataSource: .dff Not Supported + No Graceful Fallback

**Files affected**:
- `app/src/main/java/com/arima/pro/core/audio/DsdDataSource.kt`
- `app/src/main/java/com/arima/pro/core/audio/DsfParser.kt`
- `app/src/main/java/com/example/core/player/PlayerHolder.kt`

**Root cause 1**: Di `DsdDataSource.kt:43`, cek `uriStr.endsWith(".dff")` tapi parser-nya `DsfParser` yang cuma parse format `.dsf`. Kalau user main file `.dff`, parsing gagal → raw stream → noise.

**Root cause 2**: Di `PlayerHolder.kt`, `customDataSourceFactory` selalu return `DsdDataSource` — nggak ada graceful fallback kalau URI bukan DSD.

**Fix requirements**:

### 1. DffParser.kt — Buat parser baru untuk .dff format

Create file: `app/src/main/java/com/arima/pro/core/audio/DffParser.kt`

```kotlin
// DFF (DSD Interchange File Format) parser
// DFF header: "FRM8" (not "DSD " like DSF)
// Chunk structure: DSD chunk → fmt chunk → data chunk (sequential)

class DffParser {
    data class DffMetadata(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int = 1, // DSD = 1-bit
        val formatType: DffFormatType
    )

    enum class DffFormatType { DSD64, DSD128, DSD256, DSD512 }

    fun parse(inputStream: InputStream): DffMetadata {
        // Read FRM8 marker
        val marker = ByteArray(4)
        inputStream.read(marker)
        if (!marker.contentEquals("FRM8".toByteArray())) {
            throw IllegalArgumentException("Not a valid DFF file: wrong marker")
        }

        // Read chunk size (64-bit uint, big-endian)
        val chunkSize = readUint64(inputStream)

        // Read format version
        val formatVersion = readUint32(inputStream)

        // Read format ID (should be "fmt ")
        val fmtId = ByteArray(4)
        inputStream.read(fmtId)

        // Read fmt chunk size
        val fmtChunkSize = readUint64(inputStream)

        // Parse fmt chunk data
        val fs = readUint32(inputStream) // Sample rate
        val channels = readUint32(inputStream)
        val bitDepth = readUint8(inputStream)

        // Determine DSD format (DSD64=2822400Hz, DSD128=5644800Hz, etc.)
        val formatType = when (fs) {
            2822400 -> DffFormatType.DSD64
            5644800 -> DffFormatType.DSD128
            11289600 -> DffFormatType.DSD256
            22579200 -> DffFormatType.DSD512
            else -> DffFormatType.DSD64
        }

        return DffMetadata(sampleRate = fs, channels = channels, bitsPerSample = bitDepth, formatType = formatType)
    }

    private fun readUint64(inputStream: InputStream): Long { ... }
    private fun readUint32(inputStream: InputStream): Int { ... }
    private fun readUint8(inputStream: InputStream): Int { ... }
}
```

### 2. DsdDataSource — Detect Format dari Magic Bytes

```kotlin
// Di DsdDataSource.open() method:

private fun detectFormat(inputStream: InputStream): AudioFormatType {
    val magic = ByteArray(4)
    val pos = inputStream.mark(4)
    val bytesRead = inputStream.read(magic)
    inputStream.reset()

    return when {
        bytesRead < 4 -> AudioFormatType.UNKNOWN
        magic.contentEquals("DSD ".toByteArray()) -> AudioFormatType.DSF
        magic.contentEquals("FRM8".toByteArray()) -> AudioFormatType.DFF
        magic.contentEquals("RIFF".toByteArray()) -> AudioFormatType.WAV
        else -> AudioFormatType.UNKNOWN
    }
}
```

### 3. DsdDataSource — Graceful Fallback untuk Non-DSD

```kotlin
// Di DsdDataSource.open(), replace raw stream fallback:

val format = detectFormat(openConnection().getInputStream())
when (format) {
    AudioFormatType.DSF -> return DsfParser().parse(uri)
    AudioFormatType.DFF -> return DffParser().parse(openConnection().getInputStream())
    AudioFormatType.WAV -> {
        // Delegate to system MediaPlayer or return error
        return AudioSourceResult.Error("WAV not supported — use FLAC/ALAC")
    }
    AudioFormatType.UNKNOWN -> {
        // Don't return raw stream — will produce noise
        throw IllegalArgumentException("Unsupported format: not a recognized DSD file")
    }
}
```

### 4. PlayerHolder — Smart DataSource Factory

```kotlin
// Di PlayerHolder.kt, improve customDataSourceFactory:

fun createDataSourceFactory(): DataSource.Factory {
    val defaultDataSourceFactory = DefaultDataSource.Factory(context)
    return DataSource.Factory { uri ->
        when {
            uri.path?.endsWith(".dsf") == true -> DsdDataSource(context, uri)
            uri.path?.endsWith(".dff") == true -> DsdDataSource(context, uri)
            uri.scheme == "content" -> DefaultContentDataSource(context)
            uri.scheme == "http" || uri.scheme == "https" -> DefaultHttpDataSource.Factory()
                .setUserAgent("arima-pro")
                .createDataSource()
            else -> defaultDataSourceFactory.createDataSource()
        }
    }
}
```

**Constraint**:
- JANGAN return raw stream tanpa processing — itu menghasilkan noise/crackling
- Non-DSD files harus throw exception atau graceful error, bukan silent corrupt audio
- DFF parser harus handle DSD64/DSD128/DSD256/DSD512

---

## BUGFIX #8 (SEDANG) — ResamplingAudioProcessor Mono & Surround Channels Not Handled

**Files affected**:
- `app/src/main/java/com/arima/pro/core/audio/ResamplingAudioProcessor.kt`

**Root cause**: `onConfigure` return `C.ENCODING_PCM_16BIT` tanpa cek channel count. Di `queueInput`, asumsi selalu stereo (channelCount=2). Kalau lagu mono atau 5.1 surround, audio processing corrupt atau silent.

**Fix requirements**:

### 1. onConfigure — Handle all channel configs

```kotlin
// Di ResamplingAudioProcessor.onConfigure():

override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    if (!inputAudioFormat.encoding.matches(ENCODING_PCM_TYPES) || inputAudioFormat.sampleRate == Format.NO_VALUE) {
        return AudioFormat.NOT_SET
    }

    this.inputFormat = inputAudioFormat

    // Determine output format
    val targetSampleRate = if (targetSampleRate > 0) targetSampleRate else inputAudioFormat.sampleRate

    // Passthrough if no resampling needed
    if (targetSampleRate == inputAudioFormat.sampleRate) {
        return inputAudioFormat // keep same channelCount + sampleRate
    }

    return AudioFormat(
        inputAudioFormat.sampleRate,     // output sample rate
        inputAudioFormat.channelCount,    // ← keep original channel count
        inputAudioFormat.encoding
    )
}
```

### 2. queueInput — Fix per-channel iteration

```kotlin
// Di ResamplingAudioProcessor.queueInput():

val bytesPerFrame = inputFormat.channelCount * 2  // ← dynamic, not hardcoded 2
val inputFramesCount = size / bytesPerFrame

val outputFrameCount = ((inputFramesCount * outputSampleRate) / inputSampleRate).toInt()
val outputBuffer = ShortArray(outputFrameCount * inputFormat.channelCount)

var srcIndex = 0.0
val step = inputSampleRate.toFloat() / outputSampleRate.toFloat()

for (outFrame in 0 until outputFrameCount) {
    val srcIndexFloor = srcIndex.toInt()
    val fraction = (srcIndex - srcIndexFloor).toFloat()

    // Iterate per channel (supports mono=1, stereo=2, surround=6, etc.)
    for (ch in 0 until inputFormat.channelCount) {
        val idx1 = (srcIndexFloor * inputFormat.channelCount + ch).coerceIn(0, inputShorts.size - 1)
        val idx2 = ((srcIndexFloor + 1) * inputFormat.channelCount + ch).coerceIn(0, inputShorts.size - 1)

        val s1 = inputShorts[idx1].toFloat()
        val s2 = inputShorts[idx2].toFloat()
        val interpolatedSample = (s1 * (1.0f - fraction) + s2 * fraction).toInt().coerceIn(-32768, 32767).toShort()

        outputBuffer[outFrame * inputFormat.channelCount + ch] = interpolatedSample
    }

    srcIndex += step
}
```

### 3. Flush — Handle format changes

```kotlin
override fun flush() {
    srcIndex = 0.0
    inputShorts.clear()
    outputBuffer = ShortArray(0)
    isActive = targetSampleRate != inputFormat.sampleRate
}
```

**Constraint**:
- Mono (channelCount=1) dan surround (channelCount > 2) harus bekerja dengan benar
- Jangan hardcode `channelCount = 2` di mana pun
- Resampling ratio bisa fractional — polyphase interpolation handles this

---

## BUGFIX #9 (SEDANG) — Duplicate AudioEngine Classes (Domain vs Core)

**Files affected**:
- `app/src/main/java/com/example/domain/service/AudioEngine.kt`
- `app/src/main/java/com/arima/pro/core/audio/AudioEngine.kt`
- `app/src/main/java/com/example/ui/viewmodel/AudioViewModel.kt`

**Root cause**: Ada 2 class bernama `AudioEngine` di 2 package berbeda. `AudioViewModel` pakai `domain.service.AudioEngine`. `core.audio.AudioEngine` nggak pernah dipanggil (dead code atau misnamed).

**Fix requirements**:

### 1. Core AudioEngine — Rename jadi PlayerCore atau delete

```kotlin
// Di core/audio/AudioEngine.kt:
// PILIH SALAH SATU:

// Option A: Rename (jika akan dipakai):
class PlayerCore( // ← rename dari AudioEngine
    private val context: Context,
    private val player: ExoPlayer
) {
    // existing methods: play(), pause(), stop(), seek()
    // Document: "Core player wrapper — UI layer uses domain/AudioEngine"
}

// Option B: Delete (jika benar-benar dead code)
// Periksa PlayerHolder, DacController, dll — kalau nggak ada yang import
// core.audio.AudioEngine, hapus seluruh file
```

### 2. Clear Dependency Graph

```kotlin
// Di AudioViewModel:
private val audioEngine = com.example.domain.service.AudioEngine(context)

// Hapus atau jelaskan hubungan dengan core.audio.AudioEngine di comment:
// core.audio.AudioEngine is DEPRECATED — use domain.service.AudioEngine
```

**Constraint**:
- Semua consumer harus jelas menggunakan yang mana
- Jangan ada ambiguity — satu AudioEngine yang active, satu di-delete atau jelas deprecated

---

## BUGFIX #10 (SEDANG) — DacController One-Shot Detection (No Hotplug Monitoring)

**Files affected**:
- `app/src/main/java/com/arima/pro/core/audio/DacController.kt`
- `app/src/main/java/com/example/ui/viewmodel/AudioViewModel.kt`

**Root cause**: `detectDac()` dijalankan sekali di init, hasilnya fixed. Kalau user colok DAC setelah app jalan, nggak terdeteksi sampai restart.

**Fix requirements**:

### 1. AudioViewModel — USB BroadcastReceiver untuk Hotplug (Already in BUGFIX #1)

Sudah di-cover di BugFix #1 — BroadcastReceiver di AudioViewModel emit DacHotplugEvent ke UI layer.

### 2. DacController — tambahkan refreshDetection() public method

```kotlin
// Di DacController.kt:

fun refreshDetection(): DacState {
    updateDacDetection()
    return _dacState.value
}

// Called from AudioViewModel on hotplug and manual refresh
```

### 3. DacState.Sealed — tambahkan Scanning intermediate state

(Sudah di-cover di BUGFIX #3)

**Constraint**:
- Detection harus bisa dipanggil multiple times (hotplug + manual refresh)
- StateFlow harus emit setiap kali detection berubah

---

## Summary — All Fix Prompts

| # | Bug | Priority | Effort | Files |
|---|---|---|---|---|
| 1 | DAC hotplug → no user notification/dialog | 🔴 KRITICAL | Medium | AudioViewModel, MainActivity, DacHotplugDialog.kt (new) |
| 2 | Refresh button fake — nggak call detection | 🔴 KRITICAL | Small | DacMonitorScreen, AudioViewModel |
| 3 | Single-shot 500ms delay → detection miss | 🟡 TINGGI | Medium | DacController, AudioViewModel, DacMonitorScreen |
| 4 | RECORD_AUDIO permission missing → Visualizer fail | 🟡 TINGGI | Small | AndroidManifest, PermissionsScreen, AudioEngine |
| 5 | routeToDac() fail silent → audio ke speaker | 🟡 TINGGI | Medium | AudioOutputManager, AudioEngine, AudioViewModel, DacMonitorScreen |
| 6 | VU meter fake — pakai Random bukan real audio | 🟡 TINGGI | Medium | AudioEngine, PlayerHolder, VuMeterAnalyzer |
| 7 | DsdDataSource .dff not supported, no fallback | 🟡 TINGGI | Medium | DsdDataSource, DffParser.kt (new), PlayerHolder |
| 8 | ResamplingAudioProcessor mono/surround not handled | ⚪ SEDANG | Medium | ResamplingAudioProcessor |
| 9 | Double AudioEngine (domain vs core duplicate) | ⚪ SEDANG | Small | 3 files |
| 10 | DacController one-shot detection (no hotplug) | ⚪ SEDANG | Medium | DacController, AudioViewModel |

**Recommended fix order**: Bug #2 → #4 → #6 → #1 → #3 → #5 → #7 → #8 → #9 → #10

---

*Generated by Asuna (Hermes Agent) — 2026-05-29*