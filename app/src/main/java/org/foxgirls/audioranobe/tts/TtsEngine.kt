package org.foxgirls.audioranobe.tts

import android.content.Context
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Owns the loaded [SileroEngine]. The nets and the Python front end stay on one background thread; the engine is
 * loaded on the first use (a few seconds) and kept until [shutdown] or the models are deleted.
 */
object TtsEngine {
    const val SAMPLE_RATE = 48000

    private lateinit var appContext: Context
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(null, r, "silero", 16L * 1024 * 1024) }
    private val thread = executor.asCoroutineDispatcher()
    private var engine: SileroEngine? = null

    fun init(context: Context) { appContext = context.applicationContext }

    /** Synthesizes [text]; [onChunk] gets 16-bit PCM pieces as they are made. Cancelling the caller stops it between pieces. */
    suspend fun synth(text: String, speaker: String, pauseSeconds: Double, onChunk: (ShortArray) -> Unit) = withContext(thread) {
        check(TtsModels.installed.value) { "Модели озвучки не скачаны" }
        val e = engine ?: SileroEngine(appContext, TtsModels.dir).also { engine = it }
        val context = currentCoroutineContext()
        e.narrate(text, speaker, pauseSeconds, { !context.isActive }, onChunk)
        context.ensureActive()
    }

    /** Frees the loaded models (they load again on the next use). */
    fun shutdown() {
        val e = engine ?: return
        engine = null
        executor.execute { e.close() }
    }
}
