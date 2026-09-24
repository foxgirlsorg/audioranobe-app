package com.audioranobe.app.ui.screens.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.Fmt
import com.audioranobe.app.data.Author
import com.audioranobe.app.data.BannedWord
import com.audioranobe.app.data.Comment
import com.audioranobe.app.data.LocalAuth
import com.audioranobe.app.data.ModQueueCounts
import com.audioranobe.app.data.ModQueuePage
import com.audioranobe.app.data.ModRequest
import com.audioranobe.app.data.Paginated
import com.audioranobe.app.data.Report
import com.audioranobe.app.data.ReservedUsername
import com.audioranobe.app.data.ReviewQueueItem
import com.audioranobe.app.data.TitleFull
import com.audioranobe.app.data.TrashEntry
import com.audioranobe.app.data.UserBrief
import com.audioranobe.app.ui.components.ArButton
import com.audioranobe.app.ui.components.ArImage
import com.audioranobe.app.ui.components.ArModal
import com.audioranobe.app.ui.components.ArTabs
import com.audioranobe.app.ui.components.ArTextField
import com.audioranobe.app.ui.components.ArToggle
import com.audioranobe.app.ui.components.ButtonKind
import com.audioranobe.app.ui.components.CenterSpinner
import com.audioranobe.app.ui.components.ConfirmDialog
import com.audioranobe.app.ui.components.EmptyState
import com.audioranobe.app.ui.components.ErrorState
import com.audioranobe.app.ui.components.GlassPanel
import com.audioranobe.app.ui.components.HairlineDivider
import com.audioranobe.app.ui.components.IconBtn
import com.audioranobe.app.ui.components.InfiniteScrollTrigger
import com.audioranobe.app.ui.components.Load
import com.audioranobe.app.ui.components.Loader
import com.audioranobe.app.ui.components.SelectMenu
import com.audioranobe.app.ui.components.SelectOption
import com.audioranobe.app.ui.components.StatusBadge
import com.audioranobe.app.ui.components.TabItem
import com.audioranobe.app.ui.components.TabsVariant
import com.audioranobe.app.ui.components.UserAvatar
import com.audioranobe.app.ui.components.pickers.AuthorPicker
import com.audioranobe.app.ui.components.pickers.GenrePicker
import com.audioranobe.app.ui.components.rememberImageCropper
import com.audioranobe.app.ui.components.rememberLoader
import com.audioranobe.app.ui.components.rememberPagedList
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.nav.Links
import com.audioranobe.app.ui.nav.LocalNav
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.screens.catalog.Pagination
import com.audioranobe.app.ui.screens.me.ACTION_LABELS
import com.audioranobe.app.ui.screens.me.ENTITY_LABELS
import com.audioranobe.app.ui.screens.me.jsonValueText
import com.audioranobe.app.ui.screens.me.str
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.toast.toast
import com.audioranobe.app.ui.toast.toastError
import com.audioranobe.app.data.Labels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject

/** Renders a paged loader in the standard three states. */
@Composable
fun <T> PagedBox(loader: Loader<Paginated<T>>, empty: @Composable () -> Unit, errorTitle: String = "Не удалось загрузить", onPage: (Int) -> Unit, content: @Composable (List<T>) -> Unit) {
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner()
        is Load.Err -> ErrorState(s.message, { loader.reload() }, title = errorTitle)
        is Load.Ok -> if (s.data.items.isEmpty()) empty() else { content(s.data.items); Pagination(s.data.page, s.data.total, s.data.per_page, onPage) }
    }
}

fun <T> Loader<Paginated<T>>.drop(match: (T) -> Boolean) = update { p -> p.copy(items = p.items.filter { !match(it) }, total = maxOf(0, p.total - 1)) }
fun <T> Loader<Paginated<T>>.patchItems(f: (T) -> T) = update { p -> p.copy(items = p.items.map(f)) }

// ---------- app/mod/queue ----------

private data class QueueTab(val key: String, val label: String, val params: Map<String, String>)
private val QUEUE_TABS = listOf(
    QueueTab("all", "Все", emptyMap()), QueueTab("transfer", "Передачи", mapOf("kind" to "transfer")),
    QueueTab("narrator", "Чтецы", mapOf("type" to "narrator", "kind" to "content")), QueueTab("title", "Тайтлы", mapOf("type" to "title", "kind" to "content")),
    QueueTab("chapter", "Главы", mapOf("type" to "chapter", "kind" to "content")), QueueTab("author", "Авторы", mapOf("type" to "author", "kind" to "content")),
)

private fun ModQueueCounts.of(key: String) = when (key) { "all" -> all; "transfer" -> transfer; "narrator" -> narrator; "title" -> title; "chapter" -> chapter; "author" -> author; else -> 0 }

private fun entityName(r: ModRequest): String {
    r.entity?.str("name")?.takeIf { it.isNotBlank() }?.let { return it }
    r.payload.str("name")?.takeIf { it.isNotBlank() }?.let { return it }
    val t = ENTITY_LABELS[r.entity_type] ?: r.entity_type
    return if (r.entity_id != null) "$t #${r.entity_id}" else t
}

