package org.foxgirls.audioranobe.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Port of lib/format.ts. All output is Russian, like the site. */
object Fmt {
    private val ru = Locale.forLanguageTag("ru-RU")

    fun duration(totalSeconds: Number?): String {
        var s = totalSeconds?.toDouble() ?: 0.0
        if (s.isNaN() || s < 0) s = 0.0
        val sec = floor(s).toLong()
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val r = sec % 60
        fun two(n: Long) = n.toString().padStart(2, '0')
        return if (h > 0) "$h:${two(m)}:${two(r)}" else "$m:${two(r)}"
    }

    fun toDate(v: Any?): Date? = when (v) {
        null -> null
        is Number -> Date(v.toLong() * 1000)
        is String -> parseIso(v)
        else -> null
    }

    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd",
    )

    fun parseIso(s: String): Date? {
        val t = s.trim()
        if (t.isEmpty()) return null
        t.toLongOrNull()?.let { return Date(it * 1000) }
        for (f in isoFormats) {
            try {
                val df = SimpleDateFormat(f, Locale.US)
                if (!f.contains("X") && !f.contains("'Z'")) df.timeZone = java.util.TimeZone.getTimeZone("UTC")
                if (f.contains("'Z'")) df.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return df.parse(t)
            } catch (_: Exception) {}
        }
        return null
    }

    fun date(v: Any?): String {
        val d = toDate(v) ?: return ""
        return SimpleDateFormat("d MMM yyyy", ru).format(d).replace(".", "")
    }

    fun time(v: Any?): String {
        val d = toDate(v) ?: return ""
        return SimpleDateFormat("HH:mm", ru).format(d)
    }

    fun timeAgo(v: Any?): String {
        val d = toDate(v) ?: return ""
        val diff = ((System.currentTimeMillis() - d.time) / 1000)
        if (diff < 45) return "только что"
        if (diff < 3600) {
            val m = maxOf(1, (diff / 60).toInt())
            return "$m ${plural(m, "минуту", "минуты", "минут")} назад"
        }
        if (diff < 86400) {
            val h = (diff / 3600).toInt()
            return "$h ${plural(h, "час", "часа", "часов")} назад"
        }
        if (diff < 86400 * 30) {
            val days = (diff / 86400).toInt()
            return if (days == 1) "вчера" else "$days ${plural(days, "день", "дня", "дней")} назад"
        }
        return date(v)
    }

    /** Short comment-style relative time ("5 мин назад"). */
    fun timeAgoShort(v: Any?): String {
        val d = toDate(v) ?: return ""
        val s = maxOf(0.0, (System.currentTimeMillis() - d.time) / 1000.0)
        if (s < 60) return "только что"
        val m = s / 60
        if (m < 60) return "${floor(m).toInt()} мин назад"
        val h = m / 60
        if (h < 24) return "${floor(h).toInt()} ч назад"
        val dd = h / 24
        if (dd < 7) return "${floor(dd).toInt()} дн назад"
        if (dd < 30) return "${floor(dd / 7).toInt()} нед назад"
        if (dd < 365) return "${floor(dd / 30).toInt()} мес назад"
        return "${floor(dd / 365).toInt()} г. назад"
    }

    fun lastSeen(v: Any?): String {
        val d = toDate(v) ?: return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = d }
        val t = SimpleDateFormat("HH:mm", ru).format(d)
        val startNow = now.clone() as Calendar
        startNow.set(Calendar.HOUR_OF_DAY, 0); startNow.set(Calendar.MINUTE, 0); startNow.set(Calendar.SECOND, 0); startNow.set(Calendar.MILLISECOND, 0)
        val startThen = then.clone() as Calendar
        startThen.set(Calendar.HOUR_OF_DAY, 0); startThen.set(Calendar.MINUTE, 0); startThen.set(Calendar.SECOND, 0); startThen.set(Calendar.MILLISECOND, 0)
        val dayDiff = ((startNow.timeInMillis - startThen.timeInMillis) / 86_400_000.0).roundToInt()
        if (dayDiff <= 0) return "сегодня в $t"
        if (dayDiff == 1) return "вчера в $t"
        if (dayDiff < 7) return "${SimpleDateFormat("EEEE", ru).format(d)}, $t"
        if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) return SimpleDateFormat("d MMMM", ru).format(d)
        return SimpleDateFormat("d MMMM yyyy", ru).format(d)
    }

    fun presenceLabel(status: String, lastSeenAt: String?): String {
        if (status == "online") return "в сети"
        return if (lastSeenAt != null) "был(а) в сети ${lastSeen(lastSeenAt)}" else "не в сети"
    }

    fun presenceLabelCompact(status: String, lastSeenAt: String?): String {
        if (status == "online") return "в сети"
        return if (lastSeenAt != null) lastSeen(lastSeenAt) else "не в сети"
    }

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        if (m10 == 1 && m100 != 11) return one
        if (m10 in 2..4 && (m100 < 10 || m100 >= 20)) return few
        return many
    }

    fun count(n: Number?): String {
        val v = n?.toDouble() ?: return "0"
        if (v.isNaN()) return "0"
        if (abs(v) < 1000) return v.toLong().toString()
        if (abs(v) < 1_000_000) {
            val k = v / 1000
            return (if (k >= 100) k.roundToInt().toString() else ((k * 10).roundToInt() / 10.0).trim()) + "k"
        }
        val mm = v / 1_000_000
        return (if (mm >= 100) mm.roundToInt().toString() else ((mm * 10).roundToInt() / 10.0).trim()) + "M"
    }

    private fun Double.trim(): String = if (this == floor(this)) this.toLong().toString() else this.toString()

    fun rating(r: Double?): String = if (r == null || r.isNaN()) "—" else String.format(Locale.US, "%.1f", r)

    fun trimNum(n: Double): String {
        val r = (n * 1000).roundToInt() / 1000.0
        return if (r == floor(r)) r.toLong().toString() else r.toString()
    }

    /** "30" or "30–35". */
    fun chapterNumber(number: Double, numberEnd: Double?): String =
        if (numberEnd != null && numberEnd > number) "${trimNum(number)}–${trimNum(numberEnd)}" else trimNum(number)

    /** "Глава 30" / "Главы 30–35", or the chapter's own name. */
    fun chapterLabel(number: Double, numberEnd: Double?, name: String?): String {
        if (!name.isNullOrBlank()) return name
        val ranged = numberEnd != null && numberEnd > number
        return "${if (ranged) "Главы" else "Глава"} ${chapterNumber(number, numberEnd)}"
    }

    fun chapterFilePrefix(n: Double): String {
        val whole = floor(n).toLong()
        val frac = ((n - whole) * 1000).roundToInt() / 1000.0
        return whole.toString().padStart(3, '0') + (if (frac > 0) frac.toString().substring(1) else "")
    }

    fun initials(username: String): String {
        val parts = username.split(Regex("[_\\-.]+")).filter { it.isNotEmpty() }
        if (parts.size >= 2) return (parts[0].take(1) + parts[1].take(1)).uppercase()
        return username.take(2).uppercase()
    }

    fun usd(cents: Long): String = "$" + String.format(ru, "%,d", cents / 100)

    fun bytes(n: Long?): String {
        if (n == null) return "—"
        if (n < 1024) return "$n Б"
        val kb = n / 1024.0
        if (kb < 1024) return String.format(ru, "%.1f КБ", kb)
        val mb = kb / 1024
        if (mb < 1024) return String.format(ru, "%.1f МБ", mb)
        return String.format(ru, "%.2f ГБ", mb / 1024)
    }

    fun titlesPlural(n: Int) = "$n ${plural(n, "тайтл", "тайтла", "тайтлов")}"

    /** Markdown reduced to a flat single-line summary. */
    fun plainSummary(markdown: String, maxLen: Int = 200): String {
        var text = markdown
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("\\|\\|([\\s\\S]*?)\\|\\|"), "$1")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
            .replace(Regex("(?m)^\\s{0,3}>+\\s?"), "")
            .replace(Regex("(?m)^\\s{0,3}([-*+]|\\d+[.)])\\s+"), "")
            .replace(Regex("(\\*\\*\\*|___)([^*_]+)\\1"), "$2")
            .replace(Regex("(\\*\\*|__)([^*_]+)\\1"), "$2")
            .replace(Regex("(\\*|_)([^*_]+)\\1"), "$2")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.length <= maxLen) return text
        val cut = text.substring(0, maxLen)
        val lastSpace = cut.lastIndexOf(' ')
        text = if (lastSpace > maxLen * 0.6) cut.substring(0, lastSpace) else cut
        return text.trimEnd() + "…"
    }
}
