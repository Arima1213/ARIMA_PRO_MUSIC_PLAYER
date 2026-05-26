package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongsSorted(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFavorite: Boolean)

    @Query("DELETE FROM songs WHERE folderPath = :folderPath")
    suspend fun deleteSongsByFolder(folderPath: String)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()
}
