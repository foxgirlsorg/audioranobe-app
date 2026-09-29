package org.foxgirls.audioranobe.ui.player

import org.foxgirls.audioranobe.ui.components.PlayPauseIcon
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.player.Sleep
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.ImageViewer
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.swipeDownToDismiss

private val SLEEP_OPTIONS = listOf<Pair<String, Sleep?>>(
    "Выкл." to null, "15 минут" to Sleep.Minutes(15), "30 минут" to Sleep.Minutes(30), "45 минут" to Sleep.Minutes(45), "60 минут" to Sleep.Minutes(60), "До конца главы" to Sleep.Chapter,
)

/** components/Player full-screen stage. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayer() {
    val nav = LocalNav.current
    val current by PlayerController.current.collectAsStateWithLifecycle()
    val playing by PlayerController.playing.collectAsStateWithLifecycle()
    val position by PlayerController.position.collectAsStateWithLifecycle()
    val buffered by PlayerController.buffered.collectAsStateWithLifecycle()
    val duration by PlayerController.duration.collectAsStateWithLifecycle()
    val rate by PlayerController.rate.collectAsStateWithLifecycle()
    val sleep by PlayerController.sleep.collectAsStateWithLifecycle()
    val sleepRemaining by PlayerController.sleepRemaining.collectAsStateWithLifecycle()
    val loading by PlayerController.loading.collectAsStateWithLifecycle()
    val cur = current ?: return
    var scrub by remember { mutableStateOf<Float?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<Int?>(null) }

    val shown = scrub?.toDouble() ?: position
    val max = if (duration > 0) duration else maxOf(shown, 1.0)
    val chapterLabel = "${cur.title.volume_label} ${cur.volume.number} · Гл. ${Fmt.chapterNumber(cur.number, cur.number_end)}" + (if (cur.name.isNotBlank()) " — ${cur.name}" else "")
    val revealed = duration > 0 && position >= duration / 2

    Box(Modifier.fillMaxSize().swipeDownToDismiss { PlayerController.setFull(false) }.background(Ar.bg)) {
        ArImage(cur.coverUrl, Modifier.fillMaxSize().blur(40.dp), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ar.bg.copy(alpha = 0.55f), Ar.bg.copy(alpha = 0.92f), Ar.bg))))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Lucide.ChevronDown, "Свернуть плеер", { PlayerController.setFull(false) }, tint = Ar.text)
                Spacer(Modifier.weight(1f))
                IconBtn(Lucide.Share2, "Поделиться с этого места", {
                    val at = shown.toInt()
                    Links.share(nav.context, "${Links.site}/chapter/${cur.id}?t=$at", cur.title.name)
                    toast("Ссылка на ${Fmt.duration(at)} готова")
                }, tint = Ar.text)
                IconBtn(Lucide.X, "Закрыть плеер", { PlayerController.stop() }, tint = Ar.text)
            }

            // Artwork: chapter illustrations if present, else the cover; centered in the free space.
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                val ills = cur.illustrations
                if (ills.isNotEmpty()) {
                    val pager = rememberPagerState(pageCount = { ills.size })
                    LaunchedEffect(cur.id) { pager.scrollToPage(0) }
                    HorizontalPager(pager, Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(18.dp))) { i ->
                        val ill = ills[i]
                        Box(Modifier.fillMaxSize().clickable { viewer = i }) {
                            ArImage(ill.thumb_url, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop)
                            ArImage(ill.url, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, blurred = ill.blurred && !revealed)
                            if (ill.caption.isNotBlank()) {
                                Text(ill.caption, color = Ar.white, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (ills.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        for (i in ills.indices) Box(Modifier.padding(3.dp).size(6.dp).background(if (i == pager.currentPage) Ar.accent else Ar.borderStrong, CircleShape))
                    }
                    ImageViewer(viewer != null, ills.map { it.url }, viewer ?: 0, captions = ills.map { it.caption }) { viewer = null }
                } else {
                    ArImage(cur.coverUrl, Modifier.width(minOf((LocalConfiguration.current.screenWidthDp - 40).dp * 0.78f, 400.dp)).align(Alignment.CenterHorizontally).weight(1f, fill = false).aspectRatio(2f / 3f, matchHeightConstraintsFirst = true).clickable { PlayerController.setFull(false); nav.go(Routes.title(cur.title.slug)) }, fallbackIcon = Lucide.Music, shape = RoundedCornerShape(16.dp))
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(cur.title.name, color = Ar.white, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { PlayerController.setFull(false); nav.go(Routes.title(cur.title.slug)) })
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                Text(chapterLabel, color = Ar.textSecondary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, repeatDelayMillis = 0, spacing = MarqueeSpacing(39.dp)))
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth()) {
                val bufFrac = (buffered / max).toFloat().coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth(bufFrac).height(4.dp).align(Alignment.CenterStart).padding(horizontal = 0.dp).background(Ar.fill08, CircleShape))
                Slider(
                    value = shown.toFloat().coerceIn(0f, max.toFloat()),
                    onValueChange = { scrub = it },
                    onValueChangeFinished = { scrub?.let { PlayerController.seek(it.toDouble()) }; scrub = null },
                    valueRange = 0f..max.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Ar.accent, activeTrackColor = Ar.accent, inactiveTrackColor = Ar.fill08),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Fmt.duration(shown), color = Ar.textMuted, fontSize = 11.sp)
                Text(Fmt.duration(duration), color = Ar.textMuted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.widthIn(max = 380.dp).fillMaxWidth().align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Lucide.SkipBack, "Предыдущая глава", { PlayerController.prev() }, tint = Ar.text, size = 48.dp, iconSize = 24.dp)
                IconBtn(Lucide.RotateCcw, "Назад на 10 секунд", { PlayerController.skip(-10.0) }, tint = Ar.text, size = 48.dp, iconSize = 26.dp)
                Box(Modifier.size(72.dp).clip(CircleShape).background(Ar.accent).clickable { PlayerController.toggle() }, contentAlignment = Alignment.Center) {
                    if (loading && !playing) org.foxgirls.audioranobe.ui.components.Spinner(size = 28.dp)
                    else PlayPauseIcon(playing, Ar.accentOn, 32.dp, 3.dp)
                }
                IconBtn(Lucide.RotateCw, "Вперёд на 10 секунд", { PlayerController.skip(10.0) }, tint = Ar.text, size = 48.dp, iconSize = 26.dp)
                IconBtn(Lucide.SkipForward, "Следующая глава", { PlayerController.next() }, tint = Ar.text, size = 48.dp, iconSize = 24.dp, enabled = cur.next_id != null)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Text("${Fmt.trimNum(rate.toDouble())}x", color = if (rate != 1f) Ar.accent else Ar.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).clickable { menu = "rate" }.padding(horizontal = 14.dp, vertical = 8.dp))
                Row(Modifier.clip(CircleShape).clickable { menu = "sleep" }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Moon, "Таймер сна", tint = if (sleep != null) Ar.accent else Ar.textSecondary, modifier = Modifier.size(20.dp))
                    if (sleep != null && sleepRemaining != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(Fmt.duration(sleepRemaining), color = Ar.accent, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    ArSheet(menu == "rate", { menu = null }, "Скорость") {
        for (r in PlayerController.RATES) MenuRow(null, "${Fmt.trimNum(r.toDouble())}x", { PlayerController.setRate(r); menu = null }, tint = Ar.text) {
            if (r == rate) Icon(Lucide.Check, null, tint = Ar.accent, modifier = Modifier.size(15.dp))
        }
    }
    ArSheet(menu == "sleep", { menu = null }, "Таймер сна") {
        for ((label, v) in SLEEP_OPTIONS) MenuRow(null, label, { PlayerController.setSleep(v); menu = null }) {
            if (v == sleep) Icon(Lucide.Check, null, tint = Ar.accent, modifier = Modifier.size(15.dp))
        }
        if (sleep != null && sleepRemaining != null) Text("Осталось ${Fmt.duration(sleepRemaining)}", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
    }
}
