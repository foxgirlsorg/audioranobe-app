package com.audioranobe.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioranobe.app.core.Api
import com.audioranobe.app.data.ListeningHeatmapData
import com.audioranobe.app.data.ScoreStats
import com.audioranobe.app.data.UserStats
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.theme.InterFamily
import ir.ehsannarmani.compose_charts.ColumnChart
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.BarProperties
import ir.ehsannarmani.compose_charts.models.Bars
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Pie
import java.util.Calendar

/** components/ListeningHeatmap: a GitHub-style year grid of hours listened per day (fixed 5h scale). */
@Composable
fun ListeningHeatmap(userRef: String, initial: ListeningHeatmapData, modifier: Modifier = Modifier) {
    var year by remember(initial.year) { mutableStateOf(initial.year) }
    var data by remember(initial) { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(year) {
        if (year == initial.year) { data = initial; return@LaunchedEffect }
        loading = true
        runCatching { Api.get<ListeningHeatmapData>("/users/${Routes.enc(userRef)}/listening-heatmap", mapOf("year" to year)) }.onSuccess { data = it }
        loading = false
    }
    val cal = Calendar.getInstance()
    val today = cal.clone() as Calendar
    val isCurrentYear = data.year == today.get(Calendar.YEAR)
    val start = Calendar.getInstance().apply { set(data.year, Calendar.JANUARY, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
    // Monday-first column offset.
    val startDow = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val days = if (start.getActualMaximum(Calendar.DAY_OF_YEAR) == 366) 366 else 365
    val weeks = (startDow + days + 6) / 7
    val cell = 11.dp; val gap = 2.dp
    val months = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
    val levelColors = listOf(Ar.fill06, Ar.accent.copy(alpha = 0.3f), Ar.accent.copy(alpha = 0.5f), Ar.accent.copy(alpha = 0.75f), Ar.accent)

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Активность", Modifier.weight(1f))
            if (data.years.size > 1) SelectMenu(year, data.years.map { SelectOption(it, it.toString()) }, { year = it }, Modifier.width(110.dp), small = true)
            else Text(data.year.toString(), color = Ar.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.padding(end = 4.dp, top = 14.dp)) {
                for (i in 0 until 7) Box(Modifier.height(cell + gap), contentAlignment = Alignment.CenterStart) {
                    if (i == 1 || i == 3 || i == 5) Text(listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[i], color = Ar.textMuted, fontSize = 9.sp, lineHeight = 9.sp)
                }
            }
            val todayKey = String.format("%04d-%02d-%02d", today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))
            Column {
                Row {
                    var lastMonth = -1
                    for (w in 0 until weeks) {
                        val d = start.clone() as Calendar
                        d.add(Calendar.DAY_OF_YEAR, w * 7 - startDow)
                        if (d.before(start)) d.time = start.time
                        val m = d.get(Calendar.MONTH)
                        Box(Modifier.width(cell + gap).height(12.dp)) {
                            if (m != lastMonth && d.get(Calendar.YEAR) == data.year) { Text(months[m], color = Ar.textMuted, fontSize = 9.sp, lineHeight = 9.sp); lastMonth = m }
                        }
                    }
                }
                Canvas(Modifier.width((cell + gap) * weeks).height((cell + gap) * 7).padding(top = 2.dp)) {
                    val c = cell.toPx(); val g = gap.toPx()
                    val d = start.clone() as Calendar
                    for (i in 0 until days) {
                        val idx = i + startDow
                        val col = idx / 7; val row = idx % 7
                        val key = String.format("%04d-%02d-%02d", d.get(Calendar.YEAR), d.get(Calendar.MONTH) + 1, d.get(Calendar.DAY_OF_MONTH))
                        val secs = data.days[key] ?: 0
                        val future = isCurrentYear && key > todayKey
                        val level = when { secs <= 0 -> 0; secs >= 5 * 3600 -> 4; secs >= 2 * 3600 -> 3; secs >= 1800 -> 2; else -> 1 }
                        drawRoundRect(color = if (future) Color.Transparent else levelColors[level], topLeft = Offset(col * (c + g), row * (c + g)), size = Size(c, c), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        if (future) drawRoundRect(color = Ar.fill04, topLeft = Offset(col * (c + g), row * (c + g)), size = Size(c, c), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        d.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
        }
        if (loading) Text("Загружаем…", color = Ar.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** components/UserStatsCharts: ratings bar chart + status/country pies. */
@Composable
fun UserStatsCharts(score: ScoreStats, stats: UserStats, modifier: Modifier = Modifier) {
    val status = listOf(
        Triple("Завершено", stats.completed, Ar.accent), Triple("В процессе", stats.in_progress, Ar.accent.copy(alpha = 0.55f)),
        Triple("В планах", stats.planning, Ar.accent.copy(alpha = 0.3f)), Triple("Брошено", stats.dropped, Color.White.copy(alpha = 0.16f)),
    ).filter { it.second > 0 }
    val countryLabels = mapOf("japan" to "Япония", "china" to "Китай", "korea" to "Корея")
    val countryColors = mapOf("japan" to Ar.accent, "china" to Ar.accent.copy(alpha = 0.55f), "korea" to Ar.accent.copy(alpha = 0.3f))
    val countries = score.countries.map { Triple(countryLabels[it.country] ?: it.country, it.count, countryColors[it.country] ?: Color.White.copy(alpha = 0.16f)) }
    if (score.scores.isEmpty() && status.isEmpty() && countries.isEmpty()) return
    var metric by remember { mutableStateOf("titles") }
    val labelStyle = TextStyle(color = Ar.textMuted, fontSize = 11.sp, fontFamily = InterFamily)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (score.scores.isNotEmpty()) GlassPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Оценки", Modifier.weight(1f))
                ArTabs(listOf(TabItem("titles", "Книг"), TabItem("hours", "Часов")), metric, { metric = it }, scrollable = false)
            }
            Spacer(Modifier.height(12.dp))
            val bars = (1..10).map { s ->
                val row = score.scores.firstOrNull { it.score == s }
                val v = if (metric == "titles") (row?.titles ?: 0).toDouble() else (row?.hours ?: 0.0)
                Bars(label = s.toString(), values = listOf(Bars.Data(value = v, color = SolidColor(Ar.accent))))
            }
            ColumnChart(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                data = bars,
                barProperties = BarProperties(thickness = 14.dp, spacing = 4.dp, cornerRadius = Bars.Data.Radius.Rectangle(topLeft = 4.dp, topRight = 4.dp)),
                labelProperties = LabelProperties(enabled = true, textStyle = labelStyle),
                indicatorProperties = HorizontalIndicatorProperties(enabled = true, textStyle = labelStyle, count = ir.ehsannarmani.compose_charts.models.IndicatorCount.CountBased(4)),
                gridProperties = GridProperties(enabled = true, xAxisProperties = GridProperties.AxisProperties(enabled = true, color = SolidColor(Ar.border), lineCount = 4), yAxisProperties = GridProperties.AxisProperties(enabled = false)),
                labelHelperProperties = LabelHelperProperties(enabled = false),
                maxValue = maxOf(1.0, bars.maxOf { it.values[0].value }),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (status.isNotEmpty()) DistributionPie("Статус", status, Modifier.weight(1f))
            if (countries.isNotEmpty()) DistributionPie("Страна", countries, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DistributionPie(title: String, slices: List<Triple<String, Int, Color>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    GlassPanel(modifier) {
        Eyebrow(title)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            PieChart(modifier = Modifier.size(110.dp), data = slices.map { Pie(label = it.first, data = it.second.toDouble(), color = it.third) }, spaceDegree = 3f, style = Pie.Style.Stroke(width = 18.dp))
        }
        Spacer(Modifier.height(8.dp))
        for ((label, v, color) in slices) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Ar.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${Math.round(v * 100.0 / total)}%", color = Ar.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Simple horizontal stat bar (donate goal etc.). */
@Composable
fun GoalBar(pct: Double, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).background(Ar.fill06, RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth((pct / 100).toFloat().coerceIn(0f, 1f)).height(8.dp).background(Brush.horizontalGradient(listOf(Ar.accent.copy(alpha = 0.6f), Ar.accent)), RoundedCornerShape(4.dp)))
    }
}
