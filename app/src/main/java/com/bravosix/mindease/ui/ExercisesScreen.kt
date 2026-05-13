package com.bravosix.mindease.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Catálogo de ejercicios ────────────────────────────────────────────────────

private data class Exercise(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val category: String,
    val steps: List<String>
)

private val EXERCISES = listOf(
    Exercise(
        id = "478_breath",
        emoji = "🌬️",
        title = "Respiración 4-7-8",
        subtitle = "Reduce la ansiedad rápidamente",
        durationLabel = "3 min",
        category = "Respiración",
        steps = listOf(
            "Exhala completamente por la boca.",
            "Cierra la boca. Inhala por la nariz contando 4 segundos.",
            "Mantén el aire contando 7 segundos.",
            "Exhala completamente por la boca contando 8 segundos.",
            "Ese es 1 ciclo. Repite 4 veces.",
            "¡Lo has hecho muy bien! 🌟"
        )
    ),
    Exercise(
        id = "box_breath",
        emoji = "🟦",
        title = "Respiración en caja",
        subtitle = "Calma el sistema nervioso",
        durationLabel = "4 min",
        category = "Respiración",
        steps = listOf(
            "Siéntate cómodamente con la espalda recta.",
            "Inhala lentamente contando 4 segundos.",
            "Mantén el aire durante 4 segundos.",
            "Exhala lentamente durante 4 segundos.",
            "Mantén los pulmones vacíos 4 segundos.",
            "Repite 4-6 veces. El ritmo es la clave.",
            "Excelente trabajo 💪"
        )
    ),
    Exercise(
        id = "grounding_54321",
        emoji = "🌱",
        title = "Grounding 5-4-3-2-1",
        subtitle = "Ancla al presente con los sentidos",
        durationLabel = "5 min",
        category = "Mindfulness",
        steps = listOf(
            "Nombra en voz alta (o mentalmente) 5 cosas que puedas VER ahora.",
            "Nombra 4 cosas que puedas TOCAR y siéntelas.",
            "Nombra 3 cosas que puedas OÍR en este momento.",
            "Nombra 2 cosas que puedas OLER (reales o imaginadas).",
            "Nombra 1 cosa que puedas SABOREAR.",
            "Respira profundo. Estás aquí y ahora. ✨"
        )
    ),
    Exercise(
        id = "body_scan",
        emoji = "🧘",
        title = "Escaneo corporal",
        subtitle = "Libera la tensión acumulada",
        durationLabel = "8 min",
        category = "Mindfulness",
        steps = listOf(
            "Cierra los ojos. Lleva atención a los pies. ¿Hay tensión?",
            "Sube a las piernas. Nota cualquier sensación sin juzgarla.",
            "Abdomen. Observa si lo tienes contraído. Relájalo.",
            "Pecho. ¿Tu respiración es libre o corta?",
            "Hombros. Este es uno de los lugares donde más cargamos. Suelta.",
            "Cuello y mandíbula. Descansa la lengua en el paladar.",
            "Frente. Suaviza la zona entre las cejas.",
            "Bien hecho. Tómate un momento antes de abrir los ojos. 🙏"
        )
    ),
    Exercise(
        id = "thought_record",
        emoji = "📝",
        title = "Registro de pensamientos",
        subtitle = "Técnica CBT para reestructurar",
        durationLabel = "10 min",
        category = "CBT",
        steps = listOf(
            "¿Qué situación provocó el malestar? Descríbela brevemente.",
            "¿Qué pensamiento automático tuviste? (\"Soy un fracaso\", \"Todo va mal\"...)",
            "¿Qué emoción sentiste y con qué intensidad del 0 al 100?",
            "¿Qué evidencia APOYA ese pensamiento?",
            "¿Qué evidencia lo CONTRADICE o lo matiza?",
            "Formula un pensamiento alternativo más equilibrado.",
            "¿Cómo se siente la emoción ahora? ¿Ha cambiado la intensidad?",
            "¡Excelente trabajo de introspección! 🌟"
        )
    ),
    Exercise(
        id = "progressive_relax",
        emoji = "💆",
        title = "Relajación muscular progresiva",
        subtitle = "Reduce tensión física y mental",
        durationLabel = "12 min",
        category = "Relajación",
        steps = listOf(
            "Túmbate o siéntate cómodamente. Cierra los ojos.",
            "Pies: tensa los dedos 10 seg… ahora suelta. Nota la diferencia.",
            "Pantorrillas: tensa 10 seg… suelta. Siente el calor.",
            "Muslos: tensa 10 seg… suelta completamente.",
            "Abdomen: contrae 10 seg… libera. Respira profundo.",
            "Manos: aprieta los puños 10 seg… abre y suelta.",
            "Hombros: súbelos hacia las orejas 10 seg… déjalos caer.",
            "Cara: arruga todo 10 seg… relaja cada músculo.",
            "Permanece unos minutos disfrutando la relajación. 😌"
        )
    ),
    Exercise(
        id = "self_compassion",
        emoji = "💙",
        title = "Autocompasión",
        subtitle = "Trátate con amabilidad",
        durationLabel = "6 min",
        category = "CBT",
        steps = listOf(
            "Piensa en un momento de dificultad que estés viviendo.",
            "Reconoce: \"Esto duele. Estoy pasando por un momento difícil.\"",
            "Recuerda: el sufrimiento es parte de la experiencia humana. No estás solo/a.",
            "Pon una mano sobre el corazón. Siente su calor.",
            "Dite a ti mismo/a: \"Que sea amable conmigo. Que me dé lo que necesito.\"",
            "Repite la frase 3 veces, con convicción y cariño. 💙"
        )
    ),
    Exercise(
        id = "behavioral_activation",
        emoji = "🚀",
        title = "Activación conductual",
        subtitle = "Sal del ciclo de evitación",
        durationLabel = "5 min",
        category = "CBT",
        steps = listOf(
            "Cuando estamos bajos, tendemos a evitar cosas que antes disfrutábamos.",
            "Elige UNA actividad pequeña que solías disfrutar (caminar 10 min, llamar a alguien).",
            "Comprométete a hacerla hoy, no mañana.",
            "No esperes \"tener ganas\" — la motivación viene después de la acción.",
            "Después de hacerla, observa cómo te sientes.",
            "Anota la actividad y tu estado de ánimo antes/después en el Diario. 📓"
        )
    )
)

