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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CustomPauseIcon(color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(4.dp).fillMaxHeight(0.6f).background(color, RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.width(4.dp).fillMaxHeight(0.6f).background(color, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun CompactTechIndicator(label: String, value: String) {
    Row(
        modifier = Modifier
            .glassSurface(4.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = LabelCaps.copy(fontSize = 7.5.sp, color = TextSecondary)
        )
        Text(
            text = value,
            style = TechnicalSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        )
    }
}

@Composable
fun ResponsiveTechInfo(
    song: Song?,
    isSmall: Boolean,
    isLarge: Boolean
) {
    val format = song?.format ?: "FLAC"
    val sample = song?.sampleRate ?: "96kHz"
    val depth = song?.bitDepth ?: "24-bit"
    val size = song?.fileSize ?: "185MB"
    val durationStr = song?.let { getFormattedTime(it.duration) } ?: "00:00:00"

    if (isSmall) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompactTechIndicator(label = "FORMAT", value = format)
                CompactTechIndicator(label = "SAMPLE", value = sample)
                CompactTechIndicator(label = "SIZE", value = size)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CompactTechIndicator(label = "DEPTH", value = depth)
                CompactTechIndicator(label = "DURATION", value = durationStr)
            }
        }
    } else if (isLarge) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TechIndicator(label = "FORMAT", value = format, highlightValue = true)
            TechIndicator(label = "SAMPLE", value = sample)
            TechIndicator(label = "DEPTH", value = depth)
            TechIndicator(label = "SIZE", value = size)
            TechIndicator(label = "DURATION", value = durationStr)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TechIndicator(label = "FORMAT", value = format, highlightValue = true)
            TechIndicator(label = "SAMPLE", value = sample)
            TechIndicator(label = "DEPTH", value = depth)
            TechIndicator(label = "SIZE", value = size)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: AudioViewModel) {
    val currentSong by viewModel.audioEngine.currentSong.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()
    val position by viewModel.audioEngine.currentPosition.collectAsState()
    val queue by viewModel.audioEngine.playbackQueue.collectAsState()

    val vuLevels by viewModel.audioEngine.vuLevels.collectAsState()
    val peakLevels by viewModel.audioEngine.peakLevels.collectAsState()

    val dacState by viewModel.dacState.collectAsState(initial = com.arima.pro.core.audio.DacState.NotDetected)
    val dacDeviceName = when (val state = dacState) {
        is com.arima.pro.core.audio.DacState.Detected -> state.dacInfo.name.uppercase()
        else -> "SPEAKER (INTERNAL)"
    }

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isSmallWidth = screenWidth < 400.dp
        val isLargeWidth = screenWidth > 500.dp

        val albumArtSize = if (isSmallWidth) 180.dp else if (isLargeWidth) 260.dp else 220.dp
        val songTitleSize = if (isSmallWidth) 16.sp else if (isLargeWidth) 20.sp else 18.sp
        val vuMeterHeight = if (isSmallWidth) 50.dp else if (isLargeWidth) 64.dp else 60.dp
        val controlTarget = if (isSmallWidth) 48.dp else if (isLargeWidth) 64.dp else 56.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight) // Takes exactly one screen height for the main player
                ) {
                    // 1. Sleek Navigation Header (fixed height 48dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
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

                    // Spacer for some breathing room
                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. ALBUM ART (flexible height, scaled to fill available space, square, centered)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val albumArt = currentSong?.albumArt
                        val bitmapState = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = albumArt) {
                            if (albumArt != null) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        android.graphics.BitmapFactory.decodeByteArray(albumArt, 0, albumArt.size)?.let { bmp ->
                                            value = bmp.asImageBitmap()
                                        }
                                    } catch (e: Exception) {
                                        value = null
                                    }
                                }
                            } else {
                                value = null
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxHeight(0.9f)
                                .customShadow(
                                    color = if (isPlaying) AmberGold.copy(alpha = 0.2f) else Color.Transparent,
                                    radius = 20.dp,
                                    blurRadius = 30.dp,
                                    offsetY = 10.dp
                                )
                                .glassSurface(radius = 20.dp)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = bitmapState.value
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = "Album Art",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                if (currentSong?.album?.contains("Acoustic") == true || currentSong?.album?.contains("Quantum") == true) {
                                    RotatingReelsTape(isPlaying = isPlaying, rotationAngle = rotationAngle)
                                } else {
                                    DynamicVinylDisc(isPlaying = isPlaying, rotationAngle = rotationAngle)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. SONG INFO (compact, title Playfair/Serif style)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentSong?.title ?: "No Audio Playing",
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currentSong?.artist ?: "Unknown Artist"} • ${currentSong?.album ?: "Unknown Album"}",
                            style = BodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. VU METER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(vuMeterHeight)
                            .padding(horizontal = 24.dp)
                            .glassSurface(12.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        VuMeterComponent(
                            leftDb = vuLevels.first,
                            rightDb = vuLevels.second,
                            peakL = peakLevels.first,
                            peakR = peakLevels.second,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. TECHNICAL INFO GRID
                    ResponsiveTechInfo(
                        song = currentSong,
                        isSmall = isSmallWidth,
                        isLarge = isLargeWidth
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 6. SEEK BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val totalDuration = (currentSong?.duration ?: 1000L).coerceAtLeast(1L)

                        Text(
                            text = getFormattedTime(position),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        var dragPosition by remember { mutableStateOf<Float?>(null) }
                        val currentFraction = if (totalDuration > 0f) {
                            (position.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val sliderValue = dragPosition ?: currentFraction

                        Slider(
                            value = if (sliderValue.isNaN() || sliderValue.isInfinite()) 0f else sliderValue.coerceIn(0f, 1f),
                            onValueChange = {
                                dragPosition = if (it.isNaN() || it.isInfinite()) 0f else it.coerceIn(0f, 1f)
                            },
                            onValueChangeFinished = {
                                val finalFraction = dragPosition ?: 0f
                                viewModel.audioEngine.seekTo((finalFraction * totalDuration).toLong())
                                dragPosition = null
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = AmberGold,
                                activeTrackColor = AmberGold,
                                inactiveTrackColor = Color(0x0FFFFFFF) // rgba(255,255,255,0.06)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("playback_seekbar")
                        )

                        Text(
                            text = getFormattedTime(totalDuration),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    // 7. STATUS BAR & CONTROLS
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "DAC Icon",
                                tint = Color(0x88FFFFFF),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = dacDeviceName,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        val formatCode = currentSong?.format ?: "FLAC"
                        val depth = currentSong?.bitDepth ?: "24-bit"
                        val sample = currentSong?.sampleRate ?: "96kHz"
                        Text(
                            text = "$formatCode $depth/$sample",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Repeat Mode
                        var repeatState by remember { mutableStateOf(0) }
                        IconButton(
                            onClick = { repeatState = (repeatState + 1) % 3 },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Repeat",
                                    tint = when (repeatState) {
                                        1 -> TextPrimary
                                        2 -> TextPrimary
                                        else -> TextSecondary
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                if (repeatState == 2) {
                                    Text(
                                        text = "1",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BackgroundPrimary,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .background(TextPrimary, CircleShape)
                                            .padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }

                        // Prev
                        IconButton(
                            onClick = { viewModel.audioEngine.skipToPrevious() },
                            modifier = Modifier.size(44.dp).testTag("prev_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Previous",
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Play/Pause circular button (Glass style)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .glassSurface(radius = 32.dp, borderColor = AmberGold)
                                .clickable {
                                    if (isPlaying) viewModel.audioEngine.pause() else viewModel.audioEngine.play()
                                }
                                .testTag("play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaying) {
                                CustomPauseIcon(color = AmberGold, modifier = Modifier.size(24.dp).padding(4.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = AmberGold,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Next
                        IconButton(
                            onClick = { viewModel.audioEngine.skipToNext() },
                            modifier = Modifier.size(44.dp).testTag("next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next",
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Like (Favorite toggle)
                        val isFavorite = currentSong?.isFavorite == true
                        IconButton(
                            onClick = { currentSong?.let { viewModel.toggleFavorite(it) } },
                            modifier = Modifier.size(40.dp).testTag("fav_button")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) AmberGold else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // 8. QUEUE AREA
            val upcoming = queue.filter { it.id != currentSong?.id }
            
            item {
                Text(
                    text = "UP NEXT",
                    style = TechnicalLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
            
            // Now Playing item highlighted in queue
            currentSong?.let { song ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .glassSurface(8.dp)
                            .drawBehind {
                                drawRect(
                                    color = AmberGold,
                                    topLeft = Offset(0f, 0f),
                                    size = Size(4.dp.toPx(), size.height)
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "NOW PLAYING",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BackgroundPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                val itemArt = song.albumArt
                                val itemBitmapState = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = itemArt) {
                                    if (itemArt != null) {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                android.graphics.BitmapFactory.decodeByteArray(itemArt, 0, itemArt.size)?.let { bmp ->
                                                    value = bmp.asImageBitmap()
                                                }
                                            } catch (e: Exception) {
                                                value = null
                                            }
                                        }
                                    } else {
                                        value = null
                                    }
                                }
                                val itemBitmap = itemBitmapState.value
                                if (itemBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = itemBitmap,
                                        contentDescription = "Album Art",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    VinylArtVector(modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(AmberGold)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = song.title,
                                        style = BodyLarge.copy(fontSize = 14.sp, color = TextPrimary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = song.artist,
                                    style = BodyMedium.copy(fontSize = 12.sp, color = TextSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (upcoming.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No upcoming tracks in queue",
                            style = BodyMedium.copy(fontSize = 12.sp, color = TextMuted)
                        )
                    }
                }
            } else {
                itemsIndexed(upcoming) { _, song ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassSurface(8.dp, borderColor = Color(0x0AFFFFFF)) // rgba 0.04 -> 0x0A
                                .clickable { viewModel.playSong(song) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val itemArt = song.albumArt
                            val itemBitmapState = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = itemArt) {
                                if (itemArt != null) {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            android.graphics.BitmapFactory.decodeByteArray(itemArt, 0, itemArt.size)?.let { bmp ->
                                                value = bmp.asImageBitmap()
                                            }
                                        } catch (e: Exception) {
                                            value = null
                                        }
                                    }
                                } else {
                                    value = null
                                }
                            }
                            val itemBitmap = itemBitmapState.value
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BackgroundPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                if (itemBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = itemBitmap,
                                        contentDescription = "Album Art",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    VinylArtVector(modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = BodyLarge.copy(fontSize = 13.sp, color = TextPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = BodyMedium.copy(fontSize = 11.sp, color = TextSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.audioEngine.removeFromQueue(song) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
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
            .glassSurface(6.dp)
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
