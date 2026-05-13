package com.bravosix.mindease

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gestiona la descarga del modelo Gemma 3 1B (~600 MB) en background.
 *
 * La URL apunta al modelo en formato .task (LiteRT / MediaPipe compatible)
 * publicado en Hugging Face. La descarga solo ocurre una vez; tras completarse
 * el archivo persiste en el directorio externo de la app (no se incluye en backup).
 *
 * Progreso expuesto como StateFlow para actualizar la UI en tiempo real.
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownload"
    private const val WORK_NAME = "gemma_model_download"

    /**
     * URL del modelo Gemma 3 1B cuantizado (int4) en formato MediaPipe .task
     * Fuente: https://huggingface.co/litert-community/Gemma3-1B-IT
     * NOTA: actualizar esta URL cuando salga una versión más reciente.
     */
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progressPct: Int) : DownloadState()
        object Done : DownloadState()
        data class Failed(val reason: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    /** Inicia la descarga mediante WorkManager (sobrevive a muerte del proceso). */
    fun startDownload(ctx: Context) {
        if (LlmManager.isModelDownloaded(ctx)) {
            _downloadState.value = DownloadState.Done
            return
        }
        _downloadState.value = DownloadState.Downloading(0)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.KEEP, request
        )
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        _downloadState.value = DownloadState.Idle
    }

    // ── WorkManager Worker ────────────────────────────────────────────────────

    class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val dest = LlmManager.modelFile(applicationContext)
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parent, "${dest.name}.tmp")

            runCatching {
                // Abrir conexión siguiendo redirecciones manualmente (HuggingFace → CDN)
                val connection = openConnectionFollowingRedirects(MODEL_URL)
                val total = connection.contentLengthLong
                var downloaded = 0L

                connection.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(16_384)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (total > 0) {
                                val pct = (downloaded * 100 / total).toInt()
                                _downloadState.value = DownloadState.Downloading(pct)
                            }
                        }
                    }
                }
                tmp.renameTo(dest)
                _downloadState.value = DownloadState.Done
                LlmManager.get(applicationContext).onModelDownloaded()
                Log.i(TAG, "Modelo descargado en ${dest.absolutePath}")
            }.onFailure { e ->
                tmp.delete()
                val msg = when {
                    e.message?.contains("Unable to resolve host") == true ->
                        "Sin conexión a internet. Comprueba tu red y vuelve a intentarlo."
                    e.message?.contains("timeout") == true ->
                        "Tiempo de espera agotado. Comprueba tu conexión e inténtalo de nuevo."
                    else -> e.message ?: "Error desconocido"
                }
                Log.e(TAG, "Descarga fallida: ${e.message}")
                _downloadState.value = DownloadState.Failed(msg)
                return@withContext Result.retry()
            }
            Result.success()
        }

        /** Abre la URL siguiendo hasta 5 redirecciones HTTP (necesario para HuggingFace → CDN). */
        private fun openConnectionFollowingRedirects(urlStr: String, redirects: Int = 0): HttpURLConnection {
            if (redirects > 5) error("Demasiadas redirecciones")
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout    = 120_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MindEase-Android/1.0")
            }
            conn.connect()
            val code = conn.responseCode
            return if (code in 301..308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                openConnectionFollowingRedirects(location, redirects + 1)
            } else {
                conn
            }
        }
    }
}
