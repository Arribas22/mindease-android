package com.bravosix.mindease.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bravosix.mindease.CrisisDetector
import com.bravosix.mindease.db.AppDatabase
import com.bravosix.mindease.db.JournalEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun JournalScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.get(ctx) }

    val entries by db.journalDao().getRecent(50).collectAsState(initial = emptyList())
    var showEditor by remember { mutableStateOf(false) }
    var editorText by remember { mutableStateOf("") }
    var crisisBanner by remember { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<JournalEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Diario", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { showEditor = true }) {
                    Icon(Icons.Default.Add, "Nueva entrada")
                }
            }
        }

        // Crisis banner
        if (crisisBanner.isNotBlank()) {
            Card(
                modifier = Modifier.padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(crisisBanner, Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Tu diario está vacío", style = MaterialTheme.typography.titleMedium)
                    Text("Escribe cómo te sientes para empezar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    JournalCard(
                        entry = entry,
                        onDelete = { deleteCandidate = entry }
                    )
                }
            }
        }
    }

    // Editor de nueva entrada
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false; editorText = "" },
            title = { Text("Nueva entrada") },
            text = {
                OutlinedTextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    placeholder = { Text("¿Cómo te sientes? ¿Qué está pasando?") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = editorText.trim()
                        if (text.isNotBlank()) {
                            // Análisis local de crisis y sentimiento
                            val crisis = CrisisDetector.analyze(text)
                            if (crisis.level != CrisisDetector.RiskLevel.NONE) {
                                crisisBanner = CrisisDetector.crisisMessage(crisis.level)
                            }
                            val sentiment = simpleSentiment(text)
                            scope.launch {
                                db.journalDao().insert(
                                    JournalEntry(content = text, sentiment = sentiment)
                                )
                            }
                        }
                        showEditor = false
                        editorText = ""
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false; editorText = "" }) { Text("Cancelar") }
            }
        )
    }

    // Confirmación de borrado
    deleteCandidate?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("¿Eliminar entrada?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { db.journalDao().delete(entry.id) }
                    deleteCandidate = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun JournalCard(entry: JournalEntry, onDelete: () -> Unit) {
    val date = SimpleDateFormat("dd MMM · HH:mm", Locale("es")).format(Date(entry.timestamp))
    val sentimentEmoji = when {
        entry.sentiment > 0.3f  -> "😊"
        entry.sentiment < -0.3f -> "😟"
        else                    -> "😐"
    }

    Card(shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sentimentEmoji)
                Spacer(Modifier.width(6.dp))
                Text(date, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Eliminar",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                entry.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5
            )
        }
    }
}

/**
 * Análisis de sentimiento simplificado basado en léxico.
 * Devuelve -1.0 (muy negativo) a 1.0 (muy positivo).
 * Para una versión más avanzada se puede integrar ML Kit Text Classification.
 */
private fun simpleSentiment(text: String): Float {
    val lower = text.lowercase()
    val positives = listOf("feliz", "alegre", "bien", "genial", "contento", "contenta",
        "tranquilo", "tranquila", "motivado", "motivada", "ilusión", "esperanza", "gracias")
    val negatives = listOf("triste", "mal", "ansioso", "ansiosa", "miedo", "angustia",
        "solo", "sola", "frustrado", "frustrada", "cansado", "cansada", "agotado", "agotada",
        "llorar", "lloro", "llorando", "desesperado", "desesperada")
    val posCount = positives.count { lower.contains(it) }
    val negCount = negatives.count { lower.contains(it) }
    val total = posCount + negCount
    return if (total == 0) 0f else (posCount - negCount).toFloat() / total
}
