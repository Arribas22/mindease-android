package com.bravosix.mindease

import android.content.Context

/**
 * Detector de señales de crisis en texto libre.
 *
 * Analiza el texto del usuario en busca de palabras clave de riesgo.
 * NO es un sistema médico; actúa como red de seguridad básica para
 * mostrar recursos de ayuda cuando se detectan señales preocupantes.
 *
 * Teléfono de la Esperanza (España): 717 003 717
 * Teléfono de Atención a la Conducta Suicida: 024
 */
object CrisisDetector {

    /** Nivel de riesgo detectado. */
    enum class RiskLevel { NONE, MODERATE, HIGH }

    data class Result(
        val level: RiskLevel,
        val resourcePhone: String = "024",
        val resourceName: String = "Línea de Atención a la Conducta Suicida"
    )

    private val HIGH_RISK_KEYWORDS = setOf(
        "suicidio", "suicidarme", "suicidar", "quitarme la vida", "quitarme la vida",
        "no quiero vivir", "no quiero seguir viviendo", "mejor muerto", "mejor muerta",
        "quiero morir", "quiero morirme", "hacerme daño", "hacerme daño",
        "cortarme", "pastillas para morir", "tirarme", "colgarme",
        "no puedo más", "acabar con todo"
    )

    private val MODERATE_RISK_KEYWORDS = setOf(
        "desesperado", "desesperada", "sin esperanza", "inútil", "fracasado",
        "fracasada", "nadie me quiere", "estoy solo", "estoy sola",
        "todo va mal", "ya no aguanto", "agotado", "agotada",
        "ansioso", "ansiosa", "angustia", "pánico", "ataque de pánico"
    )

    /** Analiza el texto y devuelve el nivel de riesgo. */
    fun analyze(text: String): Result {
        val lower = text.lowercase()
        if (HIGH_RISK_KEYWORDS.any { lower.contains(it) }) {
            return Result(RiskLevel.HIGH)
        }
        if (MODERATE_RISK_KEYWORDS.any { lower.contains(it) }) {
            return Result(RiskLevel.MODERATE, "717 003 717", "Teléfono de la Esperanza")
        }
        return Result(RiskLevel.NONE)
    }

    /**
     * Genera un mensaje de apoyo empático y recursos según el nivel de riesgo.
     * Este mensaje se antepone / sustituye la respuesta de la IA en escenarios de crisis.
     */
    fun crisisMessage(level: RiskLevel): String = when (level) {
        RiskLevel.HIGH ->
            "Noto que estás pasando por un momento muy difícil y me importa tu bienestar. " +
            "Por favor, llama ahora al **024** (Línea de Atención a la Conducta Suicida). " +
            "Son gratuitos, confidenciales y están disponibles las 24 horas. " +
            "También puedes llamar al **112** si estás en peligro inmediato. " +
            "No estás solo/a."
        RiskLevel.MODERATE ->
            "Parece que estás teniendo un momento muy duro. " +
            "Recuerda que el **Teléfono de la Esperanza (717 003 717)** está disponible si necesitas hablar con alguien. " +
            "Cuéntame qué está pasando, estoy aquí para escucharte."
        RiskLevel.NONE -> ""
    }
}
