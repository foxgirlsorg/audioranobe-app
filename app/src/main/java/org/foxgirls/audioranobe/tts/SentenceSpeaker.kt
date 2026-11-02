package org.foxgirls.audioranobe.tts

/**
 * Reads a chapter aloud sentence by sentence and reports which sentence is being spoken. Implemented by the
 * Silero voice ([SileroSpeaker]) and by Android's own speech synthesizer ([SystemSpeaker]).
 *
 * The first [skipped] sentences (the chapter announcement) are spoken but not counted in [progressSeconds].
 * Everything but the audio threads runs on the main thread.
 */
abstract class SentenceSpeaker(
    protected val sentences: List<String>,
    protected val skipped: Int,
    protected val listener: Listener,
) {
    interface Listener {
        fun onChange()
        fun onEnd()
        fun onError(message: String)
    }

    /** Characters before each counted sentence, for the stored-length progress scale. */
    private val before = IntArray(sentences.size + 1).also { a ->
        for (i in sentences.indices) a[i + 1] = a[i] + if (i >= skipped) sentences[i].length else 0
    }
    val totalSeconds: Double get() = before[sentences.size] / TtsText.CHARS_PER_SECOND

    /** The sentence being spoken. */
    var index = 0; protected set
    /** Nothing is audible yet: the next sentence is still being prepared. */
    var busy = false; protected set
    var playing = false; protected set
    var finished = false; protected set
    @set:JvmName("storeRate")
    var rate = 1f; protected set

    /** Position in the chapter, stored-length seconds; [within] is how far into the current sentence (0..1). */
    protected fun seconds(within: Double = 0.0): Double {
        val i = index.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        val chars = before[i.coerceAtLeast(skipped).coerceAtMost(sentences.size)] + if (i >= skipped) sentences[i].length * within else 0.0
        return chars / TtsText.CHARS_PER_SECOND
    }

    open fun progressSeconds(): Double = seconds()

    /** The sentence that holds a point of the stored-length scale (the announcement is never the target). */
    fun sentenceAt(seconds: Double): Int {
        val chars = seconds * TtsText.CHARS_PER_SECOND
        for (i in skipped until sentences.size) if (before[i + 1] > chars) return i
        return (sentences.size - 1).coerceAtLeast(0)
    }

    /** Points at a sentence without starting anything (a chapter restored paused). */
    fun cue(from: Int) { index = from.coerceIn(0, (sentences.size - 1).coerceAtLeast(0)) }

    abstract fun start(from: Int, play: Boolean)
    abstract fun pause()
    abstract fun resume()
    open fun setRate(r: Float) { rate = r }
    abstract fun release()
}
