package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Song
import com.example.ui.theme.*
import com.example.ui.viewmodel.AudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: AudioViewModel) {
    val currentSong by viewModel.audioEngine.currentSong.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val position by viewModel.audioEngine.currentPosition.collectAsState()
    val queue by viewModel.audioEngine.playbackQueue.collectAsState()

    val vuLevels by viewModel.audioEngine.vuLevels.collectAsState()
    val peakLevels by viewModel.audioEngine.peakLevels.collectAsState()

    // Interactive Spinning animation factor when playing
    val infiniteTransition = rememberInfiniteTransition(label = "ReelRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .statusBarsPadding()
            .padding(bottom = 80.dp) // padding for bottom menu
    ) {
        // 1. Sleek Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.selectTab("library") }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "NOW PLAYING",
                style = TechnicalLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                color = TextSecondary
            )
            IconButton(onClick = { viewModel.showEqualizerPanel.value = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Equalizer",
                    tint = AmberGold
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // 2. Large Visualizer Panel (Spinning cassette/amp)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .aspectRatio(1.1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundSurface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentSong?.album?.contains("Acoustic") == true || currentSong?.album?.contains("Quantum") == true) {
                        RotatingReelsTape(isPlaying = isPlaying, rotationAngle = rotationAngle)
                    } else {
                        DynamicVinylDisc(isPlaying = isPlaying, rotationAngle = rotationAngle)
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // 3. Track Headings
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentSong?.title ?: "No Audio Playing",
                        style = DisplayMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${currentSong?.artist ?: "Unknown Artist"} • ${currentSong?.album ?: "Unknown Album"}",
                        style = BodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4. CUSTOM DIRECT DUAL VU METER
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BackgroundSurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    // Left Channel
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("L", style = TechnicalSmall.copy(fontWeight = FontWeight.Black))
                        Spacer(modifier = Modifier.width(12.dp))
                        // Progress segmented LED bar
                        Box(modifier = Modifier.weight(1f).height(12.dp)) {
                            SegmentedVUMeterBar(
                                dbValue = vuLevels.first,
                                peakValue = peakLevels.first
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = String.format("%.1f dB", vuLevels.first),
                            style = TechnicalSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = getVuColor(vuLevels.first)
                            ),
                            modifier = Modifier.width(54.dp),
                            textAlign = TextAlign.End
                        )
                    }

                    // Right Channel
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("R", style = TechnicalSmall.copy(fontWeight = FontWeight.Black))
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f).height(12.dp)) {
                            SegmentedVUMeterBar(
                                dbValue = vuLevels.second,
                                peakValue = peakLevels.second
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = String.format("%.1f dB", vuLevels.second),
                            style = TechnicalSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = getVuColor(vuLevels.second)
                            ),
                            modifier = Modifier.width(54.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 5. TECHNICAL FILE INFO GRID
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "— FILE INFORMATION",
                        style = LabelCaps.copy(fontWeight = FontWeight.Bold, color = AmberGold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TechIndicator(label = "FORMAT", value = currentSong?.format ?: "FLAC", highlightValue = true)
                        TechIndicator(label = "SAMPLE", value = currentSong?.sampleRate ?: "96kHz")
                        TechIndicator(label = "DEPTH", value = currentSong?.bitDepth ?: "24-bit")
                        TechIndicator(label = "SIZE", value = currentSong?.fileSize ?: "185MB")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 6. TECHNICAL HARDWARE CONFIGURATION GRID
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "— HARDWARE OUTPUT",
                        style = LabelCaps.copy(fontWeight = FontWeight.Bold, color = AmberGold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TechIndicator(label = "MODEL", value = "Dawn Pro")
                        TechIndicator(label = "CHIPSET", value = "CS43131 *2")
                        TechIndicator(label = "I/O", value = "USB-C Direct")
                        val isExclusive by viewModel.dacExclusiveMode.collectAsState()
                        TechIndicator(label = "GAIN", value = if (isExclusive) "High Gain" else "Low Gain")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 7. TIME SEEKER TIMELINE
            item {
                val totalDuration = currentSong?.duration ?: 1000L
                val progressRatio = position.toFloat() / totalDuration.toFloat()
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = getFormattedTime(position),
                            style = TechnicalSmall
                        )
                        Text(
                            text = getFormattedTime(totalDuration),
                            style = TechnicalSmall
                        )
                    }
                    
                    Slider(
                        value = progressRatio,
                        onValueChange = { ratio ->
                            viewModel.audioEngine.seekTo((ratio * totalDuration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = AmberGold,
                            activeTrackColor = AmberGold,
                            inactiveTrackColor = BorderDefault
                        ),
                        modifier = Modifier.testTag("playback_seekbar")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 8. MASTER PLAYBACK CONTROLS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Repeat mode / Shuffling
                    var isShuffle by remember { mutableStateOf(false) }
                    IconButton(onClick = { isShuffle = !isShuffle }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) AmberGold else TextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Skip Previous
                        IconButton(
                            onClick = { viewModel.audioEngine.skipToPrevious() },
                            modifier = Modifier.size(48.dp).testTag("prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Previous",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Play/Pause Master
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(BackgroundSurface)
                                .border(1.5.dp, AmberGold, CircleShape)
                                .clickable {
                                    if (isPlaying) {
                                        viewModel.audioEngine.pause()
                                    } else {
                                        viewModel.audioEngine.play()
                                    }
                                }
                                .testTag("play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = AmberGold,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Skip Next
                        IconButton(
                            onClick = { viewModel.audioEngine.skipToNext() },
                            modifier = Modifier.size(48.dp).testTag("next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Fav Toggle
                    val isFavorite = currentSong?.isFavorite == true
                    IconButton(
                        onClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
                        modifier = Modifier.testTag("fav_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color.Red else TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 9. UP NEXT QUEUE LIST
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UP NEXT",
                        style = LabelCaps.copy(fontWeight = FontWeight.Bold, color = AmberGold)
                    )
                    Text(
                        text = "FULL QUEUE >",
                        style = TechnicalSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary,
                        modifier = Modifier.clickable { /* open queue screen */ }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            val filteredQueue = queue.filter { it.id != currentSong?.id }
            if (filteredQueue.isEmpty()) {
                item {
                    Text(
                        text = "Queue is empty",
                        style = BodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(filteredQueue.take(3)) { idx, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playSong(song) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Reorder",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BackgroundCard),
                            contentAlignment = Alignment.Center
                        ) {
                            VinylArtVector(modifier = Modifier.fillMaxSize())
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = BodyLarge.copy(fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = BodyMedium.copy(fontSize = 12.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FormatBadge(format = song.format)
                    }
                    Divider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        }
    }
}

// --- RENDERING SUB ELEMENTS ---

@Composable
fun SegmentedVUMeterBar(dbValue: Float, peakValue: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val totalSegments = 24
        val spacing = 2.dp.toPx()
        val totalSpacing = spacing * (totalSegments - 1)
        val segW = (w - totalSpacing) / totalSegments

        // Normalize DB (-60 to 3 dB)
        val activeRatio = ((dbValue + 50f) / 53f).coerceIn(0f, 1f)
        val activeCount = (activeRatio * totalSegments).toInt()

        val peakRatio = ((peakValue + 50f) / 53f).coerceIn(0f, 1f)
        val peakIndex = (peakRatio * (totalSegments - 1)).toInt()

        for (i in 0 until totalSegments) {
            val left = i * (segW + spacing)
            
            // Assign color segment categories
            val color = when {
                i >= 20 -> VUPeak       // Peak (above -4 dB)
                i >= 15 -> VUWarning    // Mid-warning (from -12 to -4 db)
                else -> VUSafe          // Safe (below -12 db)
            }

            val isActive = i < activeCount
            val isPeakIndicator = i == peakIndex

            if (isActive) {
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(segW, h)
                )
            } else if (isPeakIndicator) {
                // Draw a full peak indicator segment with some alpha reduction
                drawRect(
                    color = color.copy(alpha = 0.9f),
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(segW, h)
                )
            } else {
                // Dim segment background representing inactive states
                drawRect(
                    color = color.copy(alpha = 0.08f),
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(segW, h)
                )
            }
        }
    }
}

fun getVuColor(dbValue: Float): Color {
    return when {
        dbValue >= -4.0f -> VUPeak
        dbValue >= -12.0f -> VUWarning
        else -> VUSafe
    }
}

fun getFormattedTime(timeMs: Long): String {
    val totalSecs = timeMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

@Composable
fun TechIndicator(label: String, value: String, highlightValue: Boolean = false) {
    Column(
        modifier = Modifier
            .background(BackgroundCard, RoundedCornerShape(4.dp))
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = LabelCaps.copy(fontSize = 9.sp, color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TechnicalSmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (highlightValue) AmberGold else TextPrimary
            )
        )
    }
}

@Composable
fun DynamicVinylDisc(isPlaying: Boolean, rotationAngle: Float) {
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val sizePx = size.width
        val center = sizePx / 2
        val radius = sizePx / 3.2f

        // Solid rich black outer rubber turntable background mat
        drawCircle(color = Color(0xFF0F0F0F), radius = center)
        drawCircle(color = BorderDefault, radius = center, style = Stroke(width = 2.dp.toPx()))

        // Rotate the inner record based on angle
        val activeAngle = if (isPlaying) rotationAngle else 0f
        
        rotate(degrees = activeAngle, pivot = androidx.compose.ui.geometry.Offset(center, center)) {
            // Draw the main vinyl body
            drawCircle(color = Color(0xFF1B1B1B), radius = radius * 2.2f)

            // Groove spiral lines
            drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * 2.0f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * 1.8f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * 1.5f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * 1.2f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * 0.9f, style = Stroke(width = 1.dp.toPx()))

            // Center visual album label (Golden highlight)
            drawCircle(color = AmberGold, radius = radius * 0.6f)
            
            // Inner detail ring decoration
            drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = radius * 0.45f)

            // Draw two crossing decorative gold brand alignment bars (acting like visual markers)
            drawLine(
                color = Color.Black,
                start = androidx.compose.ui.geometry.Offset(center - radius * 0.5f, center),
                end = androidx.compose.ui.geometry.Offset(center + radius * 0.5f, center),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Black,
                start = androidx.compose.ui.geometry.Offset(center, center - radius * 0.5f),
                end = androidx.compose.ui.geometry.Offset(center, center + radius * 0.5f),
                strokeWidth = 2.dp.toPx()
            )

            // Center metal spindle pin hole
            drawCircle(color = Color(0xFF121212), radius = radius * 0.15f)
            drawCircle(color = Color.Gray, radius = radius * 0.06f)
        }
    }
}

@Composable
fun RotatingReelsTape(isPlaying: Boolean, rotationAngle: Float) {
    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val sizePx = size.width
        val center = sizePx / 2
        val activeAngle = if (isPlaying) rotationAngle else 0f

        // Draw Cassette tape faceplate
        drawRect(color = Color(0xFF141414))
        drawRect(color = BorderDefault, style = Stroke(width = 1.5.dp.toPx()))

        // Draw internal clear plastic view window
        val windowW = sizePx * 0.65f
        val windowH = sizePx * 0.25f
        drawRoundRect(
            color = Color(0xFF0A0A0A),
            topLeft = androidx.compose.ui.geometry.Offset(center - windowW / 2, center - windowH / 2),
            size = androidx.compose.ui.geometry.Size(windowW, windowH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
        )
        drawRoundRect(
            color = BorderSubtle,
            topLeft = androidx.compose.ui.geometry.Offset(center - windowW / 2, center - windowH / 2),
            size = androidx.compose.ui.geometry.Size(windowW, windowH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // Two Spindle Hub wheels inside the window
        val hub1X = center - windowW * 0.25f
        val hub2X = center + windowW * 0.25f

        // Hub 1 (Left Spindle Reel)
        drawCircle(color = AmberMuted.copy(alpha = 0.4f), radius = sizePx * 0.15f, center = androidx.compose.ui.geometry.Offset(hub1X, center))
        rotate(degrees = activeAngle, pivot = androidx.compose.ui.geometry.Offset(hub1X, center)) {
            drawCircle(color = Color.Black, radius = sizePx * 0.08f, center = androidx.compose.ui.geometry.Offset(hub1X, center))
            // Gears slots
            for (i in 0 until 6) {
                val offsetAngle = i * 60.0
                val rad = Math.toRadians(offsetAngle)
                val lineLen = sizePx * 0.08f
                drawLine(
                    color = AmberGold,
                    start = androidx.compose.ui.geometry.Offset(hub1X, center),
                    end = androidx.compose.ui.geometry.Offset((hub1X + lineLen * Math.cos(rad)).toFloat(), (center + lineLen * Math.sin(rad)).toFloat()),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Hub 2 (Right Spindle Reel)
        drawCircle(color = AmberMuted.copy(alpha = 0.4f), radius = sizePx * 0.15f, center = androidx.compose.ui.geometry.Offset(hub2X, center))
        rotate(degrees = activeAngle, pivot = androidx.compose.ui.geometry.Offset(hub2X, center)) {
            drawCircle(color = Color.Black, radius = sizePx * 0.08f, center = androidx.compose.ui.geometry.Offset(hub2X, center))
            // Gears slots
            for (i in 0 until 6) {
                val offsetAngle = i * 60.0
                val rad = Math.toRadians(offsetAngle)
                val lineLen = sizePx * 0.08f
                drawLine(
                    color = AmberGold,
                    start = androidx.compose.ui.geometry.Offset(hub2X, center),
                    end = androidx.compose.ui.geometry.Offset((hub2X + lineLen * Math.cos(rad)).toFloat(), (center + lineLen * Math.sin(rad)).toFloat()),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}