private fun entityRoute(r: ModRequest): String? = when (r.entity_type) {
    "chapter" -> r.entity_id?.let { Routes.chapter(it) }
    "author" -> r.entity_id?.let { Routes.author(it) }
    else -> r.entity?.str("slug")?.takeIf { it.isNotBlank() }?.let { if (r.entity_type == "title") Routes.title(it) else Routes.narrator(it) }
}

@Composable
fun ModQueuePage(initialTab: String?) {
    var tab by remember { mutableStateOf(initialTab?.takeIf { t -> QUEUE_TABS.any { it.key == t } } ?: "all") }
    var counts by remember { mutableStateOf<ModQueueCounts?>(null) }
    val list = rememberPagedList(tab) { page ->
        val p = QUEUE_TABS.first { it.key == tab }.params
        val d = Api.get<ModQueuePage>("/mod/queue", p + mapOf("page" to page))
        counts = d.counts
        Paginated(d.items, d.page, d.per_page, d.total)
    }
    val scope = rememberCoroutineScope()
    ArTabs(QUEUE_TABS.map { TabItem(it.key, it.label, counts?.of(it.key) ?: 0) }, tab, { tab = it }, Modifier.padding(bottom = 12.dp), variant = TabsVariant.Underline)
    val items = list.items
    when {
        list.error.isNotEmpty() -> ErrorState(list.error, { scope.launch { list.load() } })
        list.loading || items == null -> CenterSpinner()
        items.isEmpty() -> EmptyState("Очередь пуста", "В этой категории нет ожидающих заявок.", Lucide.Inbox)
        else -> {
            items.forEach { r -> RequestCard(r) { id ->
                val done = items.firstOrNull { it.id == id }
                list.remove { it.id == id }
                if (done != null) counts = counts?.let { c ->
                    val key = if (done.action == "transfer") "transfer" else done.entity_type
                    c.copy(all = c.all - 1, transfer = if (key == "transfer") c.transfer - 1 else c.transfer, narrator = if (key == "narrator") c.narrator - 1 else c.narrator,
                        title = if (key == "title") c.title - 1 else c.title, chapter = if (key == "chapter") c.chapter - 1 else c.chapter, author = if (key == "author") c.author - 1 else c.author)
                }
            } }
            InfiniteScrollTrigger(list)
        }
    }
}

