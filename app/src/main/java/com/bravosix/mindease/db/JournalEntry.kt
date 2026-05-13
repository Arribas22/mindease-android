package com.bravosix.mindease.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entrada del diario libre.
 * sentiment: -1.0 (muy negativo) → 1.0 (muy positivo), calculado localmente
 *            por CrisisDetector / análisis de palabras clave.
 */
@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val content: String,
    val sentiment: Float = 0f,    // calculado localmente
    val keywords: String = "[]"   // JSON array de palabras clave detectadas
)
