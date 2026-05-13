package com.bravosix.mindease.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registro diario del estado de ánimo.
 * level: 1 (muy mal) → 10 (excelente)
 * tags: lista JSON de etiquetas emocionales ("ansioso", "triste", "feliz", etc.)
 */
@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: Int,               // 1–10
    val tags: String = "[]",      // JSON array de strings
    val note: String = ""
)
