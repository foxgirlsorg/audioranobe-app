package org.foxgirls.audioranobe.ui.screens.title

import org.foxgirls.audioranobe.ui.components.ditheredBackground
import org.foxgirls.audioranobe.ui.components.PlayPauseIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import org.foxgirls.audioranobe.ui.components.Chevron
import org.foxgirls.audioranobe.ui.components.bottomFade
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import org.foxgirls.audioranobe.ui.components.edgeFade
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.Labels
import org.foxgirls.audioranobe.core.Support
import org.foxgirls.audioranobe.data.ChapterRow
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.TitleFull
import org.foxgirls.audioranobe.data.Volume
import org.foxgirls.audioranobe.offline.OfflineStore
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.ui.screens.offline.ChapterDownloadButton
import org.foxgirls.audioranobe.ui.screens.offline.DownloadAsk
import org.foxgirls.audioranobe.ui.screens.offline.DownloadConfirm
import org.foxgirls.audioranobe.ui.screens.offline.DownloadSheet
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.dockScrollAware
import org.foxgirls.audioranobe.ui.swipeTabs
import org.foxgirls.audioranobe.ui.components.AccentBadge
import org.foxgirls.audioranobe.ui.components.AiBadge
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArMarkdown
import org.foxgirls.audioranobe.ui.components.ArTabs
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CardGrid
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.ImageViewer
import org.foxgirls.audioranobe.ui.components.KeyValueRow
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.NarratorAvatar
import org.foxgirls.audioranobe.ui.components.NsfwBadge
import org.foxgirls.audioranobe.ui.components.Pill
import org.foxgirls.audioranobe.ui.components.ProgressTrack
import org.foxgirls.audioranobe.ui.components.StatusBadge
import org.foxgirls.audioranobe.ui.components.TabItem
import org.foxgirls.audioranobe.ui.components.TabsVariant
import org.foxgirls.audioranobe.ui.components.VerifiedBadge
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.components.social.CommentSection
import org.foxgirls.audioranobe.ui.components.social.FavoriteButton
import org.foxgirls.audioranobe.ui.components.social.IllustrationGallery
import org.foxgirls.audioranobe.ui.components.social.LibraryWidget
import org.foxgirls.audioranobe.ui.components.social.RatingBars
import org.foxgirls.audioranobe.ui.components.social.RatingStars
import org.foxgirls.audioranobe.ui.components.social.VolumeCover
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.NotFoundScreen
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class RateRes(val avg_rating: Double? = null, val rating_count: Int = 0, val my_rating: Int? = null)

private fun volumeDuration(v: Volume) = v.liveChapters.sumOf { it.duration_seconds }

