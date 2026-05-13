package com.bravosix.mindease.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bravosix.mindease.*
import com.bravosix.mindease.db.ChatMessage
import com.bravosix.mindease.db.ChatSession
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val ctx = LocalContext.current
    val llm = remember { LlmManager.get(ctx) }
    val llmState      by llm.state.collectAsState()
    val downloadState by ModelDownloadManager.downloadState.collectAsState()

    val sessionId       by vm.sessionId.collectAsState()
    val messages        by vm.messages.collectAsState()
    val isGenerating    by vm.isGenerating.collectAsState()
    val streamingBuffer by vm.streamingBuffer.collectAsState()
    val sessions        by vm.sessions.collectAsState()

    var inputText         by remember { mutableStateOf("") }
    var showHistory       by remember { mutableStateOf(false) }
    var showLimitDialog   by remember { mutableStateOf(false) }
    var showCrisisWarning by remember { mutableStateOf("") }
    var bannerDismissed   by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll al último mensaje
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank() || isGenerating) return
        if (!Prefs.canSendMessage(ctx)) { showLimitDialog = true; return }
        val crisis = CrisisDetector.analyze(text)
        if (crisis.level == CrisisDetector.RiskLevel.HIGH) {
            showCrisisWarning = CrisisDetector.crisisMessage(crisis.level)
        }
        inputText = ""
        vm.sendMessage(text)
    }

    // Historia de conversaciones (desliza desde abajo)
    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            ChatHistorySheet(
                sessions  = sessions,
                currentId = sessionId,
                onSelect  = { id -> vm.openSession(id); showHistory = false },
                onDelete  = { id -> vm.deleteSession(id) },
                onNew     = { vm.startNewSession(); showHistory = false }
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Default.History, contentDescription = "Historial de conversaciones")
                }
                Column(Modifier.weight(1f)) {
                    Text("MindEase IA", style = MaterialTheme.typography.titleLarge)
                    Text(
                        when (llmState) {
                            LlmManager.State.READY       -> "🟢 Gemma 3 1B · Local"
                            LlmManager.State.LOADING     -> "⏳ Cargando modelo…"
                            LlmManager.State.UNSUPPORTED -> "⚙️ Modo básico"
                            LlmManager.State.ERROR       -> "⚠️ Error del modelo"
                            else                         -> "💬 Modo básico · Local"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Token counter chip
                val isPremium = Prefs.isPremium(ctx)
                val tokens    = Prefs.totalTokens(ctx)
                AssistChip(
                    onClick = {},
                    label   = {
                        Text(if (isPremium) "✨ Vitalicio" else "🎯 $tokens tokens")
                    },
                    colors  = AssistChipDefaults.assistChipColors(
                        containerColor = when {
                            isPremium  -> MaterialTheme.colorScheme.primaryContainer
                            tokens > 3 -> MaterialTheme.colorScheme.secondaryContainer
                            else       -> MaterialTheme.colorScheme.errorContainer
                        }
                    )
                )
                // Nueva conversación
                IconButton(onClick = { vm.startNewSession() }) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva conversación")
                }
            }
        }

        // Banner descarga modelo IA — solo visible cuando no hay modelo y no se ha descartado
        val showDownloadBanner = !bannerDismissed &&
            llmState == LlmManager.State.IDLE &&
            downloadState !is ModelDownloadManager.DownloadState.Done &&
            !LlmManager.isModelDownloaded(ctx) &&
            !LlmManager.isModelInAssets(ctx)  // si está en assets se extrae automáticamente

        AnimatedVisibility(
            visible = showDownloadBanner || downloadState is ModelDownloadManager.DownloadState.Downloading,
            enter = expandVertically(),
            exit  = shrinkVertically()
        ) {
            ModelDownloadBanner(
                downloadState = downloadState,
                onDownload    = { ModelDownloadManager.startDownload(ctx) },
                onCancel      = { ModelDownloadManager.cancel(ctx) },
                onDismiss     = { bannerDismissed = true }
            )
        }

        // Notificar al LlmManager cuando la descarga termina
        LaunchedEffect(downloadState) {
            if (downloadState is ModelDownloadManager.DownloadState.Done) {
                llm.onModelDownloaded()
            }
        }

        // Crisis warning banner
        if (showCrisisWarning.isNotBlank()) {
            Card(
                modifier = Modifier.padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Column {
                        Text("Recursos de ayuda", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(showCrisisWarning, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Lista de mensajes
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Mensaje de bienvenida
            if (messages.isEmpty() && !isGenerating) {
                item {
                    AssistantBubble(
                        "Hola 👋 Soy MindEase, tu asistente de bienestar emocional. " +
                        "Estoy aquí para escucharte y acompañarte. " +
                        "Todo lo que escribas se procesa en tu dispositivo — nadie más lo lee.\n\n" +
                        "¿Cómo te sientes hoy?"
                    )
                }
            }

            items(messages) { msg ->
                if (msg.role == "user") {
                    UserBubble(msg.content)
                } else {
                    AssistantBubble(msg.content)
                }
                // Earn-tokens nudge every 5 assistant messages (non-premium only)
                val idx = messages.indexOf(msg)
                if (!Prefs.isPremium(ctx) && msg.role == "assistant" && (idx + 1) % 10 == 0) {
                    EarnTokensNudge(
                        onWatch = {
                            RewardedAdManager.show(
                                activity     = ctx as Activity,
                                tokensAmount = Prefs.TOKENS_PER_TIP_AD,
                                onReward     = { _ -> },
                                onUnavailable = { }
                            )
                        }
                    )
                }
            }

            // Burbuja de streaming
            if (isGenerating && streamingBuffer.isNotBlank()) {
                item { AssistantBubble(streamingBuffer + " ▌") }
            } else if (isGenerating) {
                item {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(8.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }

        // Input area
        Surface(shadowElevation = 4.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Escribe un mensaje…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    enabled = !isGenerating
                )
                FilledIconButton(
                    onClick = ::sendMessage,
                    enabled = inputText.isNotBlank() && !isGenerating
                ) {
                    Icon(Icons.Default.Send, "Enviar")
                }
            }
        }
    }

    // Diálogo sin tokens — ofrece ver vídeo o ir a tienda
    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("🎯 Sin tokens de chat") },
            text  = {
                Text(
                    "Has agotado tus tokens de hoy.\n\n" +
                    "• Ve un vídeo corto y gana +${Prefs.TOKENS_PER_AD} tokens ahora mismo.\n" +
                    "• O consigue tokens ilimitados con el Plan Vitalicio."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showLimitDialog = false
                    RewardedAdManager.show(
                        activity     = ctx as Activity,
                        tokensAmount = Prefs.TOKENS_PER_AD,
                        onReward     = { _ -> /* chips se refrescan en recomposición */ },
                        onUnavailable = { /* anuncio aún no disponible */ }
                    )
                }) { Text("🎥 Ver vídeo (+${Prefs.TOKENS_PER_AD})") }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) { Text("Cerrar") }
            }
        )
    }
}

