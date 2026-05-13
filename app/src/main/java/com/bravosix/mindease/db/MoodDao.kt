package com.bravosix.mindease.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Insert
    suspend fun insert(entry: MoodEntry): Long

    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 30): Flow<List<MoodEntry>>

    @Query("SELECT * FROM mood_entries WHERE timestamp >= :fromMs ORDER BY timestamp ASC")
    suspend fun getSince(fromMs: Long): List<MoodEntry>

    @Query("SELECT AVG(level) FROM mood_entries WHERE timestamp >= :fromMs")
    suspend fun averageSince(fromMs: Long): Float?

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
