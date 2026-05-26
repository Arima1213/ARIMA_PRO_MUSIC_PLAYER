package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Folder
import com.example.domain.model.Song
import com.example.ui.theme.*
import com.example.ui.viewmodel.AlbumItem
import com.example.ui.viewmodel.ArtistItem
import com.example.ui.viewmodel.AudioViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: AudioViewModel,
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    val openDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val contentResolver = context.contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                // Log or ignore
            }
            viewModel.addNewFolder(it.toString())
        }
    }

    val currentTab by viewModel.currentLibraryTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val songs by viewModel.filteredSongs.collectAsState()
    val albums by viewModel.albumList.collectAsState()
    val artists by viewModel.artistList.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val activeSong by viewModel.audioEngine.currentSong.collectAsState()
    val isPlaying by viewModel.audioEngine.isPlaying.collectAsState()

    var showAddFolder by remember { mutableStateOf(false) }
    var folderPathInput by remember { mutableStateOf("") }
    var selectedSongForSheet by remember { mutableStateOf<Song?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Custom Top Bar (Logo + Settings Gear only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.testTag("menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = TextPrimary
                    )
                }

                Text(
                    text = "ARIMA PRO",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = AmberGold
                )

                IconButton(
                    onClick = { viewModel.selectTab("settings") },
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary
                    )
                }
            }

            // 2. Now Playing Tap Area (Active Song Header HUD, Height: 56dp)
            if (activeSong != null) {
                val position by viewModel.audioEngine.currentPosition.collectAsState()
                val totalDuration = activeSong!!.duration
                val progressFraction = if (totalDuration > 0) position.toFloat() / totalDuration.toFloat() else 0f

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { viewModel.selectTab("player") }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Spinning/pulse bar simulator container
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val barWidth = 2.5.dp.toPx()
                                    val spacing = 2.dp.toPx()
                                    val h1 = if (isPlaying) (8.dp.toPx() + kotlin.math.sin(System.currentTimeMillis() / 150.0).toFloat() * 4.dp.toPx() + 4.dp.toPx()) else 10.dp.toPx()
                                    val h2 = if (isPlaying) (12.dp.toPx() + kotlin.math.cos(System.currentTimeMillis() / 200.0).toFloat() * 6.dp.toPx() + 6.dp.toPx()) else 14.dp.toPx()
                                    val h3 = if (isPlaying) (6.dp.toPx() + kotlin.math.sin(System.currentTimeMillis() / 250.0).toFloat() * 3.dp.toPx() + 3.dp.toPx()) else 8.dp.toPx()

                                    drawRoundRect(
                                        color = AmberGold,
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - h1) / 2f),
                                        size = androidx.compose.ui.geometry.Size(barWidth, h1),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = AmberGold,
                                        topLeft = androidx.compose.ui.geometry.Offset(barWidth + spacing, (size.height - h2) / 2f),
                                        size = androidx.compose.ui.geometry.Size(barWidth, h2),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = AmberGold,
                                        topLeft = androidx.compose.ui.geometry.Offset((barWidth + spacing) * 2f, (size.height - h3) / 2f),
                                        size = androidx.compose.ui.geometry.Size(barWidth, h3),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                                    )
                                }
                            }

                            // Song Description Title & Artist Row
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeSong!!.title,
                                    style = BodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                    color = AmberGold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = " • ${activeSong!!.artist}",
                                    style = BodyMedium.copy(fontSize = 11.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Toggle play pause HUD button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isPlaying) {
                                            viewModel.audioEngine.pause()
                                        } else {
                                            viewModel.audioEngine.play()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    if (isPlaying) {
                                        CustomPauseIcon(color = AmberGold, modifier = Modifier.size(16.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Trigger play",
                                            tint = AmberGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                val currentText = formatTimeLocal(position)
                                val totalText = formatTimeLocal(totalDuration)
                                Text(
                                    text = "$currentText / $totalText",
                                    style = TechnicalSmall.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                            }
                        }

                        // Bottom horizontal seekline indicator progress bounds
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.DarkGray)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                    .background(AmberGold)
                            )
                        }
                    }
                }
                Divider(color = BorderSubtle, thickness = 0.5.dp)
            }

            // 3. Tab Select Chips (Asymmetric Selected-Expands [♪ Songs] [⊏] [👤] [📁])
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Songs", "Albums", "Artists", "Folders").forEach { tab ->
                    val isSelected = currentTab.equals(tab, ignoreCase = true)
                    val icon = when (tab) {
                        "Songs" -> Icons.Default.PlayArrow
                        "Albums" -> Icons.Default.Star
                        "Artists" -> Icons.Default.Person
                        "Folders" -> Icons.Default.Home
                        else -> Icons.Default.PlayArrow
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (isSelected) AmberGold else BackgroundCard)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AmberGold else BorderSubtle,
                                shape = RoundedCornerShape(30.dp)
                            )
                            .clickable { viewModel.selectLibraryTab(tab.lowercase()) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab,
                                tint = if (isSelected) Color.Black else TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tab,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Search Bar with brief generic "Search..." placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input"),
                    placeholder = {
                        Text(
                            text = "Search...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BackgroundCard,
                        unfocusedContainerColor = BackgroundCard,
                        focusedBorderColor = BorderActive,
                        unfocusedBorderColor = BorderDefault,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Section Header Counters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val subText = when (currentTab) {
                    "songs" -> "SONGS • ${songs.size} TRACKS"
                    "albums" -> "ALBUMS • ${albums.size} ALBUMS"
                    "artists" -> "ARTISTS • ${artists.size} CREATORS"
                    "folders" -> "DIR • ${folders.size} SOURCE PATHS"
                    else -> ""
                }
                Text(
                    text = subText,
                    style = TechnicalSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextSecondary
                )
                
                IconButton(
                    onClick = { 
                        if (currentTab == "folders") {
                            showAddFolder = true
                        } else {
                            viewModel.triggerScan("/storage/music")
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (currentTab == "folders") Icons.Default.Add else Icons.Default.Refresh,
                        contentDescription = "Action",
                        tint = AmberGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Scrollable Grid & list container contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    "songs" -> SongsListView(
                        songs = songs,
                        activeSong = activeSong,
                        onSongClick = { viewModel.playSong(it) },
                        onFavClick = { viewModel.toggleFavorite(it) },
                        onKebabClick = { selectedSongForSheet = it }
                    )
                    "albums" -> AlbumsGridView(
                        albums = albums,
                        onAlbumClick = { album ->
                            // Play all tracks in album
                            val albumSongs = songs.filter { it.album == album.title }
                            if (albumSongs.isNotEmpty()) {
                                viewModel.playSong(albumSongs.first())
                            }
                        }
                    )
                    "artists" -> ArtistsListView(
                        artists = artists,
                        onArtistClick = { artist ->
                            val artistSongs = songs.filter { it.artist == artist.name }
                            if (artistSongs.isNotEmpty()) {
                                viewModel.playSong(artistSongs.first())
                            }
                        }
                    )
                    "folders" -> FoldersListView(
                        folders = folders,
                        onAddFolderClick = { openDirectoryLauncher.launch(null) },
                        onScanClick = { viewModel.triggerScan(it.path) },
                        onDeleteClick = { viewModel.deleteFolder(it) }
                    )
                }
            }
        }

        // Add Folder Modal Dialog
        if (showAddFolder) {
            AlertDialog(
                onDismissRequest = { showAddFolder = false },
                containerColor = BackgroundSurface,
                title = {
                    Text(
                        text = "ADD MEDIA ENGINE FOLDER",
                        style = HeadlineSmall.copy(color = TextPrimary)
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Specify direct path for hi-res scanning:",
                            style = BodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = folderPathInput,
                            onValueChange = { folderPathInput = it },
                            placeholder = { Text("/storage/music/new_hi-res", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BackgroundCard,
                                unfocusedContainerColor = BackgroundCard,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                        onClick = {
                            if (folderPathInput.isNotBlank()) {
                                viewModel.addNewFolder(folderPathInput)
                                folderPathInput = ""
                                showAddFolder = false
                            }
                        }
                    ) {
                        Text("ADD DIR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddFolder = false }) {
                        Text("CANCEL", color = TextSecondary)
                    }
                }
            )
        }

        // 7. Standard Bottom Option Sheet
        if (selectedSongForSheet != null) {
            val song = selectedSongForSheet!!
            val isCurrentPlaying = activeSong?.id == song.id

            ModalBottomSheet(
                onDismissRequest = { selectedSongForSheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF141414),
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) },
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            ) {
                if (isCurrentPlaying) {
                    // Tapping kebab on current active play: display device DAC and codec specs details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AUDIO OUTPUT",
                                style = TechnicalSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                                color = AmberGold
                            )
                            IconButton(onClick = { selectedSongForSheet = null }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }

                        Divider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                        val specs = listOf(
                            "Format" to song.format,
                            "Sample Rate" to song.sampleRate,
                            "Bit Depth" to song.bitDepth,
                            "Channels" to "Stereo",
                            "Bitrate" to when (song.format.uppercase()) {
                                "WAV" -> "9,216 kbps"
                                "FLAC" -> "2,822 kbps"
                                "DSD" -> "5,644 kbps"
                                else -> "1,411 kbps"
                            },
                            "Duration" to song.durationText,
                            "File Size" to song.fileSize
                        )

                        specs.forEach { (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, style = BodyMedium.copy(fontSize = 13.sp), color = TextSecondary)
                                Text(text = value, style = TechnicalSmall.copy(fontSize = 13.sp), color = TextPrimary)
                            }
                        }

                        Divider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "DAC", style = BodyMedium.copy(fontSize = 13.sp), color = TextSecondary)
                            Text(text = "RME ADI-2 DAC fs", style = TechnicalSmall.copy(fontSize = 13.sp, color = AmberGold))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Output Mode", style = BodyMedium.copy(fontSize = 13.sp), color = TextSecondary)
                            Text(text = "PCM ${song.bitDepth}/${song.sampleRate}", style = TechnicalSmall.copy(fontSize = 13.sp, color = AmberGold))
                        }

                        Divider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.audioEngine.playNext(song)
                                    selectedSongForSheet = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundCard),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Play Next", style = BodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            }
                            Button(
                                onClick = {
                                    viewModel.audioEngine.addToQueue(song)
                                    selectedSongForSheet = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BackgroundCard),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Add to Queue", style = BodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            }
                        }
                    }
                } else {
                    // Tapping kebab on standard non-active song elements setup: details options layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SONG OPTIONS",
                                style = TechnicalSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
                                color = AmberGold
                            )
                            IconButton(onClick = { selectedSongForSheet = null }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }

                        Divider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎵 ${song.title} — ${song.artist}",
                                style = BodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${song.format} • ${song.bitDepth}/${song.sampleRate} • ${song.durationText}",
                            style = BodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )

                        Divider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                        Button(
                            onClick = {
                                viewModel.playSong(song)
                                selectedSongForSheet = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Now", tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Now", style = BodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.Black))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val actions = listOf(
                            "Play Next" to { viewModel.audioEngine.playNext(song) },
                            "Add to Queue" to { viewModel.audioEngine.addToQueue(song) },
                            "View Album" to { /* no-op details logic */ },
                            "View Artist" to { /* no-op details logic */ },
                            "Add to Favorites" to { viewModel.toggleFavorite(song) },
                            "Song Information" to { /* metadata viewer */ }
                        )

                        actions.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { (label, action) ->
                                    OutlinedButton(
                                        onClick = {
                                            action()
                                            selectedSongForSheet = null
                                        },
                                        border = BorderStroke(0.5.dp, BorderSubtle),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                                    ) {
                                        Text(text = label, style = BodyMedium.copy(fontSize = 12.sp))
                                    }
                                }
                            }
                        }

                        Divider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            text = "File: ${song.path}",
                            style = TechnicalSmall.copy(fontSize = 10.sp),
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// --- TAB SUBSCREENS ---

