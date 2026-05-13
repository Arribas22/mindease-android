package com.bravosix.mindease

import android.content.Context
import com.bravosix.mindease.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Analiza los patrones de estado de ánimo almacenados en Room y genera
 * insights textuales que se muestran en InsightsScreen.
 * Todo el cómputo es local; no se envía ningún dato a servidores.
 */
object InsightManager {

    data class WeekInsight(
        val averageMood: Float,
        val trend: Trend,
        val dominantTags: List<String>,
        val daysWithEntry: Int
    )

    enum class Trend { IMPROVING, STABLE, DECLINING }

    suspend fun getWeekInsight(ctx: Context): WeekInsight = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(ctx)
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val fourteenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)

        val recent = db.moodDao().getSince(sevenDaysAgo)
        val previous = db.moodDao().getSince(fourteenDaysAgo)
            .filter { it.timestamp < sevenDaysAgo }

        val avgRecent = if (recent.isEmpty()) 5f else recent.map { it.level }.average().toFloat()
        val avgPrev   = if (previous.isEmpty()) avgRecent else previous.map { it.level }.average().toFloat()

        val trend = when {
            avgRecent > avgPrev + 0.5f -> Trend.IMPROVING
            avgRecent < avgPrev - 0.5f -> Trend.DECLINING
            else                       -> Trend.STABLE
        }

        // Conteo de tags para identificar emociones dominantes
        val tagCount = mutableMapOf<String, Int>()
        recent.forEach { entry ->
            parseTags(entry.tags).forEach { tag ->
                tagCount[tag] = (tagCount[tag] ?: 0) + 1
            }
        }
        val dominantTags = tagCount.entries.sortedByDescending { it.value }
            .take(3).map { it.key }

        WeekInsight(avgRecent, trend, dominantTags, recent.size)
    }

    fun trendMessage(insight: WeekInsight): String {
        val trendText = when (insight.trend) {
            Trend.IMPROVING -> "mejorando ↑"
            Trend.DECLINING -> "bajando ↓"
            Trend.STABLE    -> "estable →"
        }
        val moodEmoji = when {
            insight.averageMood >= 7 -> "😊"
            insight.averageMood >= 5 -> "😐"
            else                     -> "😔"
        }
        return "$moodEmoji Tu media esta semana es ${String.format("%.1f", insight.averageMood)}/10 — $trendText"
    }

    private fun parseTags(json: String): List<String> = try {
        val arr = org.json.JSONArray(json)
        List(arr.length()) { arr.getString(it) }
    } catch (_: Exception) { emptyList() }
}