// ── UI ────────────────────────────────────────────────────────────────────────

@Composable
fun ExercisesScreen() {
    var activeExercise by remember { mutableStateOf<Exercise?>(null) }
    var activeStep by remember { mutableIntStateOf(0) }

    if (activeExercise != null) {
        ExercisePlayer(
            exercise = activeExercise!!,
            currentStep = activeStep,
            onNext = {
                if (activeStep < activeExercise!!.steps.lastIndex) activeStep++
                else { activeExercise = null; activeStep = 0 }
            },
            onClose = { activeExercise = null; activeStep = 0 }
        )
    } else {
        ExerciseCatalog { exercise ->
            activeExercise = exercise
            activeStep = 0
        }
    }
}

@Composable
private fun ExerciseCatalog(onStart: (Exercise) -> Unit) {
    val categories = EXERCISES.map { it.category }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Ejercicios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Técnicas de CBT, respiración y mindfulness para momentos difíciles.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        categories.forEach { category ->
            Text(category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            EXERCISES.filter { it.category == category }.forEach { exercise ->
                ExerciseCard(exercise = exercise, onClick = { onStart(exercise) })
            }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(exercise.emoji, fontSize = 32.sp)
            Column(Modifier.weight(1f)) {
                Text(exercise.title, style = MaterialTheme.typography.titleMedium)
                Text(exercise.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("⏱️ ${exercise.durationLabel}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.PlayArrow, "Iniciar", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExercisePlayer(
    exercise: Exercise,
    currentStep: Int,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val isLast = currentStep == exercise.steps.lastIndex
    val scope = rememberCoroutineScope()

    // Animación de pulso en el círculo central
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(exercise.emoji, fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(exercise.title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            LinearProgressIndicator(
                progress = { (currentStep + 1f) / exercise.steps.size },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                "Paso ${currentStep + 1} de ${exercise.steps.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Instrucción actual
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.scale(scale),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                exercise.steps[currentStep],
                modifier = Modifier.padding(28.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Botones
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isLast) "Finalizar ejercicio ✓" else "Siguiente →",
                    style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onClose) {
                Text("Cerrar ejercicio")
            }
        }
    }
}