@Composable
fun SongsListView(
    songs: List<Song>,
    activeSong: Song?,
    onSongClick: (Song) -> Unit,
    onFavClick: (Song) -> Unit,
    onKebabClick: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyStateView(text = "No audiophile tracks found. Trigger a sync/scan.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
        ) {
            items(songs) { song ->
                val isActive = activeSong?.id == song.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isActive) BackgroundElevated else Color.Transparent)
                        .clickable { onSongClick(song) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Album Art Vector Placeholder (Spinning Vinyl)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BackgroundCard),
                        contentAlignment = Alignment.Center
                    ) {
                        VinylArtVector(modifier = Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = BodyLarge.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) AmberGold else TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            style = BodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Technical Specs
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FormatBadge(format = song.format)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${song.bitDepth} • ${song.sampleRate}",
                                style = TechnicalSmall,
                                color = TextSecondary
                              )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { onFavClick(song) }) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (song.isFavorite) Color.Red else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { onKebabClick(song) }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Divider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun AlbumsGridView(albums: List<AlbumItem>, onAlbumClick: (AlbumItem) -> Unit) {
    if (albums.isEmpty()) {
        EmptyStateView(text = "No albums. Add a music directory.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums) { album ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album) }
                        .testTag("album_card_${album.title}"),
                    colors = CardDefaults.cardColors(containerColor = BackgroundSurface),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Drawing dynamic hardware cover internally
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(BackgroundCard)
                        ) {
                            if (album.title.contains("Supreme", ignoreCase = true)) {
                                TubeAmpVectorArt()
                            } else {
                                CassetteTapeVectorArt()
                            }
                        }

                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = album.title,
                                style = BodyLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = album.artist,
                                style = BodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${album.tacksCount} TRACKS",
                                    style = TechnicalSmall,
                                    fontSize = 10.sp
                                )
                                FormatBadge(format = album.formatCode)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsListView(artists: List<ArtistItem>, onArtistClick: (ArtistItem) -> Unit) {
    if (artists.isEmpty()) {
        EmptyStateView("No artists indexed currently.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(artists) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick(artist) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(BackgroundCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Artist",
                            tint = AmberGold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = artist.name, style = BodyLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${artist.albumsCount} ALBUMS • ${artist.tracksCount} TRACKS",
                            style = TechnicalSmall
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Details",
                        tint = TextSecondary
                    )
                }
                Divider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp)
            }
        }
    }
}