@Composable
private fun UserPill(u: UserBrief) {
    val nav = LocalNav.current
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Ar.fill04).clickable { nav.go(Routes.user(u.id)) }.padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(u.username, u.avatar_url, 22.dp, thumbUrl = u.avatar_thumb_url)
        Spacer(Modifier.width(6.dp))
        Text(u.display_name.ifBlank { u.username }, color = Ar.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(" @${u.username}", color = Ar.textMuted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestCard(r: ModRequest, onDone: (Int) -> Unit) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val route = entityRoute(r)
    val actionColor = when (r.action) { "create" -> Ar.ok; "delete" -> Ar.danger; "transfer" -> Ar.blue; else -> Ar.amber }

    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Tag(ACTION_LABELS[r.action] ?: r.action, actionColor)
            Tag(ENTITY_LABELS[r.entity_type] ?: r.entity_type)
        }
        Text(entityName(r), color = if (route != null) Ar.accentHover else Ar.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp).then(if (route != null) Modifier.clickable { nav.go(route) } else Modifier))
        Row(Modifier.padding(top = 2.dp)) {
            Text("от ", color = Ar.textMuted, fontSize = 12.sp)
            if (r.submitted_by != null) Text(r.submitted_by.username, color = Ar.accentHover, fontSize = 12.sp, modifier = Modifier.clickable { nav.go(Routes.user(r.submitted_by.id)) })
            else Text("удалённый пользователь", color = Ar.textMuted, fontSize = 12.sp)
            Text(" · ${Fmt.timeAgo(r.created_at)}", color = Ar.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        val transfer = r.transfer
        when {
            r.action == "transfer" && transfer != null -> {
                val narrator = r.entity_type == "narrator"
                Text(if (narrator) "СЕЙЧАС ВЛАДЕЕТ" else "СЕЙЧАС У ЧТЕЦОВ", color = Ar.textMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val from = (transfer["from"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
                    if (from.isEmpty()) Text("никого", color = Ar.textMuted, fontSize = 12.sp)
                    from.forEach { SidePill(it) }
                }
                Text(if (narrator) "ПОЛУЧИТ" else "ПЕРЕЙДЁТ К ЧТЕЦУ", color = Ar.textMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                Box(Modifier.padding(vertical = 4.dp)) { (transfer["to"] as? JsonObject)?.let { SidePill(it) } ?: Text("получатель больше не существует", color = Ar.danger, fontSize = 12.sp) }
                Text(
                    if (narrator) "После одобрения получатель станет единственным владельцем — все нынешние участники потеряют доступ к чтецу." else "После одобрения тайтл уйдёт от всех нынешних чтецов, а их отметки в главах будут сняты.",
                    color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
            r.action == "delete" -> Text("Одобрение навсегда удалит этот объект (${ENTITY_LABELS[r.entity_type] ?: r.entity_type}) и всё, что с ним связано.", color = Ar.danger, fontSize = 13.sp, lineHeight = 18.sp)
            r.payload.isEmpty() -> Text("В заявке нет изменённых полей.", color = Ar.textMuted, fontSize = 13.sp)
            else -> r.payload.keys.forEach { k ->
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(k.replace('_', ' '), color = Ar.textMuted, fontSize = 11.sp)
                    Row {
                        if (r.action == "update") { Text(jsonValueText(r.entity?.get(k)), color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false), maxLines = 3, overflow = TextOverflow.Ellipsis); Text(" → ", color = Ar.textMuted, fontSize = 13.sp) }
                        Text(jsonValueText(r.payload[k]), color = Ar.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArButton("Одобрить", kind = if (r.action == "delete") ButtonKind.Danger else ButtonKind.Primary, busy = busy, small = true, onClick = {
                busy = true
                scope.launch { try { Api.post<Unit>("/mod/requests/${r.id}/approve"); toast("Заявка одобрена"); onDone(r.id) } catch (e: Exception) { toastError(e); busy = false } }
            })
            ArButton("Отклонить", { rejecting = !rejecting }, small = true, enabled = !busy)
        }
        if (rejecting) Column(Modifier.padding(top = 8.dp)) {
            ArTextField(note, { note = it }, placeholder = "Причина отклонения (обязательно)…", singleLine = false, minLines = 2)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArButton("Подтвердить отклонение", kind = ButtonKind.Danger, small = true, enabled = note.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch { try { Api.post<Unit>("/mod/requests/${r.id}/reject", buildJsonObject { put("note", note.trim()) }); toast("Заявка отклонена"); onDone(r.id) } catch (e: Exception) { toastError(e); busy = false } }
                })
                ArButton("Отмена", { rejecting = false }, kind = ButtonKind.Ghost, small = true, enabled = !busy)
            }
        }
    }
}

@Composable
private fun SidePill(o: JsonObject) {
    val nav = LocalNav.current
    val username = o.str("username")
    if (username != null) UserPill(UserBrief(id = o.str("id")?.toIntOrNull() ?: 0, username = username, display_name = o.str("display_name") ?: "", avatar_url = o.str("avatar_url"), avatar_thumb_url = o.str("avatar_thumb_url")))
    else {
        val slug = o.str("slug") ?: ""
        Text(o.str("name") ?: "?", color = Ar.accentHover, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Ar.fill04).clickable(enabled = slug.isNotEmpty()) { nav.go(Routes.narrator(slug)) }.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

// ---------- app/mod/reports ----------

private val TARGET_LABELS = mapOf("comment" to "комментарий", "title" to "тайтл", "narrator" to "чтец", "chapter" to "глава", "user" to "пользователь", "collection" to "коллекция")

@Composable
fun ModReportsPage() {
    val nav = LocalNav.current
    var tab by remember { mutableStateOf("open") }
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(tab, page) { Api.get<Paginated<Report>>("/mod/reports", mapOf("status" to tab, "page" to page)) }
    var action by remember { mutableStateOf<Pair<Report, String>?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ArTabs(listOf(TabItem("open", "Открытые"), TabItem("resolved", "Решённые"), TabItem("dismissed", "Отклонённые")), tab, { tab = it; page = 1 }, Modifier.padding(bottom = 12.dp), variant = TabsVariant.Underline)
    PagedBox(loader, empty = { EmptyState(when (tab) { "open" -> "Открытых жалоб нет"; "resolved" -> "Решённых жалоб нет"; else -> "Отклонённых жалоб нет" }, if (tab == "open") "Сообщество ведёт себя прилично. Пока что." else null, Lucide.Flag) }, onPage = { page = it }) { items ->
        items.forEach { r ->
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.reporter != null) Text(r.reporter.username, color = Ar.accentHover, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { nav.go(Routes.user(r.reporter.id)) })
                    else Text("удалённый пользователь", color = Ar.textMuted, fontSize = 13.sp)
                    Text(" — жалоба: ", color = Ar.textMuted, fontSize = 13.sp)
                    Tag(TARGET_LABELS[r.target_type] ?: r.target_type)
                    Spacer(Modifier.weight(1f))
                    Text(Fmt.timeAgo(r.created_at), color = Ar.textMuted, fontSize = 11.sp)
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.target_preview.ifBlank { "—" }, color = Ar.textSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (r.target_link.isNotBlank()) ArButton("Открыть", { Links.open(nav, r.target_link) }, kind = ButtonKind.Ghost, icon = Lucide.ArrowUpRight, small = true)
                    else Text("объект удалён", color = Ar.textMuted, fontSize = 11.sp)
                }
                Text(r.reason, color = Ar.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp).background(Ar.fill04, RoundedCornerShape(8.dp)).padding(10.dp))
                if (r.status == "open") Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArButton("Принять", { note = ""; action = r to "resolved" }, kind = ButtonKind.Primary, small = true)
                    ArButton("Отклонить", { note = ""; action = r to "dismissed" }, small = true)
                } else Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(r.status)
                    if (r.resolved_at != null) Text(Fmt.timeAgo(r.resolved_at), color = Ar.textMuted, fontSize = 11.sp)
                    if (r.resolution_note.isNotBlank()) Text("“${r.resolution_note}”", color = Ar.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
    action?.let { (report, status) ->
        ArModal(true, { if (!busy) action = null }, if (status == "resolved") "Принять жалобу" else "Отклонить жалобу") {
            ArTextField(note, { note = it }, label = "Заметка (необязательно)", placeholder = "Почему такое решение…", singleLine = false, minLines = 3)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                ArButton("Отмена", { action = null }, kind = ButtonKind.Ghost, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                ArButton(if (status == "resolved") "Принять" else "Отклонить", kind = ButtonKind.Primary, busy = busy, onClick = {
                    busy = true
                    scope.launch {
                        try { Api.post<Unit>("/mod/reports/${report.id}/resolve", buildJsonObject { put("status", status); put("note", note.trim()) }); toast(if (status == "resolved") "Жалоба принята" else "Жалоба отклонена"); loader.drop { it.id == report.id }; action = null }
                        catch (e: Exception) { toastError(e) }
                        busy = false
                    }
                })
            }
        }
    }
}

// ---------- app/mod/review + TitleReviewModal ----------

@Composable
fun ModReviewPage() {
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(page) { Api.get<Paginated<ReviewQueueItem>>("/mod/review-queue", mapOf("page" to page, "per_page" to 30)) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    var openSlug by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    PagedBox(loader, empty = { EmptyState("Нечего проверять", "Все тайтлы, опубликованные напрямую (импорт или разрешение публиковать без модерации), уже проверены.", Lucide.ShieldCheck) }, onPage = { page = it }) { items ->
        ModHint("Тайтлы, опубликованные в обход очереди модерации. Отметьте, что посмотрели, чтобы убрать из списка.")
        items.forEach { item ->
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(item.name, color = Ar.accentHover, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { openSlug = item.slug })
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.is_imported) Tag("импорт", Ar.blue) else if (item.created_by_skips_moderation) Tag("без модерации", Ar.amber)
                    Text(item.created_by ?: "—", color = Ar.textMuted, fontSize = 12.sp)
                    Text(Fmt.dateTime(item.created_at), color = Ar.textMuted, fontSize = 12.sp)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArButton("Открыть", { openSlug = item.slug }, kind = ButtonKind.Ghost, icon = Lucide.Eye, small = true)
                    ArButton("Проверено", kind = ButtonKind.Primary, icon = Lucide.Check, small = true, busy = busyId == item.id, onClick = {
                        busyId = item.id
                        scope.launch { try { Api.post<Unit>("/mod/titles/${item.id}/review-check"); toast("«${item.name}» отмечен проверенным"); loader.drop { it.id == item.id } } catch (e: Exception) { toastError(e) }; busyId = null }
                    })
                }
            }
        }
    }
    TitleReviewModal(openSlug) { openSlug = null }
}

/** components/TitleReviewModal: full-size cover/bg previews plus a quick edit form through the mod endpoints. */
@Composable
fun TitleReviewModal(slug: String?, onClose: () -> Unit) {
    if (slug == null) return
    val nav = LocalNav.current
    val loader = rememberLoader(slug, keepOnReload = true) { Api.get<TitleFull>("/titles/${Routes.enc(slug)}") }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var imageBusy by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf<String?>(null) }
    val t = loader.data
    var name by remember(t?.id) { mutableStateOf(t?.name ?: "") }
    var altNames by remember(t?.id) { mutableStateOf(t?.alt_names?.joinToString(", ") ?: "") }
    var description by remember(t?.id) { mutableStateOf(t?.description ?: "") }
    var year by remember(t?.id) { mutableStateOf(t?.year?.toString() ?: "") }
    var author by remember(t?.id) { mutableStateOf(t?.author?.let { Author(it.id, it.name, it.slug) }) }
    var genreIds by remember(t?.id) { mutableStateOf(t?.genres?.map { it.id } ?: emptyList()) }
    var status by remember(t?.id) { mutableStateOf(t?.release_status ?: "ongoing") }
    var isNsfw by remember(t?.id) { mutableStateOf(t?.is_nsfw ?: false) }
    var isAi by remember(t?.id) { mutableStateOf(t?.is_ai ?: false) }
    fun sendImage(kind: String, p: com.audioranobe.app.ui.components.PickedImage) {
        val id = t?.id ?: return
        imageBusy = true
        scope.launch { try { Api.upload<Unit>("/panel/titles/$id/$kind") { addPart(p.part("file", "$kind.webp")) }; loader.reload(); toast(if (kind == "cover") "Обложка обновлена" else "Фон обновлён") } catch (e: Exception) { toastError(e) }; imageBusy = false }
    }
    val coverCropper = rememberImageCropper(2, 3, 2048, 2048) { sendImage("cover", it) }
    val bgCropper = rememberImageCropper(3, 1, 2048, 2048) { sendImage("bg", it) }

    ArModal(true, onClose, "Проверка тайтла") {
        when (val s = loader.state) {
            is Load.Loading -> CenterSpinner()
            is Load.Err -> Text(s.message, color = Ar.danger, fontSize = 13.sp)
            is Load.Ok -> {
                val title = s.data
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.width(110.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Ar.surfaceRaised).clickable(enabled = title.cover_url != null) { zoom = title.cover_url }, contentAlignment = Alignment.Center) {
                            if (title.cover_url != null) ArImage(title.cover_url, Modifier.fillMaxSize()) else Text("нет обложки", color = Ar.textMuted, fontSize = 11.sp)
                        }
                        ArButton("Заменить", { coverCropper.pick() }, kind = ButtonKind.Ghost, icon = Lucide.ImagePlus, small = true, enabled = !imageBusy, fullWidth = true)
                    }
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(8.dp)).background(Ar.surfaceRaised).clickable(enabled = title.bg_url != null) { zoom = title.bg_url }, contentAlignment = Alignment.Center) {
                            if (title.bg_url != null) ArImage(title.bg_url, Modifier.fillMaxSize()) else Text("нет фона", color = Ar.textMuted, fontSize = 11.sp)
                        }
                        ArButton("Заменить", { bgCropper.pick() }, kind = ButtonKind.Ghost, icon = Lucide.ImagePlus, small = true, enabled = !imageBusy, fullWidth = true)
                    }
                }
                Spacer(Modifier.height(10.dp))
                ArTextField(name, { name = it }, label = "Название")
                Spacer(Modifier.height(8.dp))
                Row {
                    ArTextField(year, { year = it }, Modifier.weight(1f), label = "Год", keyboardType = KeyboardType.Number)
                    Spacer(Modifier.width(8.dp))
                    SelectMenu(status, Labels.statusValues.map { SelectOption(it, Labels.releaseStatus[it] ?: it) }, { status = it }, Modifier.weight(1f), label = "Статус выпуска")
                }
                Spacer(Modifier.height(8.dp))
                ArTextField(altNames, { altNames = it }, label = "Альтернативные названия (через запятую)")
                Spacer(Modifier.height(8.dp))
                AuthorPicker(author, { author = it }, label = "Автор")
                Spacer(Modifier.height(8.dp))
                ArTextField(description, { description = it }, label = "Описание", singleLine = false, minLines = 4)
                Spacer(Modifier.height(8.dp))
                GenrePicker(genreIds, { genreIds = it }, label = "Теги")
                Spacer(Modifier.height(8.dp))
                ArToggle(isNsfw, { isNsfw = it }, "18+ / чувствительный контент", enabled = !busy)
                ArToggle(isAi, { isAi = it }, "Озвучено ИИ", enabled = !busy)
                Text("Чтецы: " + (title.narrators.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name } ?: "—"), color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArButton("На сайте", { onClose(); nav.go(Routes.title(title.slug)) }, kind = ButtonKind.Ghost, icon = Lucide.ExternalLink, small = true)
                    Spacer(Modifier.weight(1f))
                    ArButton("Отмена", onClose, kind = ButtonKind.Ghost, small = true)
                    ArButton("Сохранить", kind = ButtonKind.Primary, small = true, busy = busy, onClick = {
                        var y: Int? = null
                        if (year.isNotBlank()) { y = year.trim().toIntOrNull(); if (y == null || y < 0 || y > java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 5) { toastError("Похоже, год указан неверно"); return@ArButton } }
                        busy = true
                        scope.launch {
                            try {
                                Api.patch<Unit>("/mod/titles/${title.id}", buildJsonObject {
                                    put("name", name.trim()); put("alt_names", buildJsonArray { altNames.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } })
                                    put("author_id", author?.id?.let { JsonPrimitive(it) } ?: JsonNull); put("description", description); put("year", y?.let { JsonPrimitive(it) } ?: JsonNull)
                                    put("release_status", status); put("genre_ids", buildJsonArray { genreIds.forEach { add(JsonPrimitive(it)) } }); put("is_nsfw", isNsfw); put("is_ai", isAi)
                                })
                                toast("Тайтл обновлён"); onClose()
                            } catch (e: Exception) { toastError(e) }
                            busy = false
                        }
                    })
                }
            }
        }
    }
    com.audioranobe.app.ui.components.ImageViewer(zoom != null, listOfNotNull(zoom), onClose = { zoom = null })
}