/** app/title/[slug]: the app-style single-column title page. */
@Composable
fun TitleScreen(slug: String, initialTab: String?) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val isMod = auth.isMod
    // Without a network a downloaded book opens from its saved copy.
    val loader = rememberLoader(slug, user?.id, keepOnReload = true) {
        try { Api.get<TitleFull>("/titles/${Routes.enc(slug)}") } catch (e: Exception) { OfflineStore.manifestBySlug(slug)?.let { OfflineStore.offlineTitle(it) } ?: throw e }
    }
    val dlStates by OfflineStore.states.collectAsStateWithLifecycle()
    val offlineTitles by OfflineStore.titles.collectAsStateWithLifecycle()
    var downloadSheet by remember { mutableStateOf(false) }
    var dlAsk by remember { mutableStateOf<DownloadAsk?>(null) }
    val scope = rememberCoroutineScope()
    val bottom = LocalBottomInset.current
    var tab by remember { mutableStateOf(initialTab?.takeIf { it in setOf("about", "chapters", "illustrations", "comments", "similar") } ?: "about") }
    var openVols by remember { mutableStateOf(setOf<Int>()) }
    var descOpen by remember { mutableStateOf(false) }
    var ratingOpen by remember { mutableStateOf(false) }
    var selectedVersion by remember { mutableStateOf(0) }
    var coverViewer by remember { mutableStateOf(false) }
    var reNarrating by remember { mutableStateOf<Int?>(null) }
    val current by PlayerController.current.collectAsStateWithLifecycle()
    val playing by PlayerController.playing.collectAsStateWithLifecycle()
    val position by PlayerController.position.collectAsStateWithLifecycle()
    val liveDuration by PlayerController.duration.collectAsStateWithLifecycle()

    when (val s = loader.state) {
        is Load.Loading -> { CenterSpinner(Modifier.fillMaxSize().statusBarsPadding(), 400.dp); return }
        is Load.Err -> { if (s.notFound) NotFoundScreen() else Box(Modifier.statusBarsPadding()) { ErrorState(s.message, { loader.reload() }, "Не удалось загрузить тайтл") }; return }
        is Load.Ok -> {}
    }
    val title = loader.data!!
    LaunchedEffect(title.id, title.selected_version_id) { selectedVersion = title.selected_version_id }

    // A version's standalone volumes list its own chapters; a volume with no main
    // chapters is filled in from a standalone version (selected one first).
    val displayVolumes = remember(title, selectedVersion) {
        val alt = title.alt_chapters[selectedVersion.toString()] ?: emptyList()
        val byKey = alt.associateBy { "${it.volume_id}:${it.number}" }
        fun takeover(volumeId: Int, hasMain: Boolean): Int = when {
            selectedVersion != 0 && title.versions.any { it.id == selectedVersion && volumeId in it.standalone_volume_ids } -> selectedVersion
            hasMain -> 0
            else -> title.versions.firstOrNull { volumeId in it.standalone_volume_ids }?.id ?: 0
        }
        title.volumes.map { v ->
            val own = takeover(v.id, v.chapters.isNotEmpty())
            when {
                own != 0 -> {
                    val from = if (own == selectedVersion) null else title.versions.firstOrNull { it.id == own }?.let { "из «${it.name}»" }
                    v to (title.alt_chapters[own.toString()] ?: emptyList()).filter { it.volume_id == v.id }.sortedBy { it.number }.map { it to from }
                }
                selectedVersion == 0 -> v to v.chapters.map { it to null }
                else -> v to v.chapters.map { c -> val a = byKey["${v.id}:${c.number}"]; if (a != null && a.audio_status == "ready") a.copy(name = c.name, number = c.number, number_end = c.number_end) to null else c to "из основной" }
            }
        }
    }
    val playable = title.volumes.flatMap { it.liveChapters }.filter { it.audio_status == "ready" && it.mod_status == "approved" }
    val resume = remember(playable) {
        if (playable.isEmpty()) null else {
            var idx = -1
            playable.forEachIndexed { i, c -> if ((c.my_position ?: 0.0) > 0) idx = i }
            if (idx == -1) playable[0] to false else {
                val c = playable[idx]
                val finished = c.duration_seconds > 0 && (c.my_position ?: 0.0) >= c.duration_seconds - 5
                if (finished && idx + 1 < playable.size) playable[idx + 1] to true else c to true
            }
        }
    }
    val chaptersTotal = title.volumes.sumOf { v -> v.liveChapters.sumOf { c -> if (c.number_end != null) Math.round(c.number_end - c.number).toInt() + 1 else 1 } }
    val runtime = title.volumes.sumOf { volumeDuration(it) }
    val narrationStatus = listOf("ongoing", "completed", "frozen", "abandoned").firstOrNull { st -> title.narrators.any { it.narration_status == st } } ?: "ongoing"
    val restricted = title.is_restricted
    val illustrations = title.illustrations
    val volumeCovers = title.volumes.filter { it.cover_url != null }.map { VolumeCover(it.id, it.cover_url!!, it.name.ifBlank { "${title.volume_label} ${it.num}" }) }
    val hasIllustrations = illustrations.isNotEmpty() || volumeCovers.isNotEmpty()
    val commentsTotal = title.comments?.total ?: 0

    fun playChapter(ch: ChapterRow) { if (current?.id == ch.id) PlayerController.toggle() else PlayerController.play(ch.id) }

    fun rate(v: Int?) {
        scope.launch {
            try {
                val r = if (v == null) Api.delete<RateRes>("/titles/${title.id}/rating") else Api.put<RateRes>("/titles/${title.id}/rating", buildJsonObject { put("value", v) })
                loader.update { prev ->
                    val dist = prev.rating_distribution.toMutableMap()
                    prev.my_rating?.let { dist[it.toString()] = maxOf(0, (dist[it.toString()] ?: 0) - 1) }
                    r.my_rating?.let { dist[it.toString()] = (dist[it.toString()] ?: 0) + 1 }
                    prev.copy(avg_rating = r.avg_rating, rating_count = r.rating_count, my_rating = r.my_rating, rating_distribution = dist)
                }
            } catch (e: Exception) { toastError(e) }
        }
    }

    val tabs = listOfNotNull(
        TabItem("about", "О тайтле"), TabItem("chapters", "Главы", chaptersTotal.takeIf { it > 0 }),
        if (hasIllustrations) TabItem("illustrations", "Иллюстрации", illustrations.size + volumeCovers.size) else null,
        TabItem("comments", "Комментарии", commentsTotal.takeIf { it > 0 }),
        if (title.similar.isNotEmpty()) TabItem("similar", "Похожие") else null,
    )

    LazyColumn(Modifier.fillMaxSize().dockScrollAware().swipeTabs(tabs.map { it.key }, tab) { tab = it }, contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth()) {
                val bg = title.bg_url ?: title.cover_thumb_url ?: title.cover_url
                ArImage(bg, Modifier.fillMaxWidth().height(360.dp), contentScale = ContentScale.Crop, backdrop = true)
                Box(Modifier.fillMaxWidth().height(360.dp).ditheredBackground(Brush.verticalGradient(listOf(Ar.bg.copy(alpha = 0.35f), Ar.bg.copy(alpha = 0.85f), Ar.bg))))
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text)
                        Spacer(Modifier.weight(1f))
                        if (title.can_edit) {
                            IconBtn(Lucide.Pencil, "Редактировать тайтл", { nav.go(Routes.titleEdit(title.slug)) }, tint = Ar.text)
                            IconBtn(Lucide.ListMusic, "Главы и загрузка аудио", { nav.go(Routes.titleEdit(title.slug, "content")) }, tint = Ar.text)
                        }
                        IconBtn(Lucide.Share2, "Поделиться", { Links.share(nav.context, "${Links.site}/title/${title.slug}", title.name) }, tint = Ar.text)
                        if (playable.isNotEmpty()) IconBtn(Lucide.Download, "Скачать для офлайна", { downloadSheet = true }, tint = if (offlineTitles.any { it.titleId == title.id }) Ar.accent else Ar.text)
                    }
                    if (title.mod_status != "approved") GlassPanel(Modifier.padding(bottom = 10.dp), padding = PaddingValues(10.dp), borderColor = Ar.amber.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.ShieldAlert, null, tint = Ar.amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (title.mod_status == "pending") "Этот тайтл ждёт модерации — пока его видят только его чтецы и модераторы." else "Этот тайтл отклонён модерацией и скрыт из общего каталога.", color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                            StatusBadge(title.mod_status)
                        }
                    }
                    Box(Modifier.align(Alignment.CenterHorizontally).width(170.dp).aspectRatio(2f / 3f)) {
                        ArImage(title.cover_url, Modifier.fillMaxSize().clickable(enabled = !restricted && title.cover_url != null) { coverViewer = true }, fallbackIcon = Lucide.Headphones, blurred = restricted, shape = RoundedCornerShape(12.dp))
                        Row(Modifier.align(Alignment.TopEnd).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { if (title.is_ai) AiBadge(); if (title.is_nsfw) NsfwBadge() }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(title.name, color = Ar.white, fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (title.alt_names.isNotEmpty()) Text(title.alt_names.joinToString(" · "), color = Ar.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (!restricted && resume != null) {
                            val (ch, continued) = resume
                            val isPlaying = current?.id == ch.id && playing
                            ArButton(if (continued) "Продолжить слушать" else "Начать слушать", { playChapter(ch) }, kind = ButtonKind.Primary, icon = if (isPlaying) Lucide.Pause else Lucide.Play)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (user != null && chaptersTotal > 0) FavoriteButton(title.id, title.my_favorite, title.favorites_count, compact = true)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) {
                val banner = title.info_banner
                if (banner != null && banner.enabled && (banner.title.isNotBlank() || banner.text.isNotBlank() || banner.url.isNotBlank())) Banner(Lucide.Info, banner.title, banner.text, banner.url.takeIf { it.isNotBlank() }?.let { "Открыть" to it })
                if (title.narration_pending) Banner(Lucide.Headphones, "Идёт ИИ-озвучка", "Главы появляются по мере готовности — уже озвученные можно слушать, остальные в работе.", null)
                if (title.is_ai) Banner(Lucide.Mic, "Озвучиваете эту книгу?", "Этот тайтл озвучен синтезированным голосом. Если вы чтец и озвучили эту книгу сами — напишите в поддержку, и мы передадим тайтл вам.", "Связаться с поддержкой" to Support.URL)
                Spacer(Modifier.height(6.dp))
                ArTabs(tabs, tab, { tab = it }, variant = TabsVariant.Underline)
                Spacer(Modifier.height(14.dp))
            }
        }
        when (tab) {
            "about" -> item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (title.description.isNotBlank()) Column {
                        Eyebrow("Описание")
                        Spacer(Modifier.height(6.dp))
                        val long = title.description.length > 420
                        Box(Modifier.animateContentSize(tween(350, easing = FastOutSlowInEasing)).then(if (long && !descOpen) Modifier.bottomFade(0.4f) else Modifier)) {
                            ArMarkdown(if (long && !descOpen) Fmt.plainSummary(title.description, 420) else title.description)
                        }
                        if (long) Text(if (descOpen) "Свернуть" else "Развернуть", color = Ar.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { descOpen = !descOpen }.padding(vertical = 6.dp))
                    }
                    if (title.genres.isNotEmpty()) Column {
                        Eyebrow("Теги")
                        Spacer(Modifier.height(8.dp))
                        TagsFlow(title.genres.map { it.name to it.slug }) { nav.go(Routes.catalog(genre = it)) }
                    }
                    GlassPanel {
                        KeyValueRow("Автор", title.author?.name ?: "не указан", onClick = title.author?.let { a -> { nav.go(Routes.author(a.id)) } })
                        if (runtime > 0) KeyValueRow("Длительность", Fmt.duration(runtime))
                        Fmt.date(title.updated_at).takeIf { it.isNotEmpty() }?.let { KeyValueRow("Обновлён", it) }
                        KeyValueRow("Страна", Labels.country[title.country] ?: title.country)
                        KeyValueRow("Тайтл", Labels.releaseStatus[title.release_status] ?: title.release_status)
                        if (title.narrators.isNotEmpty()) KeyValueRow("Озвучка", Labels.narrationStatus[narrationStatus] ?: narrationStatus)
                        KeyValueRow("Просмотров", Fmt.count(title.views_count))
                        if (chaptersTotal > 0) KeyValueRow("Глав", chaptersTotal.toString())
                        if (title.translator.isNotBlank()) KeyValueRow("Переводчик", title.translator)
                    }
                    if (title.narrators.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (n in title.narrators) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ar.fill04).clickable { nav.go(Routes.narrator(n.slug)) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NarratorAvatar(n.name, n.avatar_url, 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(n.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (n.is_verified) { Spacer(Modifier.width(4.dp)); VerifiedBadge(size = 12.dp) }
                                }
                                Text("Чтец", color = Ar.textMuted, fontSize = 12.sp)
                            }
                            n.narration_status?.let { StatusBadge(it) }
                        }
                    }
                    GlassPanel {
                        Eyebrow("Рейтинг", icon = Lucide.Star)
                        Spacer(Modifier.height(8.dp))
                        RatingStars(title.avg_rating, title.rating_count, title.my_rating, if (user != null) ({ rate(it) }) else null)
                        Row(Modifier.clickable { ratingOpen = !ratingOpen }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ratingOpen) "Свернуть распределение" else "Показать распределение", color = Ar.textMuted, fontSize = 12.sp)
                            Chevron(ratingOpen, 14.dp, openTint = Ar.textMuted)
                        }
                        AnimatedVisibility(ratingOpen, enter = expandVertically(tween(350, easing = FastOutSlowInEasing)), exit = shrinkVertically(tween(350, easing = FastOutSlowInEasing))) {
                            RatingBars(title.rating_distribution, Modifier.padding(top = 8.dp))
                        }
                    }
                    LibraryWidget(title.id, title.my_library, { e -> loader.update { it.copy(my_library = e) } })
                }
            }
            "chapters" -> {
                if (chaptersTotal == 0) item { EmptyState("Глав пока нет", "У этой аудиокниги пока нет глав — загляните позже.", Lucide.ListMusic) }
                else {
                    if (title.versions.isNotEmpty()) item {
                        Row(rememberScrollState().let { Modifier.edgeFade(it).horizontalScroll(it) }.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(title.version_name, active = selectedVersion == 0, onClick = { selectedVersion = 0; if (user != null) scope.launch { runCatching { Api.put<Unit>("/titles/${title.id}/version", buildJsonObject { put("version_id", 0) }) } } })
                            for (ver in title.versions) Pill(ver.name, active = selectedVersion == ver.id, onClick = { selectedVersion = ver.id; if (user != null) scope.launch { runCatching { Api.put<Unit>("/titles/${title.id}/version", buildJsonObject { put("version_id", ver.id) }) } } })
                        }
                    }
                    for ((v, chapters) in displayVolumes) {
                        val open = v.id in openVols
                        item(key = "vol${v.id}") {
                            GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), padding = PaddingValues(0.dp)) {
                                Row(Modifier.fillMaxWidth().clickable { openVols = if (open) openVols - v.id else openVols + v.id }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row { Text("${title.volume_label} ${v.num}", color = Ar.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); if (v.name.isNotBlank()) Text("  ${v.name}", color = Ar.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Lucide.ListMusic, null, tint = Ar.textMuted, modifier = Modifier.size(12.dp)); Text(" Глав: ${v.liveChapters.size}", color = Ar.textMuted, fontSize = 12.sp)
                                            val total = volumeDuration(v)
                                            if (total > 0) { Spacer(Modifier.width(10.dp)); Icon(Lucide.Clock, null, tint = Ar.textMuted, modifier = Modifier.size(12.dp)); Text(" ${Fmt.duration(total)}", color = Ar.textMuted, fontSize = 12.sp) }
                                        }
                                    }
                                    val volReady = v.liveChapters.filter { it.audio_status == "ready" }
                                    val volManifest = offlineTitles.firstOrNull { it.titleId == title.id }
                                    val volDone = volReady.isNotEmpty() && volReady.all { volManifest?.chapters?.containsKey(it.id) == true }
                                    if (volReady.isNotEmpty()) IconBtn(if (volDone) Lucide.CircleCheck else Lucide.Download, if (volDone) "Том скачан" else "Скачать том", { if (volDone) OfflineStore.removeVolume(title.id, v) else { dlAsk = DownloadAsk.of(title, volReady.map { it.id }, "${title.volume_label.lowercase()} ${v.num}", "Том ${v.num} добавлен в загрузки") } }, size = 30.dp, iconSize = 14.dp, tint = if (volDone) Ar.accent else Ar.textSecondary)
                                    Chevron(open, 16.dp)
                                }
                                AnimatedVisibility(open, enter = expandVertically(tween(350, easing = FastOutSlowInEasing)), exit = shrinkVertically(tween(350, easing = FastOutSlowInEasing))) {
                                    Column {
                                        if (chapters.isEmpty()) Text("В этом томе пока нет глав.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                                        for ((ch, sourceLabel) in chapters) {
                                            val isCurrent = current?.id == ch.id
                                            val chPlayable = ch.audio_status == "ready"
                                            val staticPct = if (ch.duration_seconds > 0) ((ch.my_position ?: 0.0) / ch.duration_seconds).toFloat() else 0f
                                            Column(Modifier.fillMaxWidth().background(if (isCurrent) Ar.accentSoft.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)) {
                                                Row(Modifier.fillMaxWidth().clickable { nav.go(Routes.chapter(ch.id)) }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    if (chPlayable) Box(Modifier.size(30.dp).clip(CircleShape).background(if (isCurrent) Ar.accent else Ar.fill08).clickable { playChapter(ch) }, contentAlignment = Alignment.Center) {
                                                        PlayPauseIcon(isCurrent && playing, if (isCurrent) Ar.accentOn else Ar.text, 13.dp, 0.dp)
                                                    } else Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Icon(Lucide.Headphones, "Аудио ещё не готово", tint = Ar.textMuted, modifier = Modifier.size(13.dp)) }
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(Fmt.chapterNumber(ch.number, ch.number_end), color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(Fmt.chapterLabel(ch.number, ch.number_end, ch.name), color = if (isCurrent) Ar.accentHover else Ar.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            if (sourceLabel != null) Text(sourceLabel, color = Ar.textMuted, fontSize = 10.sp, maxLines = 1)
                                                            if (ch.narrators.isNotEmpty()) Text(ch.narrators.joinToString(", ") { it.name }, color = Ar.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            if (ch.mod_status != "approved") StatusBadge(ch.mod_status)
                                                            if (ch.audio_status != "ready" && (title.can_edit || ch.audio_status != "none")) StatusBadge(ch.audio_status)
                                                        }
                                                    }
                                                    if (ch.duration_seconds > 0) Text(Fmt.duration(ch.duration_seconds), color = Ar.textMuted, fontSize = 11.sp)
                                                    if (isMod && title.is_imported && title.is_ai) IconBtn(Lucide.RefreshCw, "Переозвучить главу", {
                                                        if (reNarrating != null) return@IconBtn
                                                        reNarrating = ch.id
                                                        scope.launch { try { Api.post<Unit>("/mod/chapters/${ch.id}/re-narrate"); toast("Глава ${Fmt.trimNum(ch.number)} отправлена на переозвучку") } catch (e: Exception) { toastError(e) } finally { reNarrating = null } }
                                                    }, size = 30.dp, iconSize = 12.dp)
                                                    if (chPlayable) ChapterDownloadButton(title, ch, dlStates[ch.id], offlineTitles.any { it.chapters.containsKey(ch.id) })
                                                }
                                                val pct = if (isCurrent) { val d = if (liveDuration > 0) liveDuration else ch.duration_seconds; if (d > 0) (position / d).toFloat() else 0f } else staticPct
                                                if (pct > 0f) ProgressTrack(pct, Modifier.padding(horizontal = 10.dp).padding(bottom = 6.dp), height = 2.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "illustrations" -> item { Box(Modifier.padding(horizontal = 16.dp)) { IllustrationGallery(illustrations, title.volume_label, volumeCovers, title.volume_label_plural) } }
            "comments" -> item { Box(Modifier.padding(horizontal = 16.dp)) { CommentSection("title", title.id, title.comments, showHeading = false) } }
            "similar" -> item { Box(Modifier.padding(horizontal = 16.dp)) { CardGrid(title.similar) } }
        }
    }
    DownloadSheet(downloadSheet, { downloadSheet = false }, title, offlineTitles.firstOrNull { it.titleId == title.id }, dlStates)
    DownloadConfirm(dlAsk) { dlAsk = null }
    ImageViewer(coverViewer, listOfNotNull(title.cover_url) + title.volumes.mapNotNull { it.cover_url }.filter { it != title.cover_url }, 0, listOf(title.name) + title.volumes.filter { it.cover_url != null && it.cover_url != title.cover_url }.map { it.name.ifBlank { "${title.volume_label} ${it.num}" } }) { coverViewer = false }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, action: Pair<String, String>?) {
    val nav = LocalNav.current
    GlassPanel(Modifier.padding(bottom = 10.dp), padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Ar.accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (title.isNotBlank()) Text(title, color = Ar.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (text.isNotBlank()) Text(text, color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                if (action != null) ArButton(action.first, { Links.open(nav, action.second) }, small = true, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsFlow(tags: List<Pair<String, String>>, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((name, slug) in tags) Pill(name, onClick = { onClick(slug) })
    }
}

@Composable
fun AccentLabel(text: String) = AccentBadge(text)
