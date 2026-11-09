package org.foxgirls.audioranobe.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Android's own speech synthesizer: nothing to download, works offline when a Russian voice is installed.
 * Sentences are queued a few at a time; pausing stops the synthesizer and resumes from the current sentence.
 */
class SystemSpeaker(
    context: Context,
    sentences: List<String>,
    skipped: Int,
    private val voiceName: String?,
    private val pauseSeconds: Double,
    listener: Listener,
) : SentenceSpeaker(sentences, skipped, listener) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var released = false
    private var generation = 0
    private var next = 0

    override fun start(from: Int, play: Boolean) {
        generation++
        tts?.stop()
        if (sentences.isEmpty()) { finished = true; listener.onEnd(); return }
        index = from.coerceIn(0, sentences.size - 1)
        finished = false
        playing = play
        busy = play
        listener.onChange()
        if (play) speakFromIndex()
    }

    override fun pause() {
        if (!playing) return
        playing = false
        generation++
        tts?.stop()
        listener.onChange()
    }

    override fun resume() {
        if (playing || finished) return
        playing = true
        busy = true
        generation++
        listener.onChange()
        speakFromIndex()
    }

    override fun setRate(r: Float) {
        super.setRate(r)
        tts?.setSpeechRate(r)
        if (playing) { generation++; tts?.stop(); speakFromIndex() }
    }

    override fun release() {
        released = true
        generation++
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    /** Queues from the current sentence once the synthesizer is up, unless something newer happened meanwhile. */
    private fun speakFromIndex() {
        val g = generation
        whenReady { if (g == generation && playing) queueFrom(index) }
    }

    private fun whenReady(run: () -> Unit) {
        if (ready) return run()
        if (released) return
        if (tts != null) { main.postDelayed({ whenReady(run) }, 100); return }
        tts = TextToSpeech(app) { status ->
            main.post {
                val t = tts ?: return@post
                if (status != TextToSpeech.SUCCESS) return@post initFailed(t, "Системный синтезатор речи недоступен")
                if (t.setLanguage(Locale("ru", "RU")) < TextToSpeech.LANG_AVAILABLE) return@post initFailed(t, "На устройстве нет русского голоса. Установите его: Настройки Android → Синтез речи.")
                voiceName?.let { n -> t.voices?.firstOrNull { it.name == n }?.let { t.voice = it } }
                t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                t.setSpeechRate(rate)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) { main.post { spoken(utteranceId, started = true) } }
                    override fun onDone(utteranceId: String) { main.post { spoken(utteranceId, started = false) } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { main.post { fail("Синтезатор речи вернул ошибку") } }
                })
                ready = true
                run()
            }
        }
    }

    private fun initFailed(t: TextToSpeech, message: String) {
        t.shutdown()
        tts = null
        fail(message)
    }

    private fun queueFrom(from: Int) {
        next = from
        refill()
    }

    /** Keeps a few sentences queued ahead of the one being spoken. */
    private fun refill() {
        val t = tts ?: return
        while (next < sentences.size && next < index + LOOKAHEAD) {
            t.speak(sentences[next], TextToSpeech.QUEUE_ADD, null, "$generation:$next")
            if (pauseSeconds > 0 && next < sentences.size - 1) t.playSilentUtterance((pauseSeconds * 1000).toLong(), TextToSpeech.QUEUE_ADD, "$generation:p$next")
            next++
        }
    }

    private fun spoken(id: String, started: Boolean) {
        val (gen, tag) = id.split(':').let { it[0].toIntOrNull() to it.getOrElse(1) { "" } }
        if (gen != generation || tag.startsWith("p")) return
        val i = tag.toIntOrNull() ?: return
        if (started) {
            index = i
            busy = false
            listener.onChange()
            refill()
        } else if (i == sentences.size - 1) {
            finished = true
            playing = false
            listener.onChange()
            listener.onEnd()
        }
    }

    private fun fail(message: String) {
        playing = false
        busy = false
        generation++
        tts?.stop()
        listener.onChange()
        listener.onError(message)
    }

    companion object {
        private const val LOOKAHEAD = 6

        private var cachedVoices: List<String>? = null

        /** Names of the Russian voices installed on the device (for the voice picker). A cold engine reports none for a moment after init, so it asks again for a few seconds. */
        fun russianVoices(context: Context, onResult: (List<String>) -> Unit) {
            cachedVoices?.let { onResult(it); return }
            val handler = Handler(Looper.getMainLooper())
            var tts: TextToSpeech? = null
            var tries = 0
            fun read() {
                val names = runCatching { tts?.voices?.filter { it.locale.language == "ru" }?.map { it.name }?.sorted() }.getOrNull().orEmpty()
                if (names.isEmpty() && tries++ < 15) { handler.postDelayed(::read, 300); return }
                tts?.shutdown()
                if (names.isNotEmpty()) cachedVoices = names
                onResult(names)
            }
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) handler.post(::read) else { tts?.shutdown(); onResult(emptyList()) }
            }
        }
    }
}