private fun getFolderDisplayName(path: String, context: android.content.Context): String {
    if (path.startsWith("content://")) {
        return try {
            val file = DocumentFile.fromTreeUri(context, Uri.parse(path))
            file?.name ?: path
        } catch (e: Exception) {
            path.substringAfterLast("%2F").substringAfterLast("%3A")
        }
    } else {
        return path.substringAfterLast('/')
    }
}

@Composable
fun FoldersListView(
    folders: List<Folder>,
    onAddFolderClick: () -> Unit,
    onScanClick: (Folder) -> Unit,
    onDeleteClick: (Folder) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(folders) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Folder",
                        tint = AmberGold,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = getFolderDisplayName(folder.path, LocalContext.current),
                            style = TechnicalLarge.copy(fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${folder.fileCount} files • ${folder.totalSize} • Last: ${folder.lastScan}",
                            style = TechnicalSmall,
                            fontSize = 11.sp
                        )
                    }

                    Row {
                        IconButton(onClick = { onScanClick(folder) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Folder",
                                tint = AmberGlow,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onDeleteClick(folder) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Folder",
                                tint = VUPeak,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Divider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp)
            }
        }

        // Add Folder Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clickable { onAddFolderClick() }
                .border(1.dp, color = AmberGold, shape = RoundedCornerShape(8.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = AmberGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADD PATH DIRECTORY", style = TechnicalLarge.copy(color = AmberGold, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(modifier = Modifier.height(84.dp)) // padding for bottom menu offset
    }
}

