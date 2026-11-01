package org.foxgirls.audioranobe.tts

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import org.foxgirls.audioranobe.core.Api
import java.io.File

@Serializable
private data class TtsManifest(val version: String, val total: Long = 0, val files: List<TtsFile> = emptyList())

@Serializable
private data class TtsFile(val name: String, val size: Long = 0)

/**
 * The on-device narrator's files: the Silero model set (from the API) and the web runtime that executes it
 * (onnxruntime + Pyodide, from the site). Downloaded once, on the first read-aloud, then used without a network.
 */
object TtsModels {
    private lateinit var root: File
    private val _installed = MutableStateFlow(false)
    /** The model set is complete on disk. */
    val installed: StateFlow<Boolean> = _installed.asStateFlow()
    private val _progress = MutableStateFlow<Float?>(null)
    /** 0..1 while downloading, null otherwise. */
    val progress: StateFlow<Float?> = _progress.asStateFlow()
    private val _bytes = MutableStateFlow(0L)
    val bytes: StateFlow<Long> = _bytes.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val runtime = listOf(
        "ort/ort.wasm.min.js", "ort/ort-wasm-simd-threaded.wasm", "ort/ort-wasm-simd-threaded.mjs",
        "pyodide/pyodide.js", "pyodide/pyodide.asm.js", "pyodide/pyodide.asm.wasm", "pyodide/python_stdlib.zip", "pyodide/pyodide-lock.json",
    )

    fun init(context: Context) {
        root = File(context.applicationContext.filesDir, "tts")
        refresh()
    }

    /** Where the engine page reads its files from (see [TtsWebHost]). */
    fun file(path: String): File = File(root, path)

    private fun refresh() {
        _installed.value = File(root, DONE).exists()
        _bytes.value = if (_installed.value) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    /** Starts the download in the background (a no-op while one runs); watch [installed], [progress] and [error]. */
    fun download() {
        if (job?.isActive == true || _installed.value) return
        _error.value = null
        job = scope.launch {
            try { install() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { _error.value = e.message ?: "Не удалось скачать модели" }
        }
    }

    fun cancelDownload() { job?.cancel() }

    /** Downloads everything. Throws on failure or cancellation, leaving no half-installed set behind. */
    private suspend fun install() = withContext(Dispatchers.IO) {
        val part = File(root.parentFile, "tts.part").apply { deleteRecursively(); mkdirs() }
        try {
            _progress.value = 0f
            val manifest = Api.get<TtsManifest>("/tts/manifest")
            val site = Api.siteUrl
            val jobs = runtime.map { "$site/tts/vendor/$it" to File(part, "vendor/$it") } +
                manifest.files.map { "${Api.baseUrl}/tts/model/${it.name}?v=${manifest.version}" to File(part, "models/${it.name}") }
            val total = (manifest.total + RUNTIME_ESTIMATE).toFloat()
            var done = 0L
            for ((url, dst) in jobs) {
                dst.parentFile?.mkdirs()
                Api.client.newCall(Request.Builder().url(url).header("User-Agent", "AudioRanobe-Android").build()).execute().use { r ->
                    if (!r.isSuccessful) error("${dst.name}: ${r.code}")
                    r.body!!.byteStream().use { input ->
                        dst.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                _progress.value = (done / total).coerceIn(0f, 0.99f)
                            }
                        }
                    }
                }
            }
            File(part, DONE).writeText(manifest.version)
            root.deleteRecursively()
            if (!part.renameTo(root)) error("Не удалось сохранить модели")
        } catch (e: Throwable) {
            part.deleteRecursively()
            throw e
        } finally {
            _progress.value = null
            refresh()
        }
    }

    /** Deletes the model set and the runtime. */
    fun remove() {
        root.deleteRecursively()
        refresh()
    }

    private const val DONE = ".done"
    /** The runtime is ~25 MB; only used to scale the progress bar. */
    private const val RUNTIME_ESTIMATE = 25_000_000L
}
