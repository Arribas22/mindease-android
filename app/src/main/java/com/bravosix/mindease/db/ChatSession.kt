package com.bravosix.mindease.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Sesión de chat con la IA. Agrupa múltiples mensajes. */
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val title: String = "",
    val messageCount: Int = 0
)