@Composable
private fun EarnTokensNudge(onWatch: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🎁", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ve un vídeo corto y gana +${Prefs.TOKENS_PER_TIP_AD} tokens",
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onWatch, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Ver", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ModelDownloadBanner(
    downloadState: ModelDownloadManager.DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "IA avanzada disponible",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (downloadState !is ModelDownloadManager.DownloadState.Downloading) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Cerrar", modifier = Modifier.size(16.dp))
                    }
                }
            }

            when (downloadState) {
                is ModelDownloadManager.DownloadState.Downloading -> {
                    Text(
                        "Descargando modelo Gemma 3 1B… ${downloadState.progressPct}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    LinearProgressIndicator(
                        progress = { downloadState.progressPct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = onCancel) { Text("Cancelar descarga") }
                }
                is ModelDownloadManager.DownloadState.Failed -> {
                    Text(
                        "Error: ${downloadState.reason}. Toca para reintentar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onDownload) { Text("Reintentar") }
                }
                else -> {
                    Text(
                        "Descarga el modelo Gemma 3 1B (~600 MB) para respuestas más profundas y personalizadas. " +
                        "Solo se descarga una vez y funciona sin internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload) {
                            Icon(Icons.Default.CloudDownload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Descargar (600 MB)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(text, Modifier.padding(12.dp, 10.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(text, Modifier.padding(12.dp, 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistorySheet(
    sessions:  List<ChatSession>,
    currentId: Long,
    onSelect:  (Long) -> Unit,
    onDelete:  (Long) -> Unit,
    onNew:     () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Conversaciones",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = onNew) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nueva")
            }
        }
        HorizontalDivider()
        if (sessions.isEmpty()) {
            Text(
                "No hay conversaciones guardadas aún",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == currentId
                    ListItem(
                        headlineContent = {
                            Text(
                                session.title.ifBlank { "Conversación" },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines   = 1
                            )
                        },
                        supportingContent = {
                            Text(
                                formatSessionDate(session.startedAt),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onDelete(session.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar conversación",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.clickable { onSelect(session.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatSessionDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val fmt  = SimpleDateFormat("HH:mm", Locale.getDefault())
    val fmtD = SimpleDateFormat("d MMM, HH:mm", Locale("es"))
    return when {
        diff < 60_000L         -> "Ahora mismo"
        diff < 3_600_000L      -> "${diff / 60_000} min"
        diff < 86_400_000L     -> "Hoy, ${fmt.format(Date(timestamp))}"
        diff < 2 * 86_400_000L -> "Ayer, ${fmt.format(Date(timestamp))}"
        else                   -> fmtD.format(Date(timestamp))
    }
}
