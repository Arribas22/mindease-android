package com.bravosix.mindease

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestiona el ciclo de vida del LLM local (Gemma 3 1B via MediaPipe LiteRT-LM).
 *
 * Tier 1 (todos los dispositivos): respuestas rule-based de CBT / fallback
 * Tier 2 (Android 8+, GPU, ≥4 GB RAM, modelo descargado): Gemma 3 1B on-device
 *
 * El modelo (~600 MB) se descarga mediante [ModelDownloadManager].
 * Esta clase solo lo carga si ya existe en el directorio de archivos de la app.
 */
class LlmManager private constructor(private val ctx: Context) {

    enum class State { IDLE, LOADING, READY, UNSUPPORTED, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private var inference: LlmInference? = null

    /**
     * Serializa el acceso al LLM: MediaPipe solo admite UNA LlmInferenceSession
     * activa a la vez por instancia de LlmInference.
     */
    private val llmMutex = Mutex()

    /** System prompt en español para acompañamiento emocional. */
    private val systemPrompt = """
        Eres MindEase, un asistente de acompañamiento emocional empático y cálido.
        Ayudas a las personas a gestionar el estrés, la ansiedad y las emociones difíciles
        usando técnicas de Terapia Cognitivo-Conductual (CBT) y mindfulness.
        
        REGLAS IMPORTANTES:
        - NO eres terapeuta ni médico. Nunca diagnostiques ni prescribas.
        - Si detectas señales de crisis o riesgo, recuerda SIEMPRE mencionar el 024 (España).
        - Responde siempre en español, con calidez y brevedad (máximo 4 párrafos cortos).
        - No inventes información médica. Si no sabes algo, dilo con honestidad.
        - Valida los sentimientos del usuario antes de ofrecer técnicas o consejos.
    """.trimIndent()

    /** Intenta cargar el modelo. Si está en assets lo copia primero; si ya está en disco lo carga directamente. */
    fun init() {
        scope.launch {
            if (!isDeviceCapable()) {
                _state.value = State.UNSUPPORTED
                return@launch
            }
            val modelFile = modelFile(ctx)
            when {
                modelFile.exists() -> load(modelFile)
                isModelInAssets()  -> { _state.value = State.LOADING; copyFromAssets(modelFile) }
                else               -> _state.value = State.IDLE  // esperando descarga
            }
        }
    }

