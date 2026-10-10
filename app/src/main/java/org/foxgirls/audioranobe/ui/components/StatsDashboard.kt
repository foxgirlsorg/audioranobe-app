package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.Labels
import org.foxgirls.audioranobe.data.NarratorStats
import org.foxgirls.audioranobe.data.StatsDay
import org.foxgirls.audioranobe.data.StatsLists
import org.foxgirls.audioranobe.data.TitleStats
import org.foxgirls.audioranobe.data.TitleStatsChapter
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Private stats dashboards (title edit «Статистика» tab, narrator edit stats
 * tab), mirroring the site's StatsBlocks / TitleStatsDashboard.
 */

/* Validated categorical slots 1–4 (dark surface), fixed order = list order. */
private val LIST_COLORS = mapOf(
    "planning" to Color(0xFF3987E5),
    "in_progress" to Color(0xFFD95926),
    "completed" to Color(0xFF199E70),
    "dropped" to Color(0xFFC98500),
)
private val STARTED = Ar.accent.copy(alpha = 0.35f)

private fun fmtHours(sec: Long): String {
    val h = sec / 3600.0
    return when {
        h >= 10 -> "${h.roundToInt()} ч"
        h >= 1 -> "${"%.1f".format(h)} ч"
        else -> "${(sec / 60.0).roundToInt()} мин"
    }
}

private fun fmtDay(d: String): String {
    val p = d.split("-")
    return if (p.size == 3) "${p[2].toInt()}.${p[1]}" else d
}

private fun num(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private fun chapterLabel(c: TitleStatsChapter) =
    "${num(c.volume_number)}·${num(c.number)}" + (c.number_end?.let { "–${num(it)}" } ?: "")

private fun List<StatsDay>.total(f: (StatsDay) -> Long): Long = fold(0L) { n, d -> n + f(d) }

@Composable
private fun RangeTabs(days: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow("За период")
        ArTabs(
            listOf(TabItem("7", "7 дн."), TabItem("30", "30 дн."), TabItem("90", "90 дн."), TabItem("365", "Год")),
            days.toString(), { onChange(it.toInt()) },
        )
    }
}