// ---------- app/mod/comments ----------

@Composable
fun ModCommentsPage() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val isAdmin = auth.can("comments.moderate")
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(page) { Api.get<Paginated<Comment>>("/mod/comments", mapOf("page" to page, "per_page" to 50)) }
    var marking by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Comment?>(null) }
    var editBody by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Comment?>(null) }
    var expanded by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val scope = rememberCoroutineScope()
    suspend fun mark(ids: List<Int>) { Api.post<Unit>("/mod/comments/mark-checked", buildJsonObject { put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) }); loader.patchItems { if (it.id in ids) it.copy(mod_reviewed = true) else it } }
    val unchecked = loader.data?.items?.count { !it.mod_reviewed } ?: 0
    @Composable fun MarkButton() = ArButton(if (unchecked == 0) "Страница проверена" else "Отметить страницу проверенной ($unchecked)", kind = ButtonKind.Primary, icon = Lucide.CheckCheck, small = true, enabled = unchecked > 0, busy = marking, onClick = {
        val ids = loader.data?.items?.filter { !it.mod_reviewed }?.map { it.id } ?: return@ArButton
        marking = true; scope.launch { try { mark(ids); toast("Страница отмечена проверенной (${ids.size})") } catch (e: Exception) { toastError(e) }; marking = false }
    })
    PagedBox(loader, empty = { EmptyState("Комментариев пока нет", icon = Lucide.MessageSquare) }, onPage = { page = it }) { items ->
        ModHint("Каждый комментарий на сайте, новые сверху.")
        MarkButton(); Spacer(Modifier.height(10.dp))
        items.forEach { c ->
            val busy = busyId == c.id
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.user?.username ?: "[удалён]", color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    if (c.target != null) Text(c.target.name, color = Ar.accentHover, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { Links.open(nav, c.target.link) })
                    else Text("объект удалён", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                FlowRowChips(c)
                if (editing?.id == c.id) {
                    ArTextField(editBody, { editBody = it }, Modifier.padding(top = 6.dp), singleLine = false, minLines = 3)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArButton("Сохранить", kind = ButtonKind.Primary, small = true, enabled = editBody.isNotBlank() && !busy, onClick = {
                            busyId = c.id
                            scope.launch { try { val u = Api.patch<Comment>("/comments/${c.id}", buildJsonObject { put("body", editBody.trim()) }); loader.patchItems { if (it.id == c.id) it.copy(body = u.body, updated_at = u.updated_at, edited_by_staff = u.edited_by_staff) else it }; toast("Комментарий обновлён"); editing = null } catch (e: Exception) { toastError(e) }; busyId = null }
                        })
                        ArButton("Отмена", { editing = null }, kind = ButtonKind.Ghost, small = true)
                    }
                } else {
                    val long = c.body.length > 600
                    val open = c.id in expanded
                    Text(c.body, color = if (c.is_deleted) Ar.textMuted else Ar.text, fontSize = 13.sp, lineHeight = 19.sp, maxLines = if (long && !open) 10 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    if (long) Text(if (open) "Свернуть" else "Показать полностью", color = Ar.accentHover, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp).clickable { expanded = if (open) expanded - c.id else expanded + c.id })
                    if (!c.is_deleted) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ArButton("Проверено", kind = ButtonKind.Ghost, icon = Lucide.CheckCheck, small = true, enabled = !busy && !c.mod_reviewed, onClick = { busyId = c.id; scope.launch { try { mark(listOf(c.id)) } catch (e: Exception) { toastError(e) }; busyId = null } })
                        ArButton("Изменить", { editing = c; editBody = c.body }, kind = ButtonKind.Ghost, icon = Lucide.Pencil, small = true, enabled = !busy)
                        ArButton("Удалить", { toDelete = c }, kind = ButtonKind.Ghost, icon = Lucide.Trash2, small = true, enabled = !busy)
                    }
                }
            }
        }
        MarkButton()
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = {
        val c = toDelete ?: return@ConfirmDialog
        busyId = c.id
        scope.launch {
            try { Api.delete<Unit>("/comments/${c.id}"); toast("Комментарий удалён"); if (isAdmin) loader.patchItems { if (it.id == c.id) it.copy(is_deleted = true) else it } else loader.drop { it.id == c.id } } catch (e: Exception) { toastError(e) }
            busyId = null; toDelete = null
        }
    }, title = "Удалить комментарий", body = "Удалить комментарий пользователя «${toDelete?.user?.username ?: "[удалён]"}»?", danger = true, confirmLabel = "Удалить")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(c: Comment) {
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(Fmt.dateTime(c.created_at), color = Ar.textMuted, fontSize = 11.sp)
        if (c.is_deleted) Tag("удалено", Ar.danger)
        if (c.mod_reviewed) Tag("проверено", Ar.ok) else Tag("не проверено", Ar.amber)
    }
}