// --- SUB ELEMENTS ---

@Composable
fun FormatBadge(format: String) {
    val (color, label) = when (format.uppercase()) {
        "WAV" -> Pair(WavGold, "WAV")
        "FLAC" -> Pair(FlacTeal, "FLAC")
        "DSD", "DSD256", "DSD512" -> Pair(DsdPurple, "DSD")
        "MP3" -> Pair(Mp3Gray, "MP3")
        "AIFF" -> Pair(AiffBlue, "AIFF")
        else -> Pair(TextSecondary, format.uppercase())
    }

    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.8f),
                shape = RoundedCornerShape(3.dp)
            )
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = LabelCaps.copy(
                fontSize = 9.sp,
                color = color,
                fontWeight = FontWeight.Black
            )
        )
    }
}

@Composable
fun EmptyStateView(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = BodyMedium.copy(textAlign = TextAlign.Center),
            color = TextSecondary
        )
    }
}

// Vector Renderers
@Composable
fun VinylArtVector(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.padding(4.dp)) {
        val sizePx = size.width
        val center = sizePx / 2
        val radius = sizePx / 2

        // Outer record body dark charcoal
        drawCircle(color = Color(0xFF1F1F1F), radius = radius)
        drawCircle(color = Color.Black, radius = radius, style = Stroke(width = 1.dp.toPx()))

        // Vinyl ridges groove lines
        drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * 0.8f, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * 0.6f, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = Color.White.copy(alpha = 0.08f), radius = radius * 0.4f, style = Stroke(width = 1.dp.toPx()))

        // Center golden label hub
        drawCircle(color = AmberGold, radius = radius * 0.25f)
        drawCircle(color = BackgroundPrimary, radius = radius * 0.08f)
    }
}