@Composable
private fun Kpi(k: KpiItem, modifier: Modifier) {
    GlassPanel(modifier) {
        Text(k.value, color = Ar.white, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(k.label.uppercase(), color = Ar.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        if (k.hint != null) Text(k.hint, color = Ar.textSecondary, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
        if (k.change != null) Text(
            "${if (k.change >= 0) "↑" else "↓"} ${kotlin.math.abs(k.change)}% к прошлому периоду",
            color = if (k.change >= 0) Ar.ok else Ar.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class KpiItem(val value: String, val label: String, val hint: String? = null, val change: Int? = null)

/** The dashboards fetch twice their window: (current window, previous window). */
private fun List<StatsDay>.split(days: Int): Pair<List<StatsDay>, List<StatsDay>> =
    takeLast(days) to dropLast(days)

/** Change vs the previous window in %, or null when there is nothing to compare with. */
private fun trend(cur: List<StatsDay>, prev: List<StatsDay>, f: (StatsDay) -> Long): Int? {
    val b = prev.total(f)
    if (b == 0L) return null
    return ((cur.total(f) - b) * 100.0 / b).roundToInt()
}

@Composable
private fun KpiGrid(items: List<KpiItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { Kpi(it, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PanelTitle(title: String, value: String? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(title, Modifier.weight(1f))
        if (value != null) Text(value, color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Bar chart drawn on a Canvas: tap or drag to inspect a bar; [caption] gets
 * the selected index (null = nothing selected). [back] draws a lighter bar
 * behind each foreground bar (chapter funnel: started vs finished).
 */
@Composable
private fun TapBars(
    n: Int,
    value: (Int) -> Double,
    caption: (Int?) -> String,
    height: Dp,
    back: ((Int) -> Double)? = null,
    axis: List<String>? = null,
    ends: List<String>? = null,
    maxLabel: ((Double) -> String)? = null,
) {
    if (n == 0) return
    var sel by remember(n) { mutableStateOf<Int?>(null) }
    val maxV = max(1.0, (0 until n).maxOf { max(value(it), back?.invoke(it) ?: 0.0) })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(caption(sel), color = if (sel != null) Ar.text else Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (maxLabel != null) Text("макс. ${maxLabel(maxV)}", color = Ar.textMuted, fontSize = 10.5.sp, modifier = Modifier.padding(start = 8.dp))
    }
    Spacer(Modifier.height(6.dp))
    val index: (Float, Float) -> Int = { x, w -> ((x / w) * n).toInt().coerceIn(0, n - 1) }
    val slop = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
    Canvas(
        Modifier.fillMaxWidth().height(height)
            .pointerInput(n, maxV) {
                detectTapGestures { o ->
                    // A tap on (or just above) a bar selects it; anywhere else resets to the untouched view.
                    val i = index(o.x, size.width.toFloat())
                    val v = max(value(i), back?.invoke(i) ?: 0.0)
                    val top = size.height - (v / maxV * size.height).toFloat()
                    sel = if (v > 0 && o.y >= top - slop && sel != i) i else null
                }
            }
            .pointerInput(n) { detectHorizontalDragGestures { change, _ -> sel = index(change.position.x, size.width.toFloat()) } },
    ) {
        val slot = size.width / n
        val gap = if (n > 60) min(1.dp.toPx(), slot * 0.3f) else slot * 0.2f
        val w = max(slot - gap, 1f)
        val radius = CornerRadius(min(w / 2, 3.dp.toPx()))
        for (i in 1..3) {
            val y = size.height * i / 4
            drawLine(Ar.border, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        for (i in 0 until n) {
            val x = i * slot + gap / 2
            val dim = sel != null && sel != i
            back?.invoke(i)?.takeIf { it > 0 }?.let { v ->
                val h = (v / maxV * size.height).toFloat()
                drawRoundRect(if (dim) STARTED.copy(alpha = 0.2f) else STARTED, Offset(x, size.height - h), Size(w, h), radius)
            }
            val v = value(i)
            if (v > 0) {
                val h = (v / maxV * size.height).toFloat()
                drawRoundRect(if (dim) Ar.accent.copy(alpha = 0.45f) else Ar.accent, Offset(x, size.height - h), Size(w, h), radius)
            }
        }
    }
    if (axis != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        axis.forEach { Text(it, color = Ar.textMuted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
    }
    if (ends != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ends.forEach { Text(it, color = Ar.textMuted, fontSize = 10.sp) }
    }
}

@Composable
private fun DayChart(title: String, series: List<StatsDay>, height: Dp, total: String?, value: (StatsDay) -> Long, format: (Long) -> String) {
    GlassPanel(Modifier.fillMaxWidth()) {
        PanelTitle(title, total)
        TapBars(
            series.size, { value(series[it]).toDouble() },
            { i -> if (i == null) "Коснитесь столбца" else "${fmtDay(series[i].day)}: ${format(value(series[i]))}" },
            height,
            ends = listOf(series.first(), series[series.size / 2], series.last()).map { fmtDay(it.day) },
            maxLabel = { format(it.toLong()) },
        )
    }
}

@Composable
private fun SeriesCharts(series: List<StatsDay>, minis: List<Pair<String, (StatsDay) -> Long>>) {
    if (series.isEmpty()) return
    DayChart("Время прослушивания", series, 170.dp, fmtHours(series.total { it.listen_seconds }), { it.listen_seconds }, ::fmtHours)
    DayChart("Слушатели по дням", series, 130.dp, null, { it.listeners.toLong() }, { "${Fmt.count(it)} слуш." })
    minis.forEach { (label, f) ->
        DayChart(label, series, 70.dp, Fmt.count(series.total(f)), f) { Fmt.count(it) }
    }
}

@Composable
private fun ListsPanel(lists: StatsLists) {
    GlassPanel(Modifier.fillMaxWidth()) {
        PanelTitle("В списках у пользователей", Fmt.count(lists.total))
        if (lists.total == 0) {
            Text("Пока ни у кого нет в списках.", color = Ar.textMuted, fontSize = 13.sp)
            return@GlassPanel
        }
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Labels.libraryValues.forEach { k ->
                val v = lists.of(k)
                if (v > 0) Box(Modifier.weight(v.toFloat()).height(12.dp).background(LIST_COLORS.getValue(k)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Labels.libraryValues.forEach { k ->
            val v = lists.of(k)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(LIST_COLORS.getValue(k), RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text(Labels.libraryStatus[k] ?: k, color = Ar.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(Fmt.count(v), color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${(v * 100.0 / lists.total).roundToInt()}%", color = Ar.textMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            }
        }
    }
}

@Composable
private fun RatingsPanel(dist: Map<String, Int>, avg: Double?, count: Int) {
    GlassPanel(Modifier.fillMaxWidth()) {
        PanelTitle("Распределение оценок", avg?.let { "${"%.1f".format(it)}★ · ${Fmt.count(count)}" })
        if (count == 0) {
            Text("Оценок пока нет.", color = Ar.textMuted, fontSize = 13.sp)
            return@GlassPanel
        }
        TapBars(
            10, { (dist[(it + 1).toString()] ?: 0).toDouble() },
            { i -> if (i == null) "Коснитесь столбца" else "Оценка ${i + 1}: ${Fmt.count(dist[(i + 1).toString()] ?: 0)} польз." },
            130.dp, axis = (1..10).map { it.toString() },
        )
    }
}

@Composable
private fun ChaptersPanel(chapters: List<TitleStatsChapter>) {
    GlassPanel(Modifier.fillMaxWidth()) {
        PanelTitle("Слушатели по главам")
        if (chapters.isEmpty()) {
            Text("Глав пока нет.", color = Ar.textMuted, fontSize = 13.sp)
            return@GlassPanel
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Box(Modifier.size(9.dp).background(STARTED, RoundedCornerShape(2.dp)))
            Text(" начали   ", color = Ar.textSecondary, fontSize = 11.5.sp)
            Box(Modifier.size(9.dp).background(Ar.accent, RoundedCornerShape(2.dp)))
            Text(" дослушали", color = Ar.textSecondary, fontSize = 11.5.sp)
        }
        val first = chapters.firstOrNull { it.listeners > 0 }?.listeners ?: 0
        TapBars(
            chapters.size, { chapters[it].finished.toDouble() },
            { i ->
                if (i == null) "Коснитесь главы" else chapters[i].let { c ->
                    "${chapterLabel(c)}: начали ${Fmt.count(c.listeners)}, дослушали ${Fmt.count(c.finished)}" +
                        if (first > 0) " · ${(c.listeners * 100.0 / first).roundToInt()}% от первой" else ""
                }
            },
            150.dp, back = { chapters[it].listeners.toDouble() },
            ends = if (chapters.size > 1) listOf(chapterLabel(chapters.first()), chapterLabel(chapters.last())) else null,
            maxLabel = { Fmt.count(it) },
        )
        var open by remember { mutableStateOf(false) }
        Text(
            if (open) "Скрыть таблицу" else "Таблица по главам", color = Ar.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp).clickable { open = !open },
        )
        if (open) chapters.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(chapterLabel(c) + (if (c.name.isNotEmpty()) " · ${c.name}" else ""), color = Ar.textSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${Fmt.count(c.listeners)} / ${Fmt.count(c.finished)}", color = Ar.text, fontSize = 12.5.sp)
                Text(if (c.listeners > 0) "${(c.finished * 100.0 / c.listeners).roundToInt()}%" else "—", color = Ar.textMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            }
        }
    }
}

/** Title edit «Статистика» tab (title's narrators and mods). */
@Composable
fun TitleStatsDashboard(titleId: Int) {
    var days by remember { mutableStateOf(30) }
    val loader = rememberLoader(titleId, days, keepOnReload = true) { Api.get<TitleStats>("/panel/titles/$titleId/stats", mapOf("days" to days * 2)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RangeTabs(days) { days = it }
        LoadBox(loader) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val (series, prev) = s.series.split(days)
                KpiGrid(listOf(
                    KpiItem(Fmt.count(s.listeners), "Слушатели"),
                    KpiItem(fmtHours(s.listen_seconds), "Время прослушивания", "${fmtHours(series.total { it.listen_seconds })} за период", trend(series, prev) { it.listen_seconds }),
                    KpiItem(Fmt.count(s.listens), "Прослушивания глав"),
                    KpiItem(Fmt.count(s.views_count), "Просмотры", "+${Fmt.count(series.total { it.views.toLong() })} новых", trend(series, prev) { it.views.toLong() }),
                    KpiItem(Fmt.count(s.favorites_count), "В избранном", "+${Fmt.count(series.total { it.favorites.toLong() })} за период", trend(series, prev) { it.favorites.toLong() }),
                    KpiItem(s.avg_rating?.let { "%.1f".format(it) } ?: "—", "Средняя оценка", "${Fmt.count(s.rating_count)} оценок"),
                    KpiItem(Fmt.count(s.lists.total), "В списках", "+${Fmt.count(series.total { it.library_adds.toLong() })} за период", trend(series, prev) { it.library_adds.toLong() }),
                    KpiItem(Fmt.count(s.comments_count), "Комментарии", null, trend(series, prev) { it.comments.toLong() }),
                    KpiItem(Fmt.count(s.embed_plays), "Из виджета", "+${Fmt.count(series.total { it.embed_plays.toLong() })} · сайтов: ${s.embed_sites.size}", trend(series, prev) { it.embed_plays.toLong() }),
                ))
                SeriesCharts(series, listOf(
                    "Новые посетители" to { d: StatsDay -> d.views.toLong() },
                    "Добавления в списки" to { d: StatsDay -> d.library_adds.toLong() },
                    "В избранное" to { d: StatsDay -> d.favorites.toLong() },
                    "Оценки" to { d: StatsDay -> d.ratings.toLong() },
                    "Комментарии" to { d: StatsDay -> d.comments.toLong() },
                    "Прослушивания из виджета" to { d: StatsDay -> d.embed_plays.toLong() },
                ))
                ListsPanel(s.lists)
                RatingsPanel(s.rating_distribution, s.avg_rating, s.rating_count)
                GlassPanel(Modifier.fillMaxWidth()) {
                    PanelTitle("Сайты с виджетом", Fmt.count(s.embed_sites.size))
                    if (s.embed_sites.isEmpty()) Text("Виджет тайтла пока не слушали на других сайтах.", color = Ar.textMuted, fontSize = 13.sp)
                    s.embed_sites.forEach { site ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(site.host, color = Ar.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(Fmt.count(site.plays), color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                ChaptersPanel(s.chapters)
            }
        }
    }
}

/** Narrator edit «Статистика» tab. */
@Composable
fun NarratorStatsDashboard(narratorId: Int) {
    val nav = LocalNav.current
    var days by remember { mutableStateOf(30) }
    val loader = rememberLoader(narratorId, days, keepOnReload = true) { Api.get<NarratorStats>("/panel/narrators/$narratorId/stats", mapOf("days" to days * 2)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RangeTabs(days) { days = it }
        LoadBox(loader) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val t = s.totals
                val (series, prev) = s.series.split(days)
                KpiGrid(listOf(
                    KpiItem(Fmt.count(s.subscribers_count), "Подписчики", "+${Fmt.count(series.total { it.subscribers.toLong() })} за период", trend(series, prev) { it.subscribers.toLong() }),
                    KpiItem(Fmt.count(t.listeners), "Слушатели"),
                    KpiItem(fmtHours(t.listen_seconds), "Время прослушивания", "${fmtHours(series.total { it.listen_seconds })} за период", trend(series, prev) { it.listen_seconds }),
                    KpiItem(Fmt.count(t.listens), "Прослушивания глав"),
                    KpiItem(Fmt.count(t.views), "Просмотры", "+${Fmt.count(series.total { it.views.toLong() })} новых", trend(series, prev) { it.views.toLong() }),
                    KpiItem(Fmt.count(t.favorites), "В избранном", "+${Fmt.count(series.total { it.favorites.toLong() })} за период", trend(series, prev) { it.favorites.toLong() }),
                    KpiItem(t.avg_rating?.let { "%.1f".format(it) } ?: "—", "Средняя оценка", "${Fmt.count(t.ratings)} оценок"),
                    KpiItem(Fmt.count(t.lists.total), "В списках", "+${Fmt.count(series.total { it.library_adds.toLong() })} за период", trend(series, prev) { it.library_adds.toLong() }),
                    KpiItem(Fmt.count(t.embed_plays), "Из виджета", "+${Fmt.count(series.total { it.embed_plays.toLong() })} за период", trend(series, prev) { it.embed_plays.toLong() }),
                ))
                SeriesCharts(series, listOf(
                    "Новые подписчики" to { d: StatsDay -> d.subscribers.toLong() },
                    "Новые посетители" to { d: StatsDay -> d.views.toLong() },
                    "Добавления в списки" to { d: StatsDay -> d.library_adds.toLong() },
                    "В избранное" to { d: StatsDay -> d.favorites.toLong() },
                    "Оценки" to { d: StatsDay -> d.ratings.toLong() },
                    "Комментарии" to { d: StatsDay -> d.comments.toLong() },
                    "Прослушивания из виджета" to { d: StatsDay -> d.embed_plays.toLong() },
                ))
                ListsPanel(t.lists)
                RatingsPanel(t.rating_distribution, t.avg_rating, t.ratings)
                GlassPanel(Modifier.fillMaxWidth()) {
                    PanelTitle("По тайтлам", Fmt.count(s.titles.size))
                    if (s.titles.isEmpty()) Text("Тайтлов пока нет.", color = Ar.textMuted, fontSize = 13.sp)
                    s.titles.sortedWith(compareByDescending<org.foxgirls.audioranobe.data.NarratorStatsTitle> { it.listeners }.thenByDescending { it.listens }).forEach { x ->
                        Column(Modifier.fillMaxWidth().clickable { nav.go(Routes.titleEdit(x.slug, "stats")) }.padding(vertical = 8.dp)) {
                            Text(x.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Fmt.count(x.listeners)} слуш. · ${Fmt.count(x.listens)} просл. · ${Fmt.count(x.lists.total)} в списках" +
                                    (x.avg_rating?.let { " · ${"%.1f".format(it)}★" } ?: ""),
                                color = Ar.textMuted, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
