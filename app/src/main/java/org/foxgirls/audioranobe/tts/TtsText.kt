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

    /** Pronunciation marks for the Silero voice (U+0301 stress, U+0308 ё on a spelled е). The screen and the device voice drop them. */
    private val MARKS = Regex("[\\u0301\\u0308]")

    fun plain(sentence: String): String = sentence.replace(MARKS, "")

    /** Characters of a sentence on the stored-length scale: marks do not count. */
    fun length(sentence: String): Int = plain(sentence).length

    /** What the device voice gets: no stress marks, and a marked е spelled as the ё it is read as. */
    fun spoken(sentence: String): String = plain(sentence.replace("е\u0308", "ё").replace("Е\u0308", "Ё"))

    /** Toggles the stress mark at a selection or caret: the new text and its selection, or null when nothing applies. */
    fun toggleStress(text: String, start: Int, end: Int): Triple<String, Int, Int>? {
        val lo = minOf(start, end)
        var hi = maxOf(start, end)
        if (lo == hi) return caretStress(text, lo)
        while (hi < text.length && isMark(text[hi])) hi++
        val range = text.substring(lo, hi)
        if (range.any { isMark(it) }) {
            val bare = plain(range)
            return Triple(text.substring(0, lo) + bare + text.substring(hi), lo, lo + bare.length)
        }
        val vowel = (lo until hi).lastOrNull { isVowel(text[it]) } ?: return null
        return caretStress(text, vowel + 1)
    }

    private fun caretStress(text: String, caret: Int): Triple<String, Int, Int>? {
        var p = caret
        while (p < text.length && isMark(text[p])) p++
        var q = p - 1
        while (q >= 0 && isMark(text[q])) q--
        val target = when {
            q >= 0 && isVowel(text[q]) -> q
            p < text.length && isVowel(text[p]) -> p
            else -> return null
        }
        var after = target + 1
        while (after < text.length && isMark(text[after])) after++
        if (after > target + 1) {
            return Triple(text.substring(0, target + 1) + text.substring(after), target + 1, target + 1)
        }
        var ws = target
        while (ws > 0 && (isCyrillic(text[ws - 1]) || isMark(text[ws - 1]))) ws--
        var we = target + 1
        while (we < text.length && (isCyrillic(text[we]) || isMark(text[we]))) we++
        val head = plain(text.substring(ws, target + 1))
        val tail = plain(text.substring(target + 1, we))
        val mark = if (text[target].lowercaseChar() == 'ё') "" else "\u0301"
        val next = text.substring(0, ws) + head + mark + tail + text.substring(we)
        if (next == text) return null
        val caretAt = ws + head.length + mark.length
        return Triple(next, caretAt, caretAt)
    }

    private fun isMark(c: Char): Boolean = c == '\u0301' || c == '\u0308'
    private fun isVowel(c: Char): Boolean = c.lowercaseChar() in "аеёиоуыэюя"
    private fun isCyrillic(c: Char): Boolean = c in 'а'..'я' || c in 'А'..'Я' || c == 'ё' || c == 'Ё'

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
    fun announcement(ch: ChapterPlay): String =
        announcement(ch.volume.count, ch.title.volume_label, ch.volume.number, ch.number, ch.number_end, ch.name)

    fun announcement(volumeCount: Int, volumeLabel: String, volumeNumber: Double, number: Double, numberEnd: Double?, chapterName: String): String {
        val parts = ArrayList<String>()
        if (volumeCount > 1) parts.add("$volumeLabel ${Fmt.trimNum(volumeNumber)}")
        val range = numberEnd?.let { "${Fmt.trimNum(number)}–${Fmt.trimNum(it)}" } ?: Fmt.trimNum(number)
        parts.add("${if (parts.isEmpty()) "Глава" else "глава"} $range")
        val name = chapterName.trim()
        val bare = Regex("^(глава|главы)?\\s*[\\d.,\\s–-]*$", RegexOption.IGNORE_CASE).matches(name)
        return if (name.isNotEmpty() && !bare) "${parts.joinToString(" ")} - $name" else parts.joinToString(" ")
    }
}
