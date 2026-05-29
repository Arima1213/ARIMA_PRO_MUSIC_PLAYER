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
import com.example.ui.screens.CustomPauseIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Build

import com.example.ui.components.AppHeader
import com.example.ui.components.glassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: AudioViewModel
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
            
            // 1. TOP BAR
            AppHeader(
                actions = {
                    IconButton(onClick = { viewModel.selectTab("settings") }, modifier = Modifier.testTag("settings_button")) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                }
            )

            // 3. TAB ROW
            val tabs = listOf("Songs", "Albums", "Artists", "Folders")
            val tabIcons = listOf(Icons.Default.MusicNote, Icons.Default.Star, Icons.Default.Person, Icons.Default.Folder)
            val selectedTabIndex = tabs.indexOfFirst { it.equals(currentTab, ignoreCase = true) }.coerceAtLeast(0)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(44.dp)
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(22.dp)
                    }
                    .background(Color(0x33000000)) // rgba(0,0,0,0.2)
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            val currentTabPosition = tabPositions[selectedTabIndex]
                            val animOffset by animateDpAsState(
                                targetValue = currentTabPosition.left,
                                animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                                label = "TabOffset"
                            )
                            val animWidth by animateDpAsState(
                                targetValue = currentTabPosition.width,
                                animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
                                label = "TabWidth"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .wrapContentHeight(Alignment.Bottom)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = animOffset)
                                        .width(animWidth)
                                        .padding(horizontal = 16.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(AmberGold)
                                )
                            }
                        }
                    }
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = index == selectedTabIndex
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectLibraryTab(tab.lowercase()) },
                            selectedContentColor = TextPrimary,
                            unselectedContentColor = TextSecondary,
                            modifier = Modifier
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0x0FFFFFFF) else Color.Transparent)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = tab,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tab,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
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
                            viewModel.selectAlbum(album)
                        }
                    )
                    "artists" -> ArtistsListView(
                        artists = artists,
                        onArtistClick = { artist ->
                            viewModel.selectArtist(artist)
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

        // Overlay removal
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .glassCard(shape = RoundedCornerShape(16.dp))
                        .clickable { onSongClick(song) }
                        .then(
                            if (isActive) Modifier.background(AmberGold.copy(alpha = 0.1f)) else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // Custom Album Art Vector Placeholder (Spinning Vinyl)
                    val albumArt = song.albumArt
                    val bitmapState = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = albumArt) {
                        if (albumArt != null && albumArt.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                try {
                                    val bmp = android.graphics.BitmapFactory.decodeByteArray(albumArt, 0, albumArt.size)
                                    if (bmp != null && !bmp.isRecycled) {
                                        value = bmp.asImageBitmap()
                                    } else {
                                        value = null
                                    }
                                } catch (e: Exception) {
                                    value = null
                                }
                            }
                        } else {
                            value = null
                        }
                    }
                    val bitmap = bitmapState.value
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BackgroundCard),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap,
                                contentDescription = "Album Art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            VinylArtVector(modifier = Modifier.fillMaxSize())
                        }
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
                } // End Row
                } // End Box (glass card)
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

@Composable
fun AlbumDetailScreen(
    album: AlbumItem,
    songs: List<Song>,
    viewModel: AudioViewModel,
    onBack: () -> Unit
) {
    val activeSong by viewModel.audioEngine.currentSong.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary)) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ALBUMS",
                style = HeadlineSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                color = AmberGold
            )
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Album cover + meta
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Album art placeholder
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundCard),
                contentAlignment = Alignment.Center
            ) {
                if (album.title.contains("Supreme", ignoreCase = true)) {
                    TubeAmpVectorArt()
                } else {
                    CassetteTapeVectorArt()
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = album.title, style = BodyLarge.copy(fontWeight = FontWeight.Bold))
                Text(text = album.artist, style = BodyMedium.copy(color = TextSecondary))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${album.tacksCount} tracks • ${album.formatCode} ${album.sampleRate}",
                    style = TechnicalSmall
                )
            }

            Button(
                onClick = {
                    if (songs.isNotEmpty()) {
                        viewModel.playSong(songs.first(), songs)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PLAY ALL", color = Color.Black, style = BodyMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Track list
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs.size) { index ->
                val song = songs[index]
                val isActive = activeSong?.id == song.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isActive) BackgroundElevated else Color.Transparent)
                        .clickable {
                            viewModel.playSong(song, songs)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = TechnicalSmall.copy(
                            color = if (isActive) AmberGold else TextMuted
                        ),
                        modifier = Modifier.width(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
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
                        Text(
                            text = song.durationText,
                            style = TechnicalSmall,
                            color = TextSecondary
                        )
                    }
                    FormatBadge(format = song.format)
                }
                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artist: ArtistItem,
    songs: List<Song>,
    viewModel: AudioViewModel,
    onBack: () -> Unit
) {
    val activeSong by viewModel.audioEngine.currentSong.collectAsState()
    val groupedByAlbum = songs.groupBy { it.album }.toList().sortedBy { it.first }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundPrimary)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ARTISTS",
                style = HeadlineSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                color = AmberGold
            )
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Artist meta header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(BackgroundCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, "Artist", tint = AmberGold, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = artist.name, style = HeadlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = "${artist.albumsCount} albums • ${artist.tracksCount} tracks",
                    style = TechnicalSmall,
                    color = TextSecondary
                )
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Albums + tracks grouped
        LazyColumn {
            groupedByAlbum.forEach { (albumName, albumSongs) ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = albumName,
                            style = TechnicalSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = AmberGold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                val sorted = albumSongs.sortedBy { it.path }
                                viewModel.playSong(sorted.first(), sorted)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BackgroundCard),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = AmberGold, modifier = Modifier.size(12.dp))
                            Text(" Play", style = TechnicalSmall.copy(color = AmberGold, fontWeight = FontWeight.Bold))
                        }
                    }
                }
                albumSongs.sortedBy { it.path }.forEachIndexed { idx, song ->
                    val isActive = activeSong?.id == song.id
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playSong(song, albumSongs) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = TechnicalSmall.copy(color = TextMuted),
                                modifier = Modifier.width(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = BodyMedium.copy(
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) AmberGold else TextPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.format} • ${song.durationText}",
                                    style = TechnicalSmall,
                                    color = TextSecondary
                                )
                            }
                            FormatBadge(format = song.format)
                        }
                    }
                }
                item {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
            item { Spacer(modifier = Modifier.height(84.dp)) }
        }
    }
}

