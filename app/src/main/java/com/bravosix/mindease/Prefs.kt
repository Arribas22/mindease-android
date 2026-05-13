package com.bravosix.mindease

import android.content.Context

/**
 * Persistencia de preferencias y flags de monetización.
 * Modelo idéntico al de CallForwardScheduler (Prefs.kt) adaptado para MindEase.
 */
object Prefs {
    private const val NAME = "mindease_prefs"

    // ── Monetización legacy (mantenido para compatibilidad con RewardedAdManager) ──

    const val REWARDED_HOURS_PER_AD = 2
    const val REWARDED_MAX_HOURS    = 24

    enum class Feature(val keySuffix: String) {
        UNLIMITED_CHAT("unlimited_chat"),
        ADVANCED_INSIGHTS("adv_insights"),
        PREMIUM_EXERCISES("premium_exercises"),
    }

    fun featureUnlockedUntil(ctx: Context, feature: Feature): Long =
        sp(ctx).getLong("unlock_until_${feature.keySuffix}", 0L)

    fun extendFeatureUnlock(ctx: Context, feature: Feature, hours: Int): Long {
        val now = System.currentTimeMillis()
        val current = featureUnlockedUntil(ctx, feature).coerceAtLeast(now)
        val cap = now + REWARDED_MAX_HOURS * 3_600_000L
        val next = (current + hours * 3_600_000L).coerceAtMost(cap)
        sp(ctx).edit().putLong("unlock_until_${feature.keySuffix}", next).apply()
        return next
    }

    fun isFeatureUnlocked(ctx: Context, feature: Feature): Boolean {
        if (isPremium(ctx)) return true
        return System.currentTimeMillis() < featureUnlockedUntil(ctx, feature)
    }

    /** No-op: token system replaces daily message counting. */
    fun incrementMessageCount(ctx: Context) = Unit
    fun remainingFreeMessages(ctx: Context): Int = dailyTokensRemaining(ctx)

    // ── Sistema de tokens de chat ─────────────────────────────────────────────
    //
    // • FREE_DAILY_TOKENS: tokens que se reponen automáticamente cada día (free tier)
    // • Ver un vídeo recompensado: +TOKENS_PER_AD tokens
    // • Plan Vitalicio: tokens ilimitados para siempre (isPremium = true)
    //
    // El saldo total = tokens_base (persistido) + tokens_diarios_aún_disponibles

    const val FREE_DAILY_TOKENS  = 5
    const val TOKENS_PER_AD      = 15
    const val TOKENS_PER_TIP_AD  = 5   // bonus desde el banner de chat

    private const val KEY_TOKEN_BALANCE = "token_balance"
    private const val KEY_TOKEN_DAY     = "token_day"       // día en que se repusieron los diarios

    /** Saldo de tokens extra (comprados o ganados con vídeos). No se repondrán solos. */
    fun tokenBalance(ctx: Context): Int = sp(ctx).getInt(KEY_TOKEN_BALANCE, 0)

    /** Tokens diarios restantes (se reponen a FREE_DAILY_TOKENS cada nuevo día). */
    fun dailyTokensRemaining(ctx: Context): Int {
        val today = todayKey()
        val day   = sp(ctx).getString(KEY_TOKEN_DAY, "")
        val used  = if (day == today) sp(ctx).getInt("token_daily_used", 0) else 0
        return (FREE_DAILY_TOKENS - used).coerceAtLeast(0)
    }

    /** Total de tokens disponibles para el usuario. */
    fun totalTokens(ctx: Context): Int {
        if (isPremium(ctx)) return Int.MAX_VALUE
        return tokenBalance(ctx) + dailyTokensRemaining(ctx)
    }

    /** Consume 1 token. Devuelve false si no hay saldo. */
    fun consumeToken(ctx: Context): Boolean {
        if (isPremium(ctx)) return true
        val today = todayKey()
        val prefs = sp(ctx)
        // Primero usa tokens diarios
        val day  = prefs.getString(KEY_TOKEN_DAY, "")
        val used = if (day == today) prefs.getInt("token_daily_used", 0) else 0
        if (used < FREE_DAILY_TOKENS) {
            prefs.edit()
                .putString(KEY_TOKEN_DAY, today)
                .putInt("token_daily_used", used + 1)
                .apply()
            return true
        }
        // Luego usa tokens del balance
        val bal = prefs.getInt(KEY_TOKEN_BALANCE, 0)
        if (bal <= 0) return false
        prefs.edit().putInt(KEY_TOKEN_BALANCE, bal - 1).apply()
        return true
    }

    /** Añade tokens al saldo (por ver un vídeo, etc.). */
    fun addTokens(ctx: Context, amount: Int) {
        val prefs = sp(ctx)
        val current = prefs.getInt(KEY_TOKEN_BALANCE, 0)
        prefs.edit().putInt(KEY_TOKEN_BALANCE, current + amount).apply()
    }

    /** ¿Puede el usuario enviar un mensaje? */
    fun canSendMessage(ctx: Context): Boolean = totalTokens(ctx) > 0

    // ── Premium permanente (Google Play Billing) ──────────────────────────────

    fun setPremium(ctx: Context, active: Boolean) =
        sp(ctx).edit().putBoolean("is_premium", active).apply()

    fun isPremium(ctx: Context): Boolean =
        sp(ctx).getBoolean("is_premium", false)

    // ── Notificaciones de check-in diario ─────────────────────────────────────

    fun setDailyReminderEnabled(ctx: Context, enabled: Boolean) =
        sp(ctx).edit().putBoolean("daily_reminder", enabled).apply()

    fun isDailyReminderEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean("daily_reminder", true)

    fun setReminderHour(ctx: Context, hour: Int) =
        sp(ctx).edit().putInt("reminder_hour", hour).apply()

    fun getReminderHour(ctx: Context): Int =
        sp(ctx).getInt("reminder_hour", 20) // 20:00 por defecto

    // ── Wellness tip cache (consejos generados por Gemma 3) ──────────────────

    private const val KEY_TIPS       = "wellness_tips"
    private const val KEY_TIP_INDEX  = "wellness_tip_index"
    private const val TIP_SEPARATOR  = "|||"

    fun saveTips(ctx: Context, tips: List<String>) {
        sp(ctx).edit().putString(KEY_TIPS, tips.joinToString(TIP_SEPARATOR)).apply()
    }

    /** Devuelve el siguiente consejo en rotación, o null si no hay cache. */
    fun getNextTip(ctx: Context): String? {
        val raw  = sp(ctx).getString(KEY_TIPS, null) ?: return null
        val tips = raw.split(TIP_SEPARATOR).filter { it.isNotBlank() }
        if (tips.isEmpty()) return null
        val idx  = sp(ctx).getInt(KEY_TIP_INDEX, 0) % tips.size
        sp(ctx).edit().putInt(KEY_TIP_INDEX, idx + 1).apply()
        return tips[idx]
    }

    fun getTipCount(ctx: Context): Int {
        val raw = sp(ctx).getString(KEY_TIPS, null) ?: return 0
        return raw.split(TIP_SEPARATOR).count { it.isNotBlank() }
    }

    // ── Primer arranque ───────────────────────────────────────────────────────

    fun isFirstRun(ctx: Context): Boolean =
        sp(ctx).getBoolean("first_run", true)

    fun markFirstRunDone(ctx: Context) =
        sp(ctx).edit().putBoolean("first_run", false).apply()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}_${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }
}