// ---------- app/mod/words ----------

@Composable
private fun ModeToggle(mode: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Слово", color = Ar.textMuted, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        ArToggle(mode == "substring", { onChange(if (it) "substring" else "word") }, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Text("Подстрока", color = Ar.textMuted, fontSize = 12.sp)
    }
}

@Composable
fun ModWordsPage() {
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(page) { Api.get<Paginated<BannedWord>>("/mod/words", mapOf("page" to page, "per_page" to 50)) }
    var newWord by remember { mutableStateOf("") }
    var newMode by remember { mutableStateOf("substring") }
    var newNote by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BannedWord?>(null) }
    var editWord by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<BannedWord?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text("Новое запрещённое слово", color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Фильтр применяется ко всем текстам на сайте: названиям, описаниям, био, комментариям, именам пользователей и slug-ам. Сравнение регистронезависимое и устойчиво к разделителям, повторам букв и подмене латиницы на кириллицу.", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
        ArTextField(newWord, { newWord = it }, placeholder = "Слово")
        Spacer(Modifier.height(6.dp))
        ModeToggle(newMode, { newMode = it })
        Spacer(Modifier.height(6.dp))
        ArTextField(newNote, { newNote = it }, placeholder = "Комментарий (необязательно)")
        Spacer(Modifier.height(8.dp))
        ArButton("Добавить", kind = ButtonKind.Primary, enabled = newWord.isNotBlank() && !creating, busy = creating, onClick = {
            creating = true
            scope.launch { try { Api.post<Unit>("/mod/words", buildJsonObject { put("word", newWord.trim()); put("match_mode", newMode); put("note", newNote.trim()) }); toast("Слово добавлено в фильтр"); newWord = ""; newNote = ""; newMode = "substring"; loader.reload() } catch (e: Exception) { toastError(e) }; creating = false }
        })
    }
    PagedBox(loader, empty = { EmptyState("Фильтр пуст", "Добавьте первое запрещённое слово выше.", Lucide.Filter) }, onPage = { page = it }) { items ->
        GlassPanel(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, w ->
                if (i > 0) HairlineDivider(Modifier.padding(vertical = 8.dp))
                val busy = busyId == w.id
                if (editing?.id == w.id) Row(verticalAlignment = Alignment.CenterVertically) {
                    ArTextField(editWord, { editWord = it }, Modifier.weight(1f))
                    IconBtn(Lucide.Check, "Сохранить", { val word = editWord.trim(); if (word.isEmpty() || word == w.word) { editing = null } else { busyId = w.id; scope.launch { try { Api.patch<Unit>("/mod/words/${w.id}", buildJsonObject { put("word", word) }); toast("Слово обновлено"); editing = null; loader.reload() } catch (e: Exception) { toastError(e) }; busyId = null } } }, enabled = !busy && editWord.isNotBlank(), tint = Ar.ok)
                    IconBtn(Lucide.X, "Отмена", { editing = null })
                } else Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.word, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconBtn(Lucide.Pencil, "Редактировать", { editing = w; editWord = w.word }, size = 32.dp, iconSize = 15.dp, enabled = !busy)
                        IconBtn(Lucide.Trash2, "Удалить", { toDelete = w }, size = 32.dp, iconSize = 15.dp, enabled = !busy, tint = Ar.danger)
                    }
                    ModeToggle(w.match_mode, { m -> if (m != w.match_mode) { busyId = w.id; scope.launch { try { Api.patch<Unit>("/mod/words/${w.id}", buildJsonObject { put("match_mode", m) }); loader.patchItems { if (it.id == w.id) it.copy(match_mode = m) else it } } catch (e: Exception) { toastError(e) }; busyId = null } } }, enabled = !busy)
                    Text(listOfNotNull(w.note.takeIf { it.isNotBlank() }, w.created_by?.let { "добавил $it" }).joinToString(" · ").ifBlank { "—" }, color = Ar.textMuted, fontSize = 12.sp)
                }
            }
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = {
        val w = toDelete ?: return@ConfirmDialog
        busyId = w.id; toDelete = null
        scope.launch { try { Api.delete<Unit>("/mod/words/${w.id}"); toast("Слово удалено из фильтра"); loader.drop { it.id == w.id } } catch (e: Exception) { toastError(e) }; busyId = null }
    }, title = "Удалить слово", body = "Убрать «${toDelete?.word}» из фильтра?", danger = true, confirmLabel = "Удалить")
}