@Composable
fun TubeAmpVectorArt() {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        // Background chassis
        drawRect(color = Color(0xFF101010))

        // Draw 3 tube bodies with glowing orange elements
        val spacing = w / 4
        for (i in 1..3) {
            val tubeX = spacing * i
            val tubeY = h * 0.35f
            val tubeW = w * 0.12f
            val tubeH = h * 0.45f

            // Tube glass silhouette
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = androidx.compose.ui.geometry.Offset(tubeX - tubeW / 2, tubeY),
                size = androidx.compose.ui.geometry.Size(tubeW, tubeH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(tubeW * 0.3f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Orange base glow
            drawCircle(
                color = AmberGold.copy(alpha = 0.2f),
                center = androidx.compose.ui.geometry.Offset(tubeX, tubeY + tubeH * 0.8f),
                radius = tubeW * 1.5f
            )

            // Inner heating filament
            drawLine(
                strokeWidth = 3.dp.toPx(),
                color = AmberGlow,
                start = androidx.compose.ui.geometry.Offset(tubeX - 2.dp.toPx(), tubeY + tubeH * 0.3f),
                end = androidx.compose.ui.geometry.Offset(tubeX + 2.dp.toPx(), tubeY + tubeH * 0.7f),
                cap = StrokeCap.Round
            )
        }

        // Lower solid metallic bar
        drawRect(
            color = Color(0xFF1C1813),
            topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.85f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.15f)
        )
    }
}

@Composable
fun CassetteTapeVectorArt() {
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height

        // Cassette shell
        val outlinePath = Path().apply {
            moveTo(w * 0.1f, h * 0.2f)
            lineTo(w * 0.9f, h * 0.2f)
            lineTo(w * 0.9f, h * 0.8f)
            lineTo(w * 0.1f, h * 0.8f)
            close()
        }
        drawPath(outlinePath, color = Color(0xFF111111))
        drawPath(outlinePath, color = AmberMuted.copy(alpha = 0.3f), style = Stroke(width = 2.dp.toPx()))

        // Spindle hubs
        drawCircle(color = Color(0xFF222222), center = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.5f), radius = w * 0.12f)
        drawCircle(color = Color(0xFF222222), center = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.5f), radius = w * 0.12f)

        // Cog gears
        drawCircle(color = AmberGold, center = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.5f), radius = w * 0.03f)
        drawCircle(color = AmberGold, center = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.5f), radius = w * 0.03f)

        // Trailing magnetic film tape
        drawLine(
            color = Color(0xFF332A1A),
            strokeWidth = 6.dp.toPx(),
            start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.75f),
            end = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.75f)
        )
    }
}

private fun formatTimeLocal(ms: Long): String {
    val totalSecs = ms / 1000
    val secs = totalSecs % 60
    val mins = totalSecs / 60
    return String.format("%02d:%02d", mins, secs)
}
