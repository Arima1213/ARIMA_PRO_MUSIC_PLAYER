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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: AudioViewModel) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Custom Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { /* Menu Action */ },
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

                Row {
                    IconButton(
                        onClick = { viewModel.showEqualizerPanel.value = true },
                        modifier = Modifier.testTag("eq_shortcut_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Equalizer",
                            tint = TextPrimary
                        )
                    }
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
            }

            // 2. Tab Select Chips (Songs, Albums, Artists, Folders)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Songs", "Albums", "Artists", "Folders").forEach { tab ->
                    val isSelected = currentTab.equals(tab, ignoreCase = true)
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
                        Text(
                            text = tab,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else TextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Search Bar
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
                            text = "Search library...",
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

            // 4. Header Label Details (Counts)
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

            // 5. Active Tab Contents
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
                        onFavClick = { viewModel.toggleFavorite(it) }
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
                        onAddFolderClick = { showAddFolder = true },
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
    }
}

// --- TAB SUBSCREENS ---

@Composable
fun SongsListView(
    songs: List<Song>,
    activeSong: Song?,
    onSongClick: (Song) -> Unit,
    onFavClick: (Song) -> Unit
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

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = { onFavClick(song) }) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (song.isFavorite) Color.Red else TextSecondary,
                                modifier = Modifier.size(20.dp)
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
                            text = folder.path,
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