// ---------- app/mod/usernames ----------

private val USERNAME_RE = Regex("^[A-Za-z0-9_]{3,30}$")

@Composable
fun ModUsernamesPage() {
    var q by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(q) { delay(300); query = q.trim() }
    val list = rememberPagedList(query) { page -> Api.get<Paginated<ReservedUsername>>("/mod/usernames", mapOf("q" to query, "page" to page, "per_page" to 50)) }
    var newName by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ReservedUsername?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val valid = USERNAME_RE.matches(newName.trim())

    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text("Зарезервировать имя", color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Зарезервированное имя нельзя занять никаким способом. Регистр не важен.", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        ArTextField(newName, { newName = it }, placeholder = "Имя пользователя", maxLength = 30, error = if (newName.isNotBlank() && !valid) "3–30 символов: латинские буквы, цифры и подчёркивание" else null)
        Spacer(Modifier.height(6.dp))
        ArTextField(newNote, { newNote = it }, placeholder = "Комментарий — зачем (необязательно)", maxLength = 500)
        Spacer(Modifier.height(8.dp))
        ArButton("Зарезервировать", kind = ButtonKind.Primary, enabled = valid && !creating, busy = creating, onClick = {
            creating = true
            scope.launch { try { Api.post<Unit>("/mod/usernames", buildJsonObject { put("username", newName.trim()); put("note", newNote.trim()) }); toast("Имя ${newName.trim()} зарезервировано"); newName = ""; newNote = ""; list.load() } catch (e: Exception) { toastError(e) }; creating = false }
        })
    }
    ModSearch(q, { q = it }, "Поиск по списку")
    val items = list.items
    when {
        list.error.isNotEmpty() -> ErrorState(list.error, { scope.launch { list.load() } })
        list.loading || items == null -> CenterSpinner()
        items.isEmpty() -> EmptyState(if (query.isNotEmpty()) "Ничего не найдено" else "Список пуст", if (query.isNotEmpty()) "По этому запросу зарезервированных имён нет." else "Зарезервируйте первое имя выше — например, имя бренда или служебный аккаунт.", Lucide.Lock)
        else -> {
            GlassPanel(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, r ->
                    if (i > 0) HairlineDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.username, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(r.note.takeIf { it.isNotBlank() }, r.created_by_username, Fmt.date(r.created_at)).joinToString(" · "), color = Ar.textMuted, fontSize = 12.sp)
                        }
                        IconBtn(Lucide.Trash2, "Освободить имя", { toDelete = r }, size = 32.dp, iconSize = 15.dp, tint = Ar.danger, enabled = busyId != r.id)
                    }
                }
            }
            InfiniteScrollTrigger(list)
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = {
        val r = toDelete ?: return@ConfirmDialog
        toDelete = null; busyId = r.id
        scope.launch { try { Api.delete<Unit>("/mod/usernames/${r.id}"); toast("Имя ${r.username} освобождено"); list.remove { it.id == r.id } } catch (e: Exception) { toastError(e) }; busyId = null }
    }, title = "Освободить имя", body = "Снять резерв с «${toDelete?.username}»? Его сразу сможет занять любой желающий.", danger = true, confirmLabel = "Освободить")
}

