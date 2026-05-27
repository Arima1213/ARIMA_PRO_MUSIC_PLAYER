package com.example.data.repository

import com.example.data.database.*
import com.example.domain.model.EqPreset
import com.example.domain.model.Folder
import com.example.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AudioRepository(private val db: AppDatabase) {

    private val songDao = db.songDao()
    private val folderDao = db.folderDao()
    private val eqPresetDao = db.eqPresetDao()

    // --- Songs ---
    val allSongs: Flow<List<Song>> = songDao.getAllSongsSorted().map { entities ->
        entities.map { it.toDomain() }
    }

    val favoriteSongs: Flow<List<Song>> = songDao.getFavoriteSongs().map { entities ->
        entities.map { it.toDomain() }
    }

    fun searchSongs(query: String): Flow<List<Song>> = songDao.searchSongs(query).map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun insertSongs(songs: List<Song>) {
        songDao.insertSongs(songs.map { it.toEntity() })
    }

    suspend fun updateFavorite(id: Int, isFavorite: Boolean) {
        songDao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteSongsByFolder(folderPath: String) {
        songDao.deleteSongsByFolder(folderPath)
    }

    // --- Folders ---
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun insertFolder(folder: Folder) {
        folderDao.insertFolder(folder.toEntity())
    }

    suspend fun deleteFolder(folder: Folder) {
        // Also delete songs inside this folder for cleanliness
        songDao.deleteSongsByFolder(folder.path)
        folderDao.deleteFolder(folder.toEntity())
    }

    // --- Equalizer Presets ---
    val allPresets: Flow<List<EqPreset>> = eqPresetDao.getAllPresetsFlow().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getPresetsList(): List<EqPreset> {
        return eqPresetDao.getAllPresets().map { it.toDomain() }
    }

    suspend fun getPresetByName(name: String): EqPreset? {
        return eqPresetDao.getPresetByName(name)?.toDomain()
    }

    suspend fun insertPreset(preset: EqPreset) {
        eqPresetDao.insertPreset(preset.toEntity())
    }

    suspend fun deletePreset(preset: EqPreset) {
        eqPresetDao.deletePreset(preset.toEntity())
    }

    // --- Helper Extensions for Mapping ---
    private fun SongEntity.toDomain() = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        format = format,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        fileSize = fileSize,
        path = path,
        folderPath = folderPath,
        isFavorite = isFavorite,
        albumArt = albumArt
    )

    private fun Song.toEntity() = SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        format = format,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        fileSize = fileSize,
        path = path,
        folderPath = folderPath,
        isFavorite = isFavorite,
        albumArt = albumArt
    )

    private fun FolderEntity.toDomain() = Folder(
        path = path,
        fileCount = fileCount,
        totalSize = totalSize,
        lastScan = lastScan
    )

    private fun Folder.toEntity() = FolderEntity(
        path = path,
        fileCount = fileCount,
        totalSize = totalSize,
        lastScan = lastScan
    )

    private fun EqPresetEntity.toDomain() = EqPreset(
        name = name,
        bandGains = bandGains.split(",").map { it.toFloatOrNull() ?: 0.0f },
        preamp = preamp
    )

    private fun EqPreset.toEntity() = EqPresetEntity(
        name = name,
        bandGains = bandGains.joinToString(","),
        preamp = preamp
    )
}
