package org.foxgirls.audioranobe.ui.screens.offline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.ChapterRow
import org.foxgirls.audioranobe.data.TitleFull
import org.foxgirls.audioranobe.offline.DlState
import org.foxgirls.audioranobe.offline.OfflineManifest
import org.foxgirls.audioranobe.offline.OfflineStore
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.LocalShell
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.components.ProgressTrack
import org.foxgirls.audioranobe.ui.components.SectionTitle
import org.foxgirls.audioranobe.ui.components.Spinner
import org.foxgirls.audioranobe.ui.dockScrollAware
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.TopBar
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast

/** The dock's "Загрузки" tab: downloaded books, the active queue, and the offline progress waiting for a sync. */
@Composable
fun OfflineScreen() {
    val nav = LocalNav.current
    val shell = LocalShell.current
    val titles by OfflineStore.titles.collectAsStateWithLifecycle()
    val queue by OfflineStore.queue.collectAsStateWithLifecycle()
    val states by OfflineStore.states.collectAsStateWithLifecycle()
    val pending by OfflineStore.pending.collectAsStateWithLifecycle()
    val online by OfflineStore.online.collectAsStateWithLifecycle()
    val syncing by OfflineStore.syncing.collectAsStateWithLifecycle()
    val bottom = LocalBottomInset.current
    var toDelete by remember { mutableStateOf<OfflineManifest?>(null) }

    LazyColumn(Modifier.fillMaxSize().dockScrollAware().statusBarsPadding(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                TopBar(onSearch = { shell.searchOpen = true }, horizontalPadding = 0.dp)
                Eyebrow("Слушайте без сети")
                SectionTitle("Ваши", "загрузки", Modifier.padding(top = 4.dp, bottom = 10.dp), size = 24)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (online) Lucide.Wifi else Lucide.WifiOff, null, tint = if (online) Ar.ok else Ar.amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (online) "Онлайн" else "Нет сети — доступны только скачанные главы", color = Ar.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(Fmt.bytes(OfflineStore.totalBytes()), color = Ar.textMuted, fontSize = 12.sp)
                }
                AnimatedVisibility(pending.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    GlassPanel(Modifier.fillMaxWidth().padding(top = 10.dp), borderColor = Ar.amber.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (syncing) Spinner(size = 16.dp) else Icon(Lucide.RefreshCw, null, tint = Ar.amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Прогресс ${pending.size} ${Fmt.plural(pending.size, "главы", "глав", "глав")} ждёт синхронизации", color = Ar.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (online && !syncing) ArButton("Синхронизировать", { OfflineStore.syncProgress() }, small = true)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        if (queue.isNotEmpty()) item {
            GlassPanel(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Загружается", color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    ArButton("Отменить все", { OfflineStore.cancelAll() }, kind = ButtonKind.Ghost, small = true)
                }
                queue.take(8).forEach { t ->
                    val st = states[t.chapterId]
                    Column(Modifier.padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.titleName, color = Ar.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.label, color = Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(when (st) { is DlState.Running -> "${(st.fraction * 100).toInt()}%"; is DlState.Failed -> "ошибка"; else -> "в очереди" }, color = if (st is DlState.Failed) Ar.danger else Ar.textMuted, fontSize = 12.sp)
                            IconBtn(Lucide.X, "Отменить", { OfflineStore.cancel(t.chapterId) }, size = 30.dp, iconSize = 14.dp)
                        }
                        val f by animateFloatAsState((st as? DlState.Running)?.fraction ?: 0f, tween(300), label = "dl")
                        ProgressTrack(f, Modifier.padding(top = 4.dp))
                    }
                }
                if (queue.size > 8) Text("… и ещё ${queue.size - 8}", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (titles.isEmpty()) item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                EmptyState("Пока ничего не скачано", icon = Lucide.Download, action = { ArButton("В каталог", { nav.tab(Routes.catalog()) }, kind = ButtonKind.Primary, icon = Lucide.LibraryBig) })
            }
        } else items(titles, key = { it.titleId }) { m ->
            val done = m.chapters.size
            val all = m.title.volumes.flatMap { it.liveChapters }.filter { it.audio_status == "ready" }
            val queued = queue.count { it.titleId == m.titleId }
            val t = OfflineStore.offlineTitle(m)
            val listened = all.count { c -> (c.my_position ?: 0.0) > 0 }
            GlassPanel(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), padding = PaddingValues(10.dp), onClick = { nav.go(Routes.title(m.slug)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArImage(m.title.cover_thumb_url ?: m.coverUrl, Modifier.width(56.dp).height(80.dp), fallbackIcon = Lucide.Headphones, shape = RoundedCornerShape(8.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull("$done из ${all.size} ${Fmt.plural(all.size, "главы", "глав", "глав")}", Fmt.bytes(m.bytes), if (queued > 0) "ещё $queued в очереди" else null).joinToString(" · "),
                            color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                        )
                        if (listened > 0) Text("прослушано глав: $listened", color = Ar.textSecondary, fontSize = 12.sp)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val resume = all.lastOrNull { c -> (c.my_position ?: 0.0) > 0 && m.chapters.containsKey(c.id) } ?: all.firstOrNull { m.chapters.containsKey(it.id) }
                            if (resume != null) ArButton("Слушать", { PlayerController.play(resume.id); PlayerController.setFull(true) }, kind = ButtonKind.Primary, icon = Lucide.Play, small = true)
                            if (done < all.size) ArButton("Докачать", { OfflineStore.download(t); toast("Остальные главы добавлены в загрузки") }, icon = Lucide.Download, small = true)
                        }
                    }
                    IconBtn(Lucide.Trash2, "Удалить загрузки", { toDelete = m }, size = 34.dp, iconSize = 16.dp, tint = Ar.danger)
                }
                if (all.isNotEmpty()) ProgressTrack(done.toFloat() / all.size, Modifier.padding(top = 8.dp), height = 3.dp)
            }
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = { toDelete?.let { OfflineStore.removeTitle(it.titleId); toast("Загрузки удалены") }; toDelete = null }, title = "Удалить загрузки", body = "Удалить с устройства все скачанные главы книги «${toDelete?.name}»? Прогресс прослушивания сохранится.", danger = true, confirmLabel = "Удалить")
}

/** The per-chapter control in the title's chapter list: download / progress / done (tap to remove). */
@Composable
fun ChapterDownloadButton(title: TitleFull, ch: ChapterRow, state: DlState?, downloaded: Boolean) {
    when {
        downloaded -> IconBtn(Lucide.CircleCheck, "Скачано — удалить", { OfflineStore.removeChapter(ch.id) }, size = 30.dp, iconSize = 14.dp, tint = Ar.accent)
        state is DlState.Running -> Box(Modifier.size(30.dp).clickable { OfflineStore.cancel(ch.id) }, contentAlignment = Alignment.Center) {
            Text("${(state.fraction * 100).toInt()}%", color = Ar.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        state is DlState.Queued -> IconBtn(Lucide.Clock, "В очереди — отменить", { OfflineStore.cancel(ch.id) }, size = 30.dp, iconSize = 14.dp, tint = Ar.textMuted)
        state is DlState.Failed -> IconBtn(Lucide.TriangleAlert, state.message, { OfflineStore.downloadChapter(title, ch) }, size = 30.dp, iconSize = 14.dp, tint = Ar.danger)
        else -> IconBtn(Lucide.Download, "Скачать главу", { OfflineStore.downloadChapter(title, ch); toast("Глава добавлена в загрузки") }, size = 30.dp, iconSize = 14.dp)
    }
}

/** The title header's download sheet: whole book, per volume, or remove everything. */
@Composable
fun DownloadSheet(open: Boolean, onClose: () -> Unit, title: TitleFull, manifest: OfflineManifest?, states: Map<Int, DlState>) {
    val all = title.volumes.flatMap { it.liveChapters }.filter { it.audio_status == "ready" }
    val done = all.count { manifest?.chapters?.containsKey(it.id) == true }
    val runtime = all.sumOf { it.duration_seconds }
    ArSheet(open, onClose, "Скачать для офлайна") {
        Text(
            "Книга сохраняется вместе с описанием, обложками и иллюстрациями, чтобы её можно было открыть и слушать без сети. Прогресс прослушивания синхронизируется, когда появится соединение.",
            color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        MenuRow(Lucide.BookHeadphones, if (done == all.size) "Вся книга скачана" else "Скачать всю книгу", { OfflineStore.download(title); toast("Книга добавлена в загрузки"); onClose() }, tint = Ar.accent) {
            Text("${all.size} гл. · ${Fmt.duration(runtime)}", color = Ar.textMuted, fontSize = 12.sp)
        }
        HairlineDivider(Modifier.padding(vertical = 6.dp))
        title.volumes.forEach { v ->
            val ready = v.liveChapters.filter { it.audio_status == "ready" }
            if (ready.isEmpty()) return@forEach
            val vDone = ready.count { manifest?.chapters?.containsKey(it.id) == true }
            val vQueued = ready.count { states[it.id] != null }
            MenuRow(if (vDone == ready.size) Lucide.CircleCheck else Lucide.Download, "${title.volume_label} ${v.number}" + (if (v.name.isNotBlank()) " — ${v.name}" else ""), {
                if (vDone == ready.size) OfflineStore.removeVolume(title.id, v) else { OfflineStore.downloadVolume(title, v); toast("Том ${v.number} добавлен в загрузки") }
            }, tint = if (vDone == ready.size) Ar.accent else Ar.textSecondary) {
                Text(if (vDone == ready.size) "скачан" else if (vQueued > 0) "$vDone/${ready.size} · загрузка" else "$vDone/${ready.size}", color = Ar.textMuted, fontSize = 12.sp)
            }
        }
        if (manifest != null && manifest.chapters.isNotEmpty()) {
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            MenuRow(Lucide.Trash2, "Удалить с устройства", { OfflineStore.removeTitle(title.id); toast("Загрузки удалены"); onClose() }, tint = Ar.danger) {
                Text(Fmt.bytes(manifest.bytes), color = Ar.textMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
