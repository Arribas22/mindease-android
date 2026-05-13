package com.bravosix.mindease

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.CompletableDeferred
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that fires at specific times of day with a contextual wellness tip.
 *
 * Slots: 9:00 (morning), 13:30 (lunch), 18:00 (after work), 22:00 (bedtime)
 * Each slot has its own unique WorkManager name and contextual fallback tips.
 *
 * Tip priority:
 *  1. AI-generated tips pre-cached in Prefs (Gemma 3 generated)
 *  2. Live Gemma 3 generation if model is in memory
 *  3. Curated fallback tips for the slot's context
 */
class WellnessNotificationWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val slot = inputData.getInt(KEY_SLOT, SLOT_MORNING)
        val tip  = generateTip(slot)
        sendNotification(tip, slot)
        return Result.success()
    }

    private suspend fun generateTip(slot: Int): String {
        // 1. Cached AI tips (cycled in rotation, slot-agnostic)
        val cached = Prefs.getNextTip(applicationContext)
        if (cached != null) return cached

        // 2. Live Gemma 3 generation if model is ready
        val llm = LlmManager.get(applicationContext)
        if (llm.state.value == LlmManager.State.READY) {
            val prompt = LIVE_PROMPTS[slot % LIVE_PROMPTS.size]
            val result = CompletableDeferred<String?>()
            llm.generateResponse(
                conversationHistory = emptyList(),
                userMessage = prompt,
                onToken = {},
                onDone  = { text -> result.complete(text.trim().take(280)) },
                onError = { result.complete(null) }
            )
            val generated = result.await()
            if (!generated.isNullOrBlank()) return generated
        }

        // 3. Contextual fallback for this slot
        return SLOT_TIPS[slot]?.random() ?: SLOT_TIPS[SLOT_MORNING]!!.random()
    }

    private fun sendNotification(message: String, slot: Int) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, slot, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(SLOT_TITLES[slot] ?: "MindEase · Bienestar 🧘")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_BASE + slot, notification)
    }

    companion object {
        const val CHANNEL_ID  = "mindease_wellness"
        private const val NOTIF_ID_BASE = 2000
        private const val KEY_SLOT = "slot"

        const val SLOT_MORNING   = 0  // 09:00
        const val SLOT_LUNCH     = 1  // 13:30
        const val SLOT_AFTERWORK = 2  // 18:00
        const val SLOT_BEDTIME   = 3  // 22:00

        private val SLOT_HOURS   = mapOf(SLOT_MORNING to 9,  SLOT_LUNCH to 13, SLOT_AFTERWORK to 18, SLOT_BEDTIME to 22)
        private val SLOT_MINUTES = mapOf(SLOT_MORNING to 0,  SLOT_LUNCH to 30, SLOT_AFTERWORK to 0,  SLOT_BEDTIME to 0)

        private val SLOT_TITLES = mapOf(
            SLOT_MORNING   to "MindEase · Buenos días ☀️",
            SLOT_LUNCH     to "MindEase · Pausa de mediodía 🥗",
            SLOT_AFTERWORK to "MindEase · Final del día 💼",
            SLOT_BEDTIME   to "MindEase · Hora de descansar 🌙"
        )

        private val LIVE_PROMPTS = listOf(
            "Escribe un consejo muy breve (2 frases en español) de bienestar para empezar la mañana con energía. Solo el consejo.",
            "Escribe un recordatorio muy breve (2 frases en español) para hacer una pausa consciente a mediodía. Solo el recordatorio.",
            "Escribe un consejo muy breve (2 frases en español) para desconectar del trabajo y relajarse al final del día. Solo el consejo.",
            "Escribe un consejo muy breve (2 frases en español) para preparar la mente antes de dormir y descansar mejor. Solo el consejo."
        )

        private val SLOT_TIPS = mapOf(
            SLOT_MORNING to listOf(
                "Buenos días 🌅 Antes de abrir el móvil, tómate 1 minuto para respirar profundo. El día empieza en calma.",
                "Hidrata tu cuerpo y tu mente: empieza el día con un vaso de agua y una intención positiva. ☀️",
                "La mañana es tuya. Escribe una cosa que quieras sentir hoy y guíala conscientemente. 📝"
            ),
            SLOT_LUNCH to listOf(
                "Pausa activa: estira brazos y cuello 30 segundos antes de comer. Tu cuerpo te lo agradecerá. 🧘",
                "Come sin pantallas 5 minutos. Saborear la comida reduce el estrés y mejora la digestión. 🥗",
                "A mitad del día: ¿cómo te sientes? Anota una emoción en MindEase para seguir tu bienestar. 💬"
            ),
            SLOT_AFTERWORK to listOf(
                "Fin de jornada 💼 Haz la transición: cambia de ropa o sal a caminar 10 min. Separa trabajo y vida.",
                "El estrés acumulado se libera con movimiento. Una caminata corta ahora vale más que 1 hora en el sofá.",
                "Escribe 3 cosas que salieron bien hoy, aunque sean pequeñas. La gratitud diaria cambia el cerebro. 🌟"
            ),
            SLOT_BEDTIME to listOf(
                "Hora de soltar el día 🌙 Escribe lo que te preocupa en papel y ciérralo. La mente descansa mejor vacía.",
                "Atenúa las luces y el móvil 30 min antes de dormir. Tu sueño profundo empieza ahora. 💤",
                "Respiración 4-7-8: inhala 4s, aguanta 7s, exhala 8s. Activa el modo calma de tu sistema nervioso. 🌿"
            )
        )

        /** Cancela y reencola los 4 workers con el delay exacto hasta la próxima ocurrencia. */
        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            for (slot in listOf(SLOT_MORNING, SLOT_LUNCH, SLOT_AFTERWORK, SLOT_BEDTIME)) {
                val delayMs = millisUntilSlot(slot)
                val data    = Data.Builder().putInt(KEY_SLOT, slot).build()
                val request = PeriodicWorkRequestBuilder<WellnessNotificationWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
                wm.enqueueUniquePeriodicWork(
                    "wellness_slot_$slot",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }

        /** Milliseconds until the next occurrence of a given hour:minute. */
        private fun millisUntilSlot(slot: Int): Long {
            val hour   = SLOT_HOURS[slot]   ?: 9
            val minute = SLOT_MINUTES[slot] ?: 0
            val now    = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis - now.timeInMillis
        }
    }
}

