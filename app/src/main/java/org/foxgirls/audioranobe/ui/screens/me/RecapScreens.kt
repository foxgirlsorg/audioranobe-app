package org.foxgirls.audioranobe.ui.screens.me

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.core.msg
import org.foxgirls.audioranobe.data.Recap
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.LoadBox
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.pagePadding
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- helpers ----------

private fun minutesLabel(sec: Long): String {
    val m = maxOf(1L, Math.round(sec / 60.0)).toInt()
    return "$m ${Fmt.plural(m, "минута", "минуты", "минут")}"
}

private fun bigTime(totalSeconds: Long): Pair<String, String> {
    val hours = (totalSeconds / 3600).toInt()
    val mins = ((totalSeconds % 3600) / 60).toInt()
    return if (hours > 0) hours.toString() to Fmt.plural(hours, "час", "часа", "часов")
    else mins.toString() to Fmt.plural(mins, "минута", "минуты", "минут")
}

/** Saves a bitmap into the device gallery (Pictures/AudioRanobe). */
private suspend fun saveToGallery(context: Context, bitmap: Bitmap, name: String) = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AudioRanobe")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Не удалось сохранить")
    resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}

/** Records the drawn content of [layer] so it can be exported as an image. */
private fun Modifier.captureTo(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

// ---------- app/me/recap ----------

@Composable
fun RecapScreen() {
    if (RequireAuth()) return
    val loader = rememberLoader(Unit) { Api.get<Recap>("/me/recap") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var exporting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MeHeader("Личное", "Итоги", "месяца")
        LoadBox(loader, Modifier.padding(pagePadding(top = 0))) { recap ->
            if (recap.total_seconds <= 0) {
                EmptyState("Пока пусто", "За прошлый месяц вы ещё не дослушали ни одной главы на сайте.", Lucide.Headphones)
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RecapMonthlyCard(recap, Modifier.fillMaxWidth().captureTo(layer))
                    Spacer(Modifier.height(14.dp))
                    ArButton(
                        if (exporting) "Готовим…" else "Скачать картинку", kind = ButtonKind.Primary, icon = Lucide.Download, busy = exporting,
                        onClick = {
                            if (exporting) return@ArButton
                            exporting = true
                            scope.launch {
                                try {
                                    // Re-fetch before capture so the exported card shows server data only.
                                    val fresh = Api.get<Recap>("/me/recap")
                                    loader.set(fresh)
                                    kotlinx.coroutines.delay(150)
                                    saveToGallery(context, layer.toImageBitmap().asAndroidBitmap(), "recap-${fresh.period_label}")
                                    toast("Сохранено в галерею")
                                } catch (e: Exception) { toastError(e) }
                                exporting = false
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Single shareable monthly recap card (dark + coral theme). */
@Composable
fun RecapMonthlyCard(recap: Recap, modifier: Modifier = Modifier) {
    val (big, unit) = bigTime(recap.total_seconds)
    val topTitles = recap.top_titles.take(3)
    val topNarrator = recap.top_narrators.firstOrNull()
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.aspectRatio(4f / 5f).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1B1418), Color(0xFF161616), Color(0xFF2A1622))), shape)
            .border(1.dp, Ar.border, shape),
    ) {
        // Soft coral glows, like the site's card.
        Box(
            Modifier.size(260.dp).align(Alignment.TopEnd).padding(top = 0.dp)
                .background(Brush.radialGradient(listOf(Ar.accent.copy(alpha = 0.35f), Color.Transparent))),
        )
        Box(
            Modifier.size(220.dp).align(Alignment.BottomStart)
                .background(Brush.radialGradient(listOf(Ar.accent.copy(alpha = 0.18f), Color.Transparent))),
        )
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(recap.display_name.ifBlank { recap.username }, color = Ar.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (recap.username.isNotBlank()) Text("@${recap.username}", color = Ar.textMuted, fontSize = 12.sp)
                }
                Text(recap.period_label, color = Ar.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(big, color = Ar.text, fontWeight = FontWeight.Black, fontSize = if (big.length > 3) 64.sp else 88.sp, lineHeight = 88.sp)
            Text("$unit прослушано", color = Ar.textSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Stat(recap.files_count, Fmt.plural(recap.files_count, "глава", "главы", "глав"))
                Stat(recap.titles_count, Fmt.plural(recap.titles_count, "тайтл", "тайтла", "тайтлов"))
                if (recap.books_finished > 0) Stat(recap.books_finished, "целиком")
            }
            if (topTitles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(if (topTitles.size > 1) "ЛЮБИМЫЕ ТАЙТЛЫ" else "ЛЮБИМЫЙ ТАЙТЛ", color = Ar.textMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                topTitles.forEach { t ->
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, color = Ar.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(minutesLabel(t.seconds), color = Ar.textMuted, fontSize = 11.sp)
                    }
                }
            }
            if (topNarrator != null) {
                Spacer(Modifier.height(10.dp))
                Text("ЛЮБИМЫЙ ЧТЕЦ", color = Ar.textMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(topNarrator.name, color = Ar.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(minutesLabel(topNarrator.seconds), color = Ar.textMuted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("AudioRanobe", color = Ar.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun Stat(n: Int, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(n.toString(), color = Ar.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, color = Ar.textSecondary, fontSize = 12.sp)
    }
}

// ---------- app/me/recap/[year] ----------

private val MONTH_PALETTE = listOf(
    listOf(Color(0xFF2A1622), Color(0xFFDE6161)),
    listOf(Color(0xFF1A1030), Color(0xFF6D4BD0)),
    listOf(Color(0xFF101C2E), Color(0xFF2F7FD0)),
    listOf(Color(0xFF221528), Color(0xFFB0479A)),
    listOf(Color(0xFF17251A), Color(0xFF3FA86A)),
)

@Composable
fun RecapYearScreen(year: Int) {
    if (RequireAuth()) return
    val loader = rememberLoader(year) { Api.get<Recap>("/me/recap/$year") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MeHeader("Личное", "Итоги", year.toString())
        LoadBox(loader, Modifier.padding(pagePadding(top = 0)), notFound = { EmptyState("Итоги $year", "Итоги за этот год ещё не готовы.", Lucide.Headphones) }) { recap ->
            if (recap.total_seconds <= 0) EmptyState("Итоги $year", "Итоги за этот год ещё не готовы.", Lucide.Headphones)
            else RecapDeck(recap, loader)
        }
    }
}

private sealed class DeckCard {
    data class Body(val content: @Composable () -> Unit) : DeckCard()
    data object Finale : DeckCard()
}

@Composable
private fun RecapDeck(recap: Recap, loader: org.foxgirls.audioranobe.ui.components.Loader<Recap>) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (timeBig, timeUnit) = bigTime(recap.total_seconds)
    val cards = remember(recap) {
        val out = mutableListOf<DeckCard>()
        out += DeckCard.Body {
            Icon(Lucide.Sparkles, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(14.dp))
            Kicker(recap.display_name.ifBlank { recap.username } + (if (recap.username.isNotBlank()) " · @${recap.username}" else ""))
            Hero(recap.period_label)
            Sub("Листайте дальше →")
        }
        out += DeckCard.Body {
            Kicker("Вы слушали")
            Text(timeBig, color = Color.White, fontWeight = FontWeight.Black, fontSize = 84.sp, lineHeight = 88.sp, textAlign = TextAlign.Center)
            Text(timeUnit, color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Sub(
                "${recap.files_count} ${Fmt.plural(recap.files_count, "глава", "главы", "глав")} · за ${recap.active_days} ${Fmt.plural(recap.active_days, "день", "дня", "дней")}" +
                    if (recap.books_finished > 0) " · ${recap.books_finished} ${Fmt.plural(recap.books_finished, "тайтл целиком", "тайтла целиком", "тайтлов целиком")}" else "",
            )
        }
        if (recap.top_titles.isNotEmpty()) out += DeckCard.Body {
            Kicker("Больше всего слушали")
            Spacer(Modifier.height(8.dp))
            recap.top_titles.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(10.dp)).clickable { nav.go(Routes.title(t.slug)) }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text((i + 1).toString(), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                    ArImage(t.cover_url, Modifier.width(34.dp).height(44.dp), fallbackIcon = Lucide.Headphones, shape = RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("${maxOf(1L, Math.round(t.seconds / 60.0))} мин", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
        if (recap.top_narrators.isNotEmpty()) {
            val top = recap.top_narrators.first()
            out += DeckCard.Body {
                Kicker("Ваш чтец")
                Text(
                    top.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 34.sp, textAlign = TextAlign.Center, lineHeight = 38.sp,
                    modifier = Modifier.clickable { nav.go(Routes.narrator(top.slug)) },
                )
                Sub("${minutesLabel(top.seconds)} вместе")
                if (recap.top_narrators.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recap.top_narrators.drop(1).forEach { n ->
                            Text(
                                n.name, color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable { nav.go(Routes.narrator(n.slug)) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (recap.design != null) out += DeckCard.Finale
        out.toList()
    }
    val pager = rememberPagerState { cards.size }
    var finaleHtml by remember(recap) { mutableStateOf(recap.design?.let { fillTemplate(it.html, recap) }) }
    var finaleView by remember { mutableStateOf<WebView?>(null) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            cards.indices.forEach { i ->
                Box(Modifier.width(if (i == pager.currentPage) 18.dp else 7.dp).height(7.dp).clip(CircleShape).background(if (i == pager.currentPage) Ar.accent else Ar.fill08))
            }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp) { i ->
            val card = cards[i]
            val shape = RoundedCornerShape(22.dp)
            Box(
                Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(shape).then(
                    if (card is DeckCard.Finale) Modifier.background(Color(0xFF141416), shape)
                    else Modifier.background(Brush.linearGradient(MONTH_PALETTE[i % MONTH_PALETTE.size]), shape),
                ).border(1.dp, Ar.border, shape),
            ) {
                when (card) {
                    is DeckCard.Body -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) { card.content() }
                    DeckCard.Finale -> Column(Modifier.fillMaxSize()) {
                        val html = finaleHtml
                        if (html != null) {
                            val css = recap.design?.css.orEmpty()
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        setBackgroundColor(0xFF141416.toInt())
                                        settings.javaScriptEnabled = false
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        webViewClient = WebViewClient()
                                        finaleView = this
                                    }
                                },
                                update = { wv ->
                                    val doc = "<!doctype html><html><head><meta name=viewport content='width=380, initial-scale=1'>" +
                                        "<style>html,body{margin:0;background:#141416;color:#e8e8e8;font-family:Inter,system-ui,sans-serif}#host{display:block;width:380px;max-width:100%}$css</style></head>" +
                                        "<body><div id=host>$html</div></body></html>"
                                    wv.loadDataWithBaseURL(Api.siteUrl, doc, "text/html", "utf-8", null)
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End) {
                            ArButton("Скачать", icon = Lucide.Download, small = true, onClick = {
                                scope.launch {
                                    try {
                                        val fresh = Api.get<Recap>("/me/recap/${recap.year ?: recap.period_label}")
                                        loader.set(fresh)
                                        fresh.design?.let { finaleHtml = fillTemplate(it.html, fresh) }
                                        kotlinx.coroutines.delay(400)
                                        val wv = finaleView ?: return@launch
                                        val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                                        wv.draw(android.graphics.Canvas(bmp))
                                        saveToGallery(context, bmp, "recap-${fresh.period_label}")
                                        toast("Сохранено в галерею")
                                    } catch (e: Exception) { toastError(e.msg()) }
                                }
                            })
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArButton("Назад", icon = Lucide.ChevronLeft, kind = ButtonKind.Ghost, enabled = pager.currentPage > 0, small = true, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } })
            ArButton("Дальше", icon = Lucide.ChevronRight, kind = ButtonKind.Primary, enabled = pager.currentPage < cards.size - 1, small = true, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
        }
    }
}

@Composable
private fun Kicker(text: String) = Text(text, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, letterSpacing = 0.5.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun Hero(text: String) = Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 48.sp, textAlign = TextAlign.Center)

@Composable
private fun Sub(text: String) = Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))

// ---------- lib/recapTemplate.ts ----------

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Substitutes {{token}} holes in an admin's HTML template with a user's stats. */
fun fillTemplate(html: String, recap: Recap): String {
    val hours = recap.total_seconds / 3600
    val minutes = recap.total_seconds / 60
    val topTitles = if (recap.top_titles.isEmpty()) "" else "<ol class=\"recap-top-titles\">" + recap.top_titles.mapIndexed { i, t ->
        "<li><span class=\"rank\">${i + 1}</span><span class=\"name\">${esc(t.name)}</span><span class=\"time\">${maxOf(1L, Math.round(t.seconds / 60.0))} мин</span></li>"
    }.joinToString("") + "</ol>"
    val topNarrators = if (recap.top_narrators.isEmpty()) "" else "<ul class=\"recap-top-narrators\">" + recap.top_narrators.joinToString("") { "<li>${esc(it.name)}</li>" } + "</ul>"
    val map = mapOf(
        "display_name" to esc(recap.display_name.ifBlank { recap.username }),
        "handle" to esc(if (recap.username.isNotBlank()) "@${recap.username}" else ""),
        "year" to esc(recap.period_label),
        "total_hours" to hours.toString(),
        "total_minutes" to minutes.toString(),
        "total_seconds" to recap.total_seconds.toString(),
        "files_count" to recap.files_count.toString(),
        "titles_count" to recap.titles_count.toString(),
        "books_finished" to recap.books_finished.toString(),
        "active_days" to recap.active_days.toString(),
        "top_title" to esc(recap.top_titles.firstOrNull()?.name ?: "—"),
        "top_narrator" to esc(recap.top_narrators.firstOrNull()?.name ?: "—"),
        "top_titles" to topTitles,
        "top_narrators" to topNarrators,
    )
    return Regex("\\{\\{\\s*(\\w+)\\s*\\}\\}").replace(html) { m -> map[m.groupValues[1]] ?: m.value }
}

/** Realistic stand-in so the admin editor can preview without real data. */
val SAMPLE_RECAP: Recap by lazy {
    Recap(
        scope = "year", period_label = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString(),
        username = "kitsune", display_name = "Кицунэ", total_seconds = 187L * 3600 + 20 * 60,
        files_count = 642, titles_count = 37, books_finished = 12, active_days = 148,
        top_titles = listOf(
            org.foxgirls.audioranobe.data.RecapTitle("a", "Восхождение в тени", null, 42L * 3600),
            org.foxgirls.audioranobe.data.RecapTitle("b", "Реинкарнация безработного", null, 31L * 3600),
            org.foxgirls.audioranobe.data.RecapTitle("c", "Магическая академия", null, 24L * 3600),
            org.foxgirls.audioranobe.data.RecapTitle("d", "Повелитель тайн", null, 18L * 3600),
            org.foxgirls.audioranobe.data.RecapTitle("e", "Курс на север", null, 9L * 3600),
        ),
        top_narrators = listOf(
            org.foxgirls.audioranobe.data.RecapNarrator(1, "n1", "Алекс Ветров", 61L * 3600),
            org.foxgirls.audioranobe.data.RecapNarrator(2, "n2", "Мария Ли", 40L * 3600),
            org.foxgirls.audioranobe.data.RecapNarrator(3, "n3", "Игорь Ким", 22L * 3600),
        ),
    )
}
