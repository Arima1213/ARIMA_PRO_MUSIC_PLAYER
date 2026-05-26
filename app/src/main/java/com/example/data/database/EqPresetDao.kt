package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EqPresetDao {
    @Query("SELECT * FROM eq_presets")
    fun getAllPresetsFlow(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_presets")
    suspend fun getAllPresets(): List<EqPresetEntity>

    @Query("SELECT * FROM eq_presets WHERE name = :name LIMIT 1")
    suspend fun getPresetByName(name: String): EqPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: EqPresetEntity)

    @Delete
    suspend fun deletePreset(preset: EqPresetEntity)
}
