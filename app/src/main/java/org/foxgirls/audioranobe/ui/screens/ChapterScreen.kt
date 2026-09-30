package org.foxgirls.audioranobe.ui.screens

import org.foxgirls.audioranobe.ui.components.PlayPauseIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.ChapterPlay
import org.foxgirls.audioranobe.offline.OfflineStore
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.OutlineChip
import org.foxgirls.audioranobe.ui.components.ProgressTrack
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar

/** app/chapter/[id] */
@Composable
fun ChapterScreen(id: Int, startAt: Double?) {
    val nav = LocalNav.current
    val loader = rememberLoader(id) { try { Api.get<ChapterPlay>("/chapters/$id") } catch (e: Exception) { OfflineStore.chapterPlay(id) ?: throw e } }
    val current by PlayerController.current.collectAsStateWithLifecycle()
    val playing by PlayerController.playing.collectAsStateWithLifecycle()
    val livePos by PlayerController.position.collectAsStateWithLifecycle()
    val liveDur by PlayerController.duration.collectAsStateWithLifecycle()
    val bottom = LocalBottomInset.current

    LaunchedEffect(loader.data?.id, startAt) {
        val ch = loader.data ?: return@LaunchedEffect
        if (startAt != null && startAt > 0) { PlayerController.play(ch.id, startAt); PlayerController.setFull(true) }
    }

    when (val s = loader.state) {
        is Load.Loading -> { CenterSpinner(Modifier.fillMaxSize().statusBarsPadding(), 300.dp); return }
        is Load.Err -> {
            if (s.notFound) NotFoundScreen("Глава недоступна. Попробуйте позже.") else Box(Modifier.statusBarsPadding()) { ErrorState(s.message, { loader.reload() }, "Не удалось загрузить главу") }
            return
        }
        is Load.Ok -> {}
    }
    val ch = loader.data!!
    val isCurrent = current?.id == ch.id
    val isPlaying = isCurrent && playing
    val pos = if (isCurrent) livePos else (ch.my_position ?: 0.0)
    val dur = if (isCurrent && liveDur > 0) liveDur else ch.duration_seconds
    val pct = if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f
    val chapterLabel = ch.name.ifBlank { "Глава ${Fmt.trimNum(ch.number)}" }
    val volumeLabel = "${ch.title.volume_label} ${ch.volume.number}" + (if (ch.volume.name.isNotBlank()) " — ${ch.volume.name}" else "")

    Box(Modifier.fillMaxSize()) {
        ArImage(ch.title.cover_url, Modifier.fillMaxWidth().height(320.dp), contentScale = ContentScale.Crop, backdrop = true)
        Box(Modifier.fillMaxWidth().height(320.dp).background(Brush.verticalGradient(listOf(Ar.bg.copy(alpha = 0.4f), Ar.bg))))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp).padding(bottom = bottom + 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text)
                Spacer(Modifier.weight(1f))
                IconBtn(Lucide.Share2, "Поделиться", { Links.share(nav.context, "${Links.site}/chapter/${ch.id}", ch.title.name) }, tint = Ar.text)
            }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ch.title.name, color = Ar.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { nav.go(Routes.title(ch.title.slug)) })
                Text(" / $volumeLabel", color = Ar.textMuted, fontSize = 12.sp)
            }
            GlassPanel(padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArImage(ch.coverUrl, Modifier.size(96.dp, 140.dp).clickable { nav.go(Routes.title(ch.title.slug)) }, fallbackIcon = Lucide.Headphones, shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.height(14.dp))
                    Eyebrow("$volumeLabel · Глава ${Fmt.trimNum(ch.number)}")
                    Text(chapterLabel, color = Ar.white, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.size(76.dp).clip(CircleShape).background(if (isPlaying) Ar.accent else Ar.accentSoft).clickable { if (isCurrent) PlayerController.toggle() else PlayerController.play(ch.id) }, contentAlignment = Alignment.Center) {
                        PlayPauseIcon(isPlaying, if (isPlaying) Ar.accentOn else Ar.accent, 32.dp, 3.dp)
                    }
                    Text(if (isPlaying) "Играет" else if (isCurrent) "На паузе" else "Слушать главу", color = Ar.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(16.dp))
                    ProgressTrack(pct, height = 4.dp)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Fmt.duration(pos), color = Ar.textMuted, fontSize = 11.sp); Text(Fmt.duration(dur), color = Ar.textMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlineChip("⏱ ${Fmt.duration(ch.duration_seconds)}")
                        if (!isCurrent && (ch.my_position ?: 0.0) > 0) OutlineChip("Остановились на ${Fmt.duration(ch.my_position)}")
                        OutlineChip("Скачать", onClick = { Links.external(nav.context, ch.audio_url) }, color = Ar.accent)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArButton("Предыдущая", { ch.prev_id?.let { nav.replace(Routes.chapter(it)) } }, Modifier.weight(1f), icon = Lucide.ChevronLeft, enabled = ch.prev_id != null)
                        ArButton("Следующая", { ch.next_id?.let { nav.replace(Routes.chapter(it)) } }, Modifier.weight(1f), icon = Lucide.ChevronRight, enabled = ch.next_id != null)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
