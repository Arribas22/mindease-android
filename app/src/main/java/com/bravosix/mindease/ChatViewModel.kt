package com.bravosix.mindease

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bravosix.mindease.db.AppDatabase
import com.bravosix.mindease.db.ChatMessage
import com.bravosix.mindease.db.ChatSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel scoped to the Chat nav-entry. Survives tab switches, keeping the
 * conversation alive in memory without restarting a new session on every recomposition.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db  = AppDatabase.get(app)
    private val llm = LlmManager.get(app)
    private val ctx get() = getApplication<Application>()

    private val _sessionId       = MutableStateFlow(-1L)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

    private val _messages        = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating    = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingBuffer = MutableStateFlow("")
    val streamingBuffer: StateFlow<String> = _streamingBuffer.asStateFlow()

    private val _sessions        = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private var messagesJob: Job? = null

    init {
        // Observe all sessions list
        viewModelScope.launch {
            db.chatDao().getSessions(50).collect { _sessions.value = it }
        }
        // Load the most recent session, or create one if none exists
        viewModelScope.launch {
            val last = db.chatDao().getLastSession()
            if (last != null) openSession(last.id) else startNewSession()
        }
    }

    /** Switch to an existing session and start collecting its messages. */
    fun openSession(id: Long) {
        _sessionId.value = id
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            db.chatDao().getMessages(id).collect { _messages.value = it }
        }
    }

    /** Create a fresh empty session and open it. */
    fun startNewSession() {
        viewModelScope.launch {
            val id = db.chatDao().insertSession(ChatSession(title = "Conversación"))
            openSession(id)
        }
    }

    /** Delete a session. If it was the active one, open the next available or create new. */
    fun deleteSession(id: Long) {
        viewModelScope.launch {
            db.chatDao().deleteSession(id)
            if (_sessionId.value == id) {
                val next = db.chatDao().getLastSession()
                if (next != null) openSession(next.id) else startNewSession()
            }
        }
    }

    /** Send a message in the current session. Delegates all LLM interaction to LlmManager. */
    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return
        val sid = _sessionId.value
        if (sid < 0) return

        // IMPORTANT: read history BEFORE inserting the new user message.
        // Room's Flow updates _messages synchronously; if we read after inserting,
        // the user message appears in history AND is passed again as userMessage →
        // two consecutive user turns in the prompt → Gemma session throws an exception.
        val history = _messages.value.map { it.role to it.content }

        viewModelScope.launch {
            if (!Prefs.consumeToken(ctx)) return@launch  // guard: should never be false here
            db.chatDao().insertMessage(ChatMessage(sessionId = sid, role = "user", content = text))

            _isGenerating.value    = true
            _streamingBuffer.value = ""

            llm.generateResponse(
                conversationHistory = history,
                userMessage         = text,
                onToken = { token ->
                    _streamingBuffer.value += token
                },
                onDone  = { full ->
                    _isGenerating.value    = false
                    _streamingBuffer.value = ""
                    viewModelScope.launch {
                        db.chatDao().insertMessage(
                            ChatMessage(sessionId = sid, role = "assistant", content = full)
                        )
                        // Auto-title the session from the first user message
                        if (_messages.value.size <= 2) {
                            val title = text.take(40).let { if (text.length > 40) "$it…" else it }
                            db.chatDao().updateSession(ChatSession(id = sid, title = title))
                        }
                    }
                },
                onError = { _ ->
                    _isGenerating.value    = false
                    _streamingBuffer.value = ""
                    viewModelScope.launch {
                        db.chatDao().insertMessage(
                            ChatMessage(
                                sessionId = sid,
                                role      = "assistant",
                                content   = "Lo siento, hubo un error al generar la respuesta."
                            )
                        )
                    }
                }
            )
        }
    }
}
