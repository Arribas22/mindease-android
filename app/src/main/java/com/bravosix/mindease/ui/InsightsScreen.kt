package com.bravosix.mindease.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bravosix.mindease.InsightManager
import com.bravosix.mindease.db.AppDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun InsightsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.get(ctx) }

    var weekInsight by remember { mutableStateOf<InsightManager.WeekInsight?>(null) }
    var last7Entries by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            weekInsight = InsightManager.getWeekInsight(ctx)
            val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            val entries = db.moodDao().getSince(sevenDaysAgo)
            last7Entries = entries.map { entry ->
                val day = SimpleDateFormat("EEE", Locale("es")).format(Date(entry.timestamp))
                day to entry.level.toFloat()
            }
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Tendencias", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val insight = weekInsight

            // Tarjeta de resumen semanal
            if (insight != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Esta semana", style = MaterialTheme.typography.titleMedium)
                        Text(
                            InsightManager.trendMessage(insight),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoChip("${insight.daysWithEntry} días registrados")
                            if (insight.dominantTags.isNotEmpty()) {
                                InfoChip(insight.dominantTags.first())
                            }
                        }
                        if (insight.daysWithEntry == 0) {
                            Text(
                                "Aún no tienes registros esta semana. ¡Completa tu primer check-in de hoy!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Gráfica de línea (últimos 7 días)
            if (last7Entries.isNotEmpty()) {
                Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Estado de ánimo · últimos 7 días",
                            style = MaterialTheme.typography.titleMedium)
                        MoodLineChart(
                            data = last7Entries,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                    }
                }
            } else {
                EmptyInsightCard(
                    "No hay datos esta semana",
                    "Completa el check-in diario para ver tu evolución aquí."
                )
            }

            // Emociones dominantes
            if (insight != null && insight.dominantTags.isNotEmpty()) {
                Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Emociones más frecuentes",
                            style = MaterialTheme.typography.titleMedium)
                        insight.dominantTags.forEachIndexed { i, tag ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("${i + 1}.", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(tag, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            // Consejo basado en tendencia
            if (insight != null) {
                val tip = when (insight.trend) {
                    InsightManager.Trend.DECLINING ->
                        "💡 Tu estado de ánimo ha bajado esta semana. Considera hacer alguno de los ejercicios de CBT o dedicar tiempo a una actividad que te guste."
                    InsightManager.Trend.IMPROVING ->
                        "🌱 Tu ánimo está mejorando esta semana. Sigue manteniendo los hábitos que te están ayudando."
                    InsightManager.Trend.STABLE ->
                        "⚖️ Tu ánimo ha estado estable esta semana. La consistencia es una fortaleza."
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(tip, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MoodLineChart(data: List<Pair<String, Float>>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor  = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padV = 20f
        val padH = 24f
        val plotW = w - padH * 2
        val plotH = h - padV * 2

        if (data.isEmpty()) return@Canvas

        val minVal = 1f; val maxVal = 10f
        val stepX = if (data.size > 1) plotW / (data.size - 1) else plotW

        // Líneas de cuadrícula horizontales
        for (i in 0..4) {
            val y = padV + plotH * (1f - i / 4f)
            drawLine(gridColor, Offset(padH, y), Offset(w - padH, y), strokeWidth = 1f)
        }

        // Línea de datos
        val points = data.mapIndexed { i, (_, v) ->
            Offset(padH + i * stepX, padV + plotH * (1f - (v - minVal) / (maxVal - minVal)))
        }

        val path = Path().apply {
            points.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))

        // Puntos
        points.forEach { pt -> drawCircle(dotColor, radius = 6f, center = pt) }
    }
}

@Composable
private fun InfoChip(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    ) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyInsightCard(title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("📊", style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