// ---------- app/mod/trash ----------

private val KIND_TABS = listOf("title" to "Тайтлы", "narrator" to "Чтецы", "chapter" to "Главы", "author" to "Авторы", "comment" to "Комментарии")

@Composable
fun ModTrashPage() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val canRestore = auth.can("trash.restore")
    val canPurge = auth.can("trash.purge")
    val visible = KIND_TABS.filter { auth.can("trash.view.${it.first}") }
    var kind by remember { mutableStateOf(visible.firstOrNull()?.first ?: "title") }
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(kind, page) { Api.get<Paginated<TrashEntry>>("/mod/trash", mapOf("kind" to kind, "page" to page)) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    var toPurge by remember { mutableStateOf<TrashEntry?>(null) }
    val scope = rememberCoroutineScope()
    ModHint("Ничто на сайте не удаляется сразу — всё попадает сюда. Модератор может вернуть запись, администратор — стереть её окончательно вместе с файлами.")
    ArTabs(visible.map { TabItem(it.first, it.second) }, kind, { kind = it; page = 1 }, Modifier.padding(bottom = 12.dp), variant = TabsVariant.Underline)
    PagedBox(loader, empty = { EmptyState("Здесь пусто", "Ничего удалённого этого типа нет.", Lucide.Trash2) }, onPage = { page = it }) { items ->
        items.forEach { e ->
            val busy = busyId == e.id
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(e.name, color = if (e.link.isNotBlank()) Ar.accentHover else Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(enabled = e.link.isNotBlank()) { Links.open(nav, e.link) })
                Text(listOfNotNull(e.context.takeIf { it.isNotBlank() }, e.deleted_at?.let { "удалено ${Fmt.timeAgo(it)}" }).joinToString(" · ").ifBlank { "—" }, color = Ar.textMuted, fontSize = 12.sp)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canRestore) ArButton("Вернуть", icon = Lucide.RotateCcw, small = true, busy = busy, onClick = { busyId = e.id; scope.launch { try { Api.post<Unit>("/mod/trash/${e.kind}/${e.id}/restore"); toast("Восстановлено"); loader.drop { it.id == e.id } } catch (x: Exception) { toastError(x) }; busyId = null } })
                    if (canPurge) ArButton("Стереть", { toPurge = e }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true, enabled = !busy)
                }
            }
        }
    }
    ConfirmDialog(toPurge != null, { toPurge = null }, onConfirm = {
        val e = toPurge ?: return@ConfirmDialog
        busyId = e.id
        scope.launch { try { Api.delete<Unit>("/mod/trash/${e.kind}/${e.id}"); toast("Удалено навсегда"); toPurge = null; loader.drop { it.id == e.id } } catch (x: Exception) { toastError(x) }; busyId = null }
    }, title = "Стереть навсегда", body = "«${toPurge?.name}» и все связанные файлы будут удалены окончательно. Вернуть будет нечего.", danger = true, confirmLabel = "Стереть")
}