    /** Copia el modelo desde el APK/assets al almacenamiento externo (solo ocurre una vez). */
    private suspend fun copyFromAssets(dest: File) {
        runCatching {
            withContext(Dispatchers.IO) {
                dest.parentFile?.mkdirs()
                val tmp = File(dest.parent, "${dest.name}.tmp")
                ctx.assets.open(MODEL_FILENAME).use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(65_536)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) output.write(buf, 0, n)
                    }
                }
                tmp.renameTo(dest)
            }
        }.onSuccess {
            Log.i(TAG, "Modelo extraído de assets a ${dest.absolutePath}")
            load(dest)
        }.onFailure { e ->
            Log.e(TAG, "Error extrayendo modelo de assets: ${e.message}")
            _state.value = State.ERROR
        }
    }

    private fun isModelInAssets(): Boolean = runCatching {
        ctx.assets.list("")?.contains(MODEL_FILENAME) == true
    }.getOrDefault(false)

    private suspend fun load(modelFile: File) {
        _state.value = State.LOADING
        runCatching {
            withContext(Dispatchers.IO) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(1024)
                    .setMaxTopK(40)
                    .build()
                LlmInference.createFromOptions(ctx, options)
            }
        }.onSuccess {
            inference = it
            _state.value = State.READY
            Log.i(TAG, "Gemma 3 1B cargado correctamente")
            // NOTE: prefillWellnessTips() is intentionally NOT called here.
            // Auto-prefilling creates a background LlmInferenceSession that races
            // with user chat sessions → MediaPipe async-close race → session
            // creation fails on the first 1-2 chat messages.
            // Notifications use hardcoded fallback tips instead.
        }.onFailure { e ->
            Log.e(TAG, "Error cargando modelo: ${e.message}")
            _state.value = State.ERROR
        }
    }

    /**
     * Genera una respuesta de forma asíncrona con streaming.
     * [onToken] se invoca en el hilo principal con cada token parcial.
     * [onDone] se invoca cuando la respuesta está completa.
     *
     * Si el modelo no está listo, devuelve una respuesta de fallback.
     */
    fun generateResponse(
        conversationHistory: List<Pair<String, String>>, // (role, content)
        userMessage: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Seguridad: detector de crisis antes de llegar al LLM
        val crisis = CrisisDetector.analyze(userMessage)
        if (crisis.level == CrisisDetector.RiskLevel.HIGH) {
            val msg = CrisisDetector.crisisMessage(crisis.level)
            onToken(msg); onDone(msg); return
        }
        val llm = inference
        if (llm == null || _state.value != State.READY) {
            val fallback = fallbackResponse(userMessage)
            onToken(fallback); onDone(fallback); return
        }

        scope.launch(Dispatchers.IO) {
            llmMutex.withLock {
                runCatching {
                    doGenerate(
                        llm     = llm,
                        history = conversationHistory,
                        userMsg = userMessage,
                        onToken = onToken,
                        onDone  = { full -> scope.launch(Dispatchers.Main) { onDone(full) } }
                    )
                }.onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e  // never swallow
                    Log.e(TAG, "LLM error: ${e.javaClass.simpleName}: ${e.message}", e)
                    withContext(Dispatchers.Main) { onError(e.message ?: "Error desconocido") }
                }
            }
        }
    }

    /**
     * Función interna que crea una sesión LLM, construye el prompt, genera la
     * respuesta con streaming y cierra la sesión. REQUIERE que el llamador ya
     * tenga adquirido [llmMutex] — nunca llamar directamente desde fuera.
     */
    private suspend fun doGenerate(
        llm: LlmInference,
        history: List<Pair<String, String>>,
        userMsg: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit
    ) {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.7f)
            .setRandomSeed(42)
            .build()
        val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
        try {
            session.addQueryChunk("<start_of_turn>system\n$systemPrompt<end_of_turn>\n")
            history.forEach { (role, content) ->
                val tag = if (role == "user") "user" else "model"
                session.addQueryChunk("<start_of_turn>$tag\n$content<end_of_turn>\n")
            }
            session.addQueryChunk("<start_of_turn>user\n$userMsg<end_of_turn>\n<start_of_turn>model\n")

            // Usamos CompletableDeferred en lugar de future.get() para no
            // bloquear el hilo IO y permitir cancelación correcta de corrutinas.
            val deferred = CompletableDeferred<String>()
            val sb = StringBuilder()
            session.generateResponseAsync { partial: String, done: Boolean ->
                sb.append(partial)
                scope.launch(Dispatchers.Main) { onToken(partial) }
                if (done) deferred.complete(sb.toString())
            }
            val full = deferred.await()
            onDone(full)
        } finally {
            // Siempre cerramos la sesión, incluso en caso de excepción o cancelación.
            runCatching { session.close() }
        }
    }

    /**
     * Genera un texto corto sin historial de conversación. Usado para pre-generar tips.
     * Suspende hasta que la respuesta esté completa.
     */
    /**
     * Genera texto una sola vez sin historial. Puede usarse externamente
     * (p.ej. para pre-generar tips manualmente desde un Worker).
     * Debe llamarse con [llmMutex] ya adquirido.
     */
    private suspend fun generateTextOnce(prompt: String): String? {
        val llm = inference ?: return null
        val deferred = CompletableDeferred<String?>()
        doGenerate(
            llm     = llm,
            history = emptyList(),
            userMsg = prompt,
            onToken = {},
            onDone  = { text -> deferred.complete(text.trim().take(280)) }
        )
        return deferred.await()
    }

    /**
     * Pre-genera 7 consejos de bienestar con Gemma 3 y los almacena en caché.
     * Se ejecuta en background una sola vez tras cargar el modelo.
     */
    /**
     * Pre-genera consejos de bienestar con Gemma 3 y los almacena en caché.
     * NO se llama automáticamente al cargar el modelo para evitar race conditions
     * con las sesiones de chat del usuario.
     * Llamar manualmente desde un Workers de background cuando el usuario no esté activo.
     */
    fun prefillWellnessTips() {
        if (Prefs.getTipCount(ctx) >= WELLNESS_PROMPTS.size) return
        scope.launch(Dispatchers.IO) {
            val tips = mutableListOf<String>()
            for (prompt in WELLNESS_PROMPTS) {
                llmMutex.withLock {
                    val tip = runCatching { generateTextOnce(prompt) }.getOrNull()
                    if (!tip.isNullOrBlank()) tips.add(tip)
                }
            }
            if (tips.isNotEmpty()) {
                Prefs.saveTips(ctx, tips)
                Log.i(TAG, "Pre-generados ${tips.size} consejos de bienestar")
            }
        }
    }

    /** Llamado cuando el modelo acaba de descargarse. Recarga automáticamente. */
    fun onModelDownloaded() {
        scope.launch { load(modelFile(ctx)) }
    }

    fun release() {
        inference?.close()
        inference = null
        _state.value = State.IDLE
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Respuesta de fallback cuando el LLM no está disponible.
     * Basada en detección simple de palabras clave + respuestas CBT pre-definidas.
     */
    private fun fallbackResponse(text: String): String {
        val lower = text.lowercase()
        return when {
            CrisisDetector.analyze(text).level == CrisisDetector.RiskLevel.MODERATE ->
                CrisisDetector.crisisMessage(CrisisDetector.RiskLevel.MODERATE)
            lower.contains("ansios") || lower.contains("ansiedad") ->
                "Entiendo que estás sintiendo ansiedad. Prueba el ejercicio 4-7-8: " +
                "inhala 4 segundos, mantén 7 y exhala 8. Repítelo 3 veces. " +
                "¿Quieres que exploremos juntos qué lo está desencadenando?"
            lower.contains("triste") || lower.contains("tristeza") ->
                "La tristeza es una emoción válida y tiene su lugar. " +
                "¿Puedes identificar qué ha pasado hoy que haya contribuido a cómo te sientes? " +
                "A veces ponerlo en palabras ayuda mucho."
            lower.contains("estrés") || lower.contains("estresado") || lower.contains("estresada") ->
                "El estrés puede ser agotador. Una técnica rápida: observa 5 cosas que puedas ver, " +
                "4 que puedas tocar, 3 que puedas oír. Esto ancla la mente al presente."
            lower.contains("no puedo dormir") || lower.contains("insomnio") ->
                "El insomnio es muy incómodo. ¿Has probado a escribir todo lo que te preocupa " +
                "antes de acostarte? Externalizar los pensamientos libera la mente."
            else ->
                "Estoy aquí para escucharte. Ahora mismo funciono en modo básico. " +
                "Cuéntame cómo te sientes y haré lo posible por acompañarte. " +
                "Puedes activar la IA avanzada descargando el modelo desde el banner en el chat."
        }
    }

    /** Verifica si el dispositivo tiene suficiente RAM y GPU para Gemma 3 1B. */
    private fun isDeviceCapable(): Boolean {
        // Requiere Android 8+ (API 26, nuestro minSdk) y suficiente memoria libre
        val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024f * 1024 * 1024)
        return totalRamGb >= 3.5f  // mínimo ~4 GB RAM total
    }

    companion object {
        private const val TAG = "LlmManager"
        const val MODEL_FILENAME = "gemma3-1b-it-int4.task"

        private val WELLNESS_PROMPTS = listOf(
            "Escribe un consejo muy breve (2 frases en español) para manejar la ansiedad diaria. Solo el consejo, sin introducción.",
            "Escribe un recordatorio muy breve (2 frases en español) para practicar respiración consciente hoy. Solo el recordatorio.",
            "Escribe un consejo muy breve (2 frases en español) de bienestar emocional para empezar el día. Solo el consejo.",
            "Sugiere un ejercicio muy breve (2 frases en español) de mindfulness para hacer en 2 minutos. Solo el ejercicio.",
            "Escribe una frase motivadora muy breve (2 frases en español) sobre salud mental. Solo la frase.",
            "Escribe un consejo muy breve (2 frases en español) para dormir mejor esta noche. Solo el consejo.",
            "Escribe un recordatorio muy breve (2 frases en español) para hacer una pausa y estirar el cuerpo. Solo el recordatorio."
        )

        @Volatile private var INSTANCE: LlmManager? = null

        fun get(ctx: Context): LlmManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LlmManager(ctx.applicationContext).also { INSTANCE = it }
            }

        fun modelFile(ctx: Context): File =
            File(ctx.getExternalFilesDir(null), MODEL_FILENAME)

        fun isModelDownloaded(ctx: Context): Boolean = modelFile(ctx).exists()

        fun isModelInAssets(ctx: Context): Boolean = runCatching {
            ctx.assets.list("")?.contains(MODEL_FILENAME) == true
        }.getOrDefault(false)
    }
}
