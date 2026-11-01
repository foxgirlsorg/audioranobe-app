package org.foxgirls.audioranobe.tts

import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.ChapterPlay

/** Chapter text → sentences, the way the site splits it for the full-screen reader (lib/browserNarration.ts). */
object TtsText {
    /** Stored-length scale: saved progress is counted in characters ÷ this, same as the server's estimated chapter length. */
    const val CHARS_PER_SECOND = 14.0
    private const val MAX_SENTENCE = 300
    private val sentenceEnd = Regex("(?<=[.!?…])\\s+(?!—\\s*[а-яё])")
    private val spaces = Regex("\\s+")

    /** The text as paragraphs of sentences. */
    fun paragraphs(text: String): List<List<String>> =
        text.split(Regex("\n+"))
            .map { it.replace(spaces, " ").trim() }
            .filter { it.isNotEmpty() }
            .map { p -> p.split(sentenceEnd).flatMap(::breakLong).filter { it.isNotEmpty() } }
            .filter { it.isNotEmpty() }

    fun sentences(text: String): List<String> = paragraphs(text).flatten()

    private fun breakLong(sentence: String): List<String> {
        val out = ArrayList<String>()
        var s = sentence
        while (s.length > MAX_SENTENCE) {
            val cut = maxOf(s.lastIndexOf(' ', MAX_SENTENCE), s.lastIndexOf(", ", MAX_SENTENCE) + 1)
            val at = if (cut > MAX_SENTENCE / 3) cut else MAX_SENTENCE
            out.add(s.substring(0, at).trim())
            s = s.substring(at).trim()
        }
        out.add(s)
        return out
    }

    /** "Том 2 глава 5 - Название": the volume only when the title has several, the name only when it says more than the number. */
    fun announcement(ch: ChapterPlay): String {
        val parts = ArrayList<String>()
        if (ch.volume.count > 1) parts.add("${ch.title.volume_label} ${Fmt.trimNum(ch.volume.number)}")
        val range = ch.number_end?.let { "${Fmt.trimNum(ch.number)}–${Fmt.trimNum(it)}" } ?: Fmt.trimNum(ch.number)
        parts.add("${if (parts.isEmpty()) "Глава" else "глава"} $range")
        val name = ch.name.trim()
        val bare = Regex("^(глава|главы)?\\s*[\\d.,\\s–-]*$", RegexOption.IGNORE_CASE).matches(name)
        return if (name.isNotEmpty() && !bare) "${parts.joinToString(" ")} - $name" else parts.joinToString(" ")
    }
}
