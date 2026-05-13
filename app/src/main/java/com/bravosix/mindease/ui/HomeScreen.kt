package com.bravosix.mindease.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.bravosix.mindease.db.AppDatabase
import com.bravosix.mindease.db.MoodEntry
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val EMOTION_TAGS = listOf(
    "😊 Feliz", "😌 Tranquilo", "😐 Neutro", "😟 Triste",
    "😰 Ansioso", "😤 Frustrado", "😴 Cansado", "💪 Motivado"
)

private val DAILY_QUOTES = listOf(
    "\"Cuidar tu mente es el acto de amor más importante hacia ti mismo.\"",
    "\"Cada día es una nueva oportunidad para empezar de nuevo.\"",
    "\"La calma no es ausencia de tormenta, sino paz en medio de ella.\"",
    "\"Pequeños pasos cada día llevan a grandes transformaciones.\"",
    "\"Tu bienestar es una prioridad, no un lujo.\"",
    "\"Respira. El presente es el único momento que tienes.\"",
    "\"Eres más fuerte de lo que crees y más valioso de lo que imaginas.\""
)

@Composable
fun HomeScreen(navController: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.get(ctx) }

    var moodLevel by remember { mutableIntStateOf(5) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    val recentEntries by db.moodDao().getRecent(30).collectAsState(initial = emptyList())
    val alreadyCheckedIn = remember(recentEntries) {
        recentEntries.isNotEmpty() &&
        isSameDay(recentEntries.first().timestamp, System.currentTimeMillis())
    }
    val streak = remember(recentEntries) { calculateStreak(recentEntries) }
    val dailyQuote = remember {
        DAILY_QUOTES[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % DAILY_QUOTES.size]
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Saludo
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Buenos días ☀️"
            hour < 19 -> "Buenas tardes 🌤️"
            else      -> "Buenas noches 🌙"
        }
        Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es")).format(Date()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (alreadyCheckedIn || saved) {
            // Check-in completado + racha
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                "Check-in de hoy completado ✓",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "¡Sigue así, lo estás haciendo bien!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔥", fontSize = 22.sp)
                        Text(
                            "$streak días",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Frase del día
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "💡 Frase del día",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        dailyQuote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Card de check-in
            Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("¿Cómo te sientes hoy?", style = MaterialTheme.typography.titleLarge)

                    Text(
                        moodEmoji(moodLevel) + "  ${moodLabel(moodLevel)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Slider(
                        value = moodLevel.toFloat(),
                        onValueChange = { moodLevel = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1 - Muy mal", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("10 - Excelente", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Text("¿Qué emociones describes?", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EMOTION_TAGS.size) { i ->
                            val tag = EMOTION_TAGS[i]
                            val selected = tag in selectedTags
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedTags = if (selected) selectedTags - tag else selectedTags + tag
                                },
                                label = { Text(tag, fontSize = 13.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota (opcional)") },
                        placeholder = { Text("¿Qué está pasando hoy?") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                val tagsJson = org.json.JSONArray(selectedTags.toList()).toString()
                                db.moodDao().insert(
                                    MoodEntry(level = moodLevel, tags = tagsJson, note = note)
                                )
                                saved = true
                                note = ""
                                selectedTags = emptySet()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardar check-in")
                    }
                }
            }
        }

        // Sección de ejercicios
        Text("🧘 Ejercicios de bienestar", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ExerciseCard("🌬️", "Respiración\n4-7-8", "1 min · Ansiedad") { navController.navigate("exercises") } }
            item { ExerciseCard("🌿", "Técnica\n5-4-3-2-1", "3 min · Grounding") { navController.navigate("exercises") } }
            item { ExerciseCard("🧘", "Meditación\nguiada", "5 min · Calma") { navController.navigate("exercises") } }
            item { ExerciseCard("💪", "Body\nScan", "8 min · Relajación") { navController.navigate("exercises") } }
        }

        // Accesos rápidos
        Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard(
                title = "Chat con IA",
                subtitle = "Habla con MindEase",
                emoji = "💬",
                modifier = Modifier.weight(1f)
            ) { navController.navigate("chat") }
            QuickCard(
                title = "Mi Diario",
                subtitle = "Reflexiones y notas",
                emoji = "📓",
                modifier = Modifier.weight(1f)
            ) { navController.navigate("journal") }
        }

        // Banner publicitario
        AndroidView(
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = "ca-app-pub-3940256099942544/6300978111"
                    loadAd(AdRequest.Builder().build())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )

        // Disclaimer
        Text(
            "⚠️ MindEase no sustituye a un profesional de salud mental. " +
            "En crisis llama al 024 (gratuito, 24h).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun ExerciseCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 26.sp)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun QuickCard(
    title: String,
    subtitle: String,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, fontSize = 28.sp)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun calculateStreak(entries: List<MoodEntry>): Int {
    if (entries.isEmpty()) return 0
    val sortedDays = entries.map {
        val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }.distinct().sorted().reversed()

    var streak = 0
    val today = Calendar.getInstance()
    var expected = today.get(Calendar.YEAR) * 1000 + today.get(Calendar.DAY_OF_YEAR)
    for (day in sortedDays) {
        if (day == expected) { streak++; expected-- } else break
    }
    return streak
}

private fun moodEmoji(level: Int): String = when (level) {
    1, 2 -> "😣"; 3, 4 -> "😟"; 5, 6 -> "😐"; 7, 8 -> "🙂"; else -> "😄"
}
private fun moodLabel(level: Int): String = when (level) {
    1 -> "Muy mal"; 2 -> "Mal"; 3 -> "Bastante mal"; 4 -> "Regular-mal"
    5 -> "Regular"; 6 -> "Regular-bien"; 7 -> "Bien"; 8 -> "Bastante bien"
    9 -> "Muy bien"; else -> "Excelente"
}
private fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
