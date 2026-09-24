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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.Fmt
import com.audioranobe.app.data.Author
import com.audioranobe.app.data.Badge
import com.audioranobe.app.data.DmcaRequest
import com.audioranobe.app.data.Genre
import com.audioranobe.app.data.LocalAuth
import com.audioranobe.app.data.Me
import com.audioranobe.app.data.ModNarrator
import com.audioranobe.app.data.ModNarratorList
import com.audioranobe.app.data.Paginated
import com.audioranobe.app.data.RequestableTitle
import com.audioranobe.app.data.RoleOption
import com.audioranobe.app.data.TitleCard
import com.audioranobe.app.ui.components.ArButton
import com.audioranobe.app.ui.components.ArImage
import com.audioranobe.app.ui.components.ArModal
import com.audioranobe.app.ui.components.ArTabs
import com.audioranobe.app.ui.components.ArTextField
import com.audioranobe.app.ui.components.ArToggle
import com.audioranobe.app.ui.components.BadgeIcon
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
import com.audioranobe.app.ui.components.SelectMenu
import com.audioranobe.app.ui.components.SelectOption
import com.audioranobe.app.ui.components.Spinner
import com.audioranobe.app.ui.components.StatusBadge
import com.audioranobe.app.ui.components.TabItem
import com.audioranobe.app.ui.components.TabsVariant
import com.audioranobe.app.ui.components.UserAvatar
import com.audioranobe.app.ui.components.rememberLoader
import com.audioranobe.app.ui.components.rememberPagedList
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.nav.Links
import com.audioranobe.app.ui.nav.LocalNav
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.screens.catalog.Pagination
import com.audioranobe.app.ui.screens.me.UserEditModal
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.toast.toast
import com.audioranobe.app.ui.toast.toastError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable private data class Items<T>(val items: List<T> = emptyList())
@Serializable private data class ImportResult(val name: String = "", val already: Boolean = false)

// ---------- app/mod/titles ----------

@Composable
fun ModTitlesPage() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(q) { delay(300); query = q.trim() }
    val loader = rememberLoader(query, keepOnReload = true) { Api.get<Items<TitleCard>>("/titles", mapOf("q" to query.ifBlank { null }, "per_page" to 30, "sort" to "new")).items }
    var importOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        ArTextField(q, { q = it }, Modifier.weight(1f), placeholder = "Поиск по каталогу", leading = Lucide.Search)
        Spacer(Modifier.width(8.dp))
        IconBtn(Lucide.DownloadCloud, "Импортировать тайтл", { importOpen = true }, tint = Ar.accent, background = Ar.accentSoft)
    }
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner()
        is Load.Err -> ErrorState(s.message, { loader.reload() })
        is Load.Ok -> if (s.data.isEmpty()) Text("Ничего не найдено.", color = Ar.textMuted, fontSize = 13.sp) else s.data.forEach { t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { nav.go(Routes.title(t.slug)) }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ArImage(t.cover_thumb_url ?: t.cover_url, Modifier.width(38.dp).height(52.dp), shape = RoundedCornerShape(6.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(t.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(t.year?.toString(), t.chapters_count.takeIf { it > 0 }?.let { "$it гл." }).joinToString(" · "), color = Ar.textMuted, fontSize = 12.sp)
                }
            }
        }
    }
    if (importOpen) {
        var iq by remember { mutableStateOf("") }
        var items by remember { mutableStateOf<List<RequestableTitle>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }
        var busyRef by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(iq) {
            if (iq.trim().length < 2) { items = emptyList(); return@LaunchedEffect }
            searching = true; delay(350)
            try { items = Api.get<Items<RequestableTitle>>("/admin/titles/import-search", mapOf("q" to iq.trim())).items } catch (e: Exception) { toastError(e) }
            searching = false
        }
        ArModal(true, { importOpen = false }, "Импорт тайтла") {
            ArTextField(iq, { iq = it }, placeholder = "Название тайтла в источнике", leading = Lucide.Search)
            Text("Импортируются только метаданные — без озвучки и глав.", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Spinner() }
                items.isEmpty() -> Text(if (iq.trim().length < 2) "Введите запрос." else "Ничего не найдено.", color = Ar.textMuted, fontSize = 13.sp)
                else -> items.forEach { it ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArImage(it.cover_url, Modifier.width(36.dp).height(48.dp), shape = RoundedCornerShape(6.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.name, color = Ar.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(it.year?.toString(), it.status.takeIf { s -> s.isNotBlank() }).joinToString(" · "), color = Ar.textMuted, fontSize = 12.sp)
                        }
                        ArButton("Импорт", kind = ButtonKind.Primary, icon = Lucide.DownloadCloud, small = true, enabled = busyRef == null, busy = busyRef == it.ref, onClick = {
                            busyRef = it.ref
                            scope.launch { try { val res = Api.post<ImportResult>("/admin/titles/import", buildJsonObject { put("ref", it.ref) }); toast(if (res.already) "«${res.name}» уже в каталоге" else "«${res.name}» импортирован"); importOpen = false; loader.reload() } catch (e: Exception) { toastError(e) }; busyRef = null }
                        })
                    }
                }
            }
        }
    }
}

// ---------- app/mod/users ----------

@Composable
fun ModUsersPage() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val me by auth.user.collectAsStateWithLifecycle()
    val canRole = auth.can("users.role"); val canBan = auth.can("users.ban"); val canSkip = auth.can("users.grant_skip_moderation"); val canDelete = auth.can("users.delete"); val isAdmin = auth.can("*")
    val roleOptions = rememberLoader(Unit) { runCatching { Api.get<Items<RoleOption>>("/mod/role-options").items }.getOrDefault(emptyList()) }.data ?: emptyList()
    fun priorityOf(slug: String) = roleOptions.firstOrNull { it.slug == slug }?.priority ?: 0
    val myRank = if (isAdmin) Int.MAX_VALUE else priorityOf(me?.role ?: "")
    fun outranks(u: Me) = isAdmin || priorityOf(u.role) < myRank
    var q by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(q) { delay(300); query = q.trim() }
    val list = rememberPagedList(query) { page -> Api.get<Paginated<Me>>("/mod/users", mapOf("q" to query, "page" to page)) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    var toDelete by remember { mutableStateOf<Me?>(null) }
    var toEditId by remember { mutableStateOf<Int?>(null) }
    var toBan by remember { mutableStateOf<Me?>(null) }
    var banReason by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun patchUser(u: Me, path: String, body: kotlinx.serialization.json.JsonObject, ok: (Me) -> String) {
        busyId = u.id
        scope.launch { try { val updated = Api.patch<Me>(path, body); list.patch({ it.id == updated.id }) { updated }; toast(ok(updated)) } catch (e: Exception) { toastError(e) }; busyId = null }
    }

    ModSearch(q, { q = it }, "Поиск по нику или email…")
    val items = list.items
    when {
        list.error.isNotEmpty() -> ErrorState(list.error, { scope.launch { list.load() } })
        list.loading || items == null -> CenterSpinner()
        items.isEmpty() -> EmptyState("Пользователи не найдены", "Попробуйте изменить запрос.", Lucide.Users)
        else -> {
            items.forEach { u ->
                val self = me?.id == u.id
                val busy = busyId == u.id
                val ok = outranks(u)
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp), borderColor = if (u.is_banned) Ar.danger.copy(alpha = 0.4f) else Ar.border) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(u.username, u.avatar_url, 34.dp, thumbUrl = u.avatar_thumb_url, onClick = { nav.go(Routes.user(u.id)) })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.username, color = Ar.accentHover, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { nav.go(Routes.user(u.id)) })
                                if (u.is_banned) { Spacer(Modifier.width(6.dp)); Icon(Lucide.Hammer, "Забанен", tint = Ar.danger, modifier = Modifier.size(13.dp)) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.email ?: "—", color = Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (u.email != null && isAdmin) {
                                    Spacer(Modifier.width(4.dp))
                                    if (u.email_verified) Icon(Lucide.MailCheck, "Почта подтверждена", tint = Ar.ok, modifier = Modifier.size(13.dp))
                                    else IconBtn(Lucide.MailCheck, "Отметить почту подтверждённой", { patchUser(u, "/mod/users/${u.id}", buildJsonObject { put("email_verified", true) }) { "Почта отмечена подтверждённой" } }, size = 22.dp, iconSize = 13.dp, enabled = !busy)
                                }
                            }
                            Text("Регистрация: ${Fmt.date(u.created_at)}", color = Ar.textMuted, fontSize = 11.sp)
                        }
                        IconBtn(Lucide.Pencil, "Редактировать", { toEditId = u.id }, size = 32.dp, iconSize = 15.dp, enabled = !busy && (self || ok))
                        if (canDelete) IconBtn(Lucide.Trash2, "Удалить аккаунт", { toDelete = u }, size = 32.dp, iconSize = 15.dp, enabled = !self && !busy && ok, tint = Ar.danger)
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SelectMenu(u.role, roleOptions.filter { isAdmin || it.priority < myRank || it.slug == u.role }.map { SelectOption(it.slug, it.name) }, { role -> if (role != u.role) patchUser(u, "/mod/users/${u.id}", buildJsonObject { put("role", role) }) { "${u.username} теперь ${roleOptions.firstOrNull { r -> r.slug == role }?.name ?: role}" } }, Modifier.weight(1f), enabled = canRole && !self && !busy && ok, small = true)
                        Spacer(Modifier.width(8.dp))
                        ArToggle(u.skip_moderation, { patchUser(u, "/mod/users/${u.id}/skip-moderation", buildJsonObject { put("skip_moderation", !u.skip_moderation) }) { if (it.skip_moderation) "${u.username} публикует без модерации" else "${u.username} снова проходит модерацию" } }, "Без мод.", enabled = canSkip && !busy && ok)
                        Spacer(Modifier.width(8.dp))
                        ArButton(if (u.is_banned) "Разбанить" else "Забанить", kind = if (u.is_banned) ButtonKind.Default else ButtonKind.Danger, small = true, enabled = !self && !busy && canBan && ok, onClick = {
                            if (isAdmin) { toBan = u; banReason = "" }
                            else patchUser(u, "/mod/users/${u.id}", buildJsonObject { put("is_banned", !u.is_banned) }) { if (it.is_banned) "${u.username} забанен" else "${u.username} разбанен" }
                        })
                    }
                }
            }
            InfiniteScrollTrigger(list)
        }
    }
    toEditId?.let { id -> UserEditModal(id, onClose = { toEditId = null }) { updated -> list.patch({ it.id == updated.id }) { updated } } }
    toBan?.let { u ->
        ArModal(true, { toBan = null }, if (u.is_banned) "Разбанить пользователя" else "Забанить пользователя") {
            Text(if (u.is_banned) "Вы уверены, что хотите разбанить ${u.username}?" else "Забанить пользователя ${u.username}?", color = Ar.textSecondary, fontSize = 14.sp)
            if (!u.is_banned) { Spacer(Modifier.height(10.dp)); ArTextField(banReason, { banReason = it }, label = "Причина бана *", placeholder = "Укажите причину бана…", singleLine = false, minLines = 3) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                ArButton("Отмена", { toBan = null }, kind = ButtonKind.Ghost)
                Spacer(Modifier.width(8.dp))
                ArButton(if (u.is_banned) "Разбанить" else "Забанить", kind = if (u.is_banned) ButtonKind.Primary else ButtonKind.Danger, enabled = u.is_banned || banReason.isNotBlank(), busy = busyId == u.id, onClick = {
                    patchUser(u, "/mod/users/${u.id}", buildJsonObject { put("is_banned", !u.is_banned); if (banReason.isNotBlank()) put("ban_reason", banReason.trim()) }) { if (it.is_banned) "${u.username} забанен" else "${u.username} разбанен" }
                    toBan = null
                })
            }
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = {
        val u = toDelete ?: return@ConfirmDialog
        busyId = u.id; toDelete = null
        scope.launch { try { Api.delete<Unit>("/mod/users/${u.id}"); toast("Пользователь удалён"); list.remove { it.id == u.id } } catch (e: Exception) { toastError(e) }; busyId = null }
    }, title = "Удалить пользователя", body = "Навсегда удалить аккаунт ${toDelete?.username}? Это нельзя отменить.", danger = true, confirmLabel = "Удалить")
}

// ---------- app/mod/narrators ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModNarratorsPage() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    var status by remember { mutableStateOf("pending") }
    var q by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    LaunchedEffect(q) { delay(300); query = q.trim(); page = 1 }
    val loader = rememberLoader(status, query, page, keepOnReload = true) { Api.get<ModNarratorList>("/mod/narrators", mapOf("status" to status, "q" to query, "page" to page, "per_page" to 50)) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    var toDelete by remember { mutableStateOf<ModNarrator?>(null) }
    var toPurge by remember { mutableStateOf<ModNarrator?>(null) }
    val scope = rememberCoroutineScope()
    fun act(n: ModNarrator, done: String, run: suspend () -> Unit) { busyId = n.id; scope.launch { try { run(); toast(done); toDelete = null; toPurge = null; loader.reload() } catch (e: Exception) { toastError(e) }; busyId = null } }
    val counts = loader.data?.counts
    ModHint("Все страницы чтецов — включая ожидающие проверки, отклонённые и удалённые. Публичный каталог показывает только одобренных.")
    ArTabs(listOf(TabItem("pending", "На проверке", counts?.pending ?: 0), TabItem("approved", "Одобренные", counts?.approved ?: 0), TabItem("rejected", "Отклонённые", counts?.rejected ?: 0), TabItem("deleted", "В корзине", counts?.deleted ?: 0), TabItem("all", "Все")), status, { status = it; page = 1 }, Modifier.padding(bottom = 12.dp), variant = TabsVariant.Underline)
    ModSearch(q, { q = it }, "Поиск по имени или slug…")
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner()
        is Load.Err -> ErrorState(s.message, { loader.reload() })
        is Load.Ok -> if (s.data.items.isEmpty()) EmptyState("Чтецов нет", "В этой категории пока никого.", Lucide.Mic) else {
            s.data.items.forEach { n ->
                val busy = busyId == n.id
                val deleted = n.deleted_at != null
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArImage(n.avatar_url, Modifier.size(36.dp), fallbackIcon = Lucide.Mic, shape = androidx.compose.foundation.shape.CircleShape)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.name, color = Ar.accentHover, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { nav.go(Routes.narrator(n.slug)) })
                            Text(n.slug, color = Ar.textMuted, fontSize = 11.sp)
                        }
                        StatusBadge(n.mod_status)
                    }
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (n.is_self) Tag("сам чтец", Ar.blue)
                        if (deleted) Tag("удалён", Ar.danger)
                        Text("владелец: " + (n.owner?.username ?: "—"), color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.clickable(enabled = n.owner != null) { n.owner?.let { nav.go(Routes.user(it.id)) } })
                        Text("тайтлов: ${n.titles_count}", color = Ar.textMuted, fontSize = 12.sp)
                        Text("подписчиков: ${n.subscribers_count}", color = Ar.textMuted, fontSize = 12.sp)
                        Text(Fmt.timeAgo(n.created_at), color = Ar.textMuted, fontSize = 12.sp)
                    }
                    if (auth.can("narrators.contact") && !n.admin_contact.isNullOrBlank()) Text(n.admin_contact, color = Ar.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (deleted) {
                            if (auth.can("trash.view.narrator") && auth.can("trash.restore")) ArButton("Вернуть", { act(n, "Чтец восстановлен") { Api.post<Unit>("/mod/trash/narrator/${n.id}/restore") } }, icon = Lucide.RotateCcw, small = true, busy = busy)
                            if (auth.can("trash.view.narrator") && auth.can("trash.purge")) ArButton("Стереть", { toPurge = n }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true, enabled = !busy)
                        } else {
                            if (n.mod_status != "approved") ArButton("Одобрить", { act(n, "Чтец одобрен") { Api.patch<Unit>("/mod/narrators/${n.id}", buildJsonObject { put("mod_status", "approved") }) } }, kind = ButtonKind.Primary, icon = Lucide.Check, small = true, busy = busy)
                            if (n.mod_status != "rejected") ArButton("Отклонить", { act(n, "Чтец отклонён") { Api.patch<Unit>("/mod/narrators/${n.id}", buildJsonObject { put("mod_status", "rejected") }) } }, icon = Lucide.X, small = true, busy = busy)
                            ArButton("Изменить", { nav.go(Routes.narratorEdit(n.slug)) }, kind = ButtonKind.Ghost, icon = Lucide.Pencil, small = true)
                            if (auth.can("narrators.delete")) ArButton("Удалить", { toDelete = n }, kind = ButtonKind.Ghost, icon = Lucide.Trash2, small = true, enabled = !busy)
                        }
                    }
                }
            }
            Pagination(s.data.page, s.data.total, s.data.per_page) { page = it }
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = { toDelete?.let { n -> act(n, "Чтец удалён") { Api.delete<Unit>("/mod/narrators/${n.id}") } } }, title = "Удалить чтеца", body = "«${toDelete?.name}» попадёт в корзину. Тайтлы этого чтеца останутся на месте.", danger = true, confirmLabel = "Удалить")
    ConfirmDialog(toPurge != null, { toPurge = null }, onConfirm = { toPurge?.let { n -> act(n, "Удалено навсегда") { Api.delete<Unit>("/mod/trash/narrator/${n.id}") } } }, title = "Стереть навсегда", body = "«${toPurge?.name}», аватар и обложка будут удалены окончательно. Вернуть будет нечего.", danger = true, confirmLabel = "Стереть")
}

// ---------- app/mod/authors ----------

@Composable
fun ModAuthorsPage() {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    LaunchedEffect(q) { delay(300); query = q.trim(); page = 1 }
    val loader = rememberLoader(query, page) { Api.get<Paginated<Author>>("/authors", mapOf("q" to query, "page" to page, "per_page" to 50)) }
    var toDelete by remember { mutableStateOf<Author?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    ModSearch(q, { q = it }, "Поиск по имени…")
    PagedBox(loader, empty = { EmptyState("Авторов пока нет", "Авторы создаются при заполнении настроек тайтла.", Lucide.Feather) }, onPage = { page = it }) { items ->
        GlassPanel(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, a ->
                if (i > 0) HairlineDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { nav.go(Routes.author(a.id)) }) {
                        Text(a.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${a.slug} · тайтлов: ${a.titles_count}", color = Ar.textMuted, fontSize = 12.sp)
                    }
                    IconBtn(Lucide.Pencil, "Редактировать", { nav.go(Routes.authorEdit(a.id)) }, size = 32.dp, iconSize = 15.dp)
                    IconBtn(Lucide.Trash2, "Удалить", { toDelete = a }, size = 32.dp, iconSize = 15.dp, enabled = busyId != a.id, tint = Ar.danger)
                }
            }
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = {
        val a = toDelete ?: return@ConfirmDialog
        busyId = a.id; toDelete = null
        scope.launch { try { Api.delete<Unit>("/mod/authors/${a.id}"); toast("Автор удалён"); loader.drop { it.id == a.id } } catch (e: Exception) { toastError(e) }; busyId = null }
    }, title = "Удалить автора", body = "Удалить автора «${toDelete?.name}»? Тайтлы с этим автором не будут удалены.", danger = true, confirmLabel = "Удалить")
}

// ---------- app/mod/genres ----------

@Composable
fun ModGenresPage() {
    val auth = LocalAuth.current
    val isAdmin = auth.can("tags.edit")
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(page) { Api.get<Paginated<Genre>>("/genres", mapOf("page" to page, "per_page" to 50)) }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Genre?>(null) }
    var editName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Genre?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    if (isAdmin) GlassPanel(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text("Новый тег", color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArTextField(newName, { newName = it }, Modifier.weight(1f), placeholder = "Название тега")
            Spacer(Modifier.width(8.dp))
            ArButton("Создать", kind = ButtonKind.Primary, enabled = newName.isNotBlank() && !creating, busy = creating, onClick = {
                creating = true
                scope.launch { try { Api.post<Unit>("/genres", buildJsonObject { put("name", newName.trim()) }); toast("Тег создан"); newName = ""; loader.reload() } catch (e: Exception) { toastError(e) }; creating = false }
            })
        }
    }
    PagedBox(loader, empty = { EmptyState("Тегов пока нет", "Создайте первый тег выше.", Lucide.BookMarked) }, onPage = { page = it }) { items ->
        GlassPanel(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, g ->
                if (i > 0) HairlineDivider(Modifier.padding(vertical = 6.dp))
                val busy = busyId == g.id
                if (editing?.id == g.id) Row(verticalAlignment = Alignment.CenterVertically) {
                    ArTextField(editName, { editName = it }, Modifier.weight(1f))
                    IconBtn(Lucide.Check, "Сохранить", { val name = editName.trim(); if (name.isEmpty() || name == g.name) editing = null else { busyId = g.id; scope.launch { try { Api.patch<Unit>("/mod/genres/${g.id}", buildJsonObject { put("name", name) }); toast("Тег обновлён"); editing = null; loader.reload() } catch (e: Exception) { toastError(e) }; busyId = null } } }, enabled = !busy && editName.isNotBlank(), tint = Ar.ok)
                    IconBtn(Lucide.X, "Отмена", { editing = null })
                } else Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("тайтлов: ${g.titles_count}", color = Ar.textMuted, fontSize = 12.sp)
                        if (isAdmin) {
                            IconBtn(Lucide.Pencil, "Редактировать", { editing = g; editName = g.name }, size = 32.dp, iconSize = 15.dp, enabled = !busy)
                            IconBtn(Lucide.Trash2, "Удалить", { toDelete = g }, size = 32.dp, iconSize = 15.dp, enabled = !busy, tint = Ar.danger)
                        }
                    }
                    ArToggle(g.is_sensitive, { busyId = g.id; scope.launch { try { val fresh = Api.patch<Genre>("/mod/genres/${g.id}", buildJsonObject { put("is_sensitive", !g.is_sensitive) }); toast(if (fresh.is_sensitive) "Тег помечен как чувствительный" else "Отметка снята"); loader.patchItems { if (it.id == g.id) it.copy(is_sensitive = fresh.is_sensitive) else it } } catch (e: Exception) { toastError(e) }; busyId = null } }, if (g.is_sensitive) "18+ · скрыт для гостей" else "Виден всем", enabled = isAdmin && !busy)
                }
            }
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = {
        val g = toDelete ?: return@ConfirmDialog
        busyId = g.id; toDelete = null
        scope.launch { try { Api.delete<Unit>("/mod/genres/${g.id}"); toast("Тег удалён"); loader.drop { it.id == g.id } } catch (e: Exception) { toastError(e) }; busyId = null }
    }, title = "Удалить тег", body = "Удалить тег «${toDelete?.name}»? Тайтлы не будут удалены, но потеряют привязку к этому тегу.", danger = true, confirmLabel = "Удалить")
}

// ---------- app/mod/badges ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModBadgesPage() {
    val loader = rememberLoader(Unit) { Api.get<Items<Badge>>("/mod/badges").items }
    var draft by remember { mutableStateOf<Badge?>(null) }
    var saving by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Badge?>(null) }
    val scope = rememberCoroutineScope()
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner()
        is Load.Err -> ErrorState(s.message, { loader.reload() })
        is Load.Ok -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
            s.data.forEach { b ->
                GlassPanel(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { BadgeIcon(b, 40.dp) }
                    Text(b.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
                        IconBtn(Lucide.Pencil, "Редактировать", { draft = b }, size = 32.dp, iconSize = 15.dp)
                        IconBtn(Lucide.Trash2, "Удалить", { toDelete = b }, size = 32.dp, iconSize = 15.dp, tint = Ar.danger)
                    }
                }
            }
            GlassPanel(Modifier.weight(1f), borderColor = Ar.accent.copy(alpha = 0.4f), onClick = { draft = Badge(0) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Icon(Lucide.Plus, null, tint = Ar.accent, modifier = Modifier.size(32.dp)) }
                Text("Создать бейдж", color = Ar.accentHover, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
            }
        }
    }
    draft?.let { d ->
        var name by remember(d.id) { mutableStateOf(d.name) }
        var svg by remember(d.id) { mutableStateOf(d.svg) }
        ArModal(true, { draft = null }, if (d.id == 0) "Новый бейдж" else "Изменение бейджа") {
            Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(10.dp)).background(Ar.fill04), contentAlignment = Alignment.Center) {
                if (svg.isNotBlank()) BadgeIcon(Badge(d.id, name = name, svg = svg), 64.dp) else Text("Предпросмотр иконки", color = Ar.textMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            ArTextField(name, { name = it }, label = "Название", placeholder = "Название бейджа", maxLength = 40)
            Spacer(Modifier.height(10.dp))
            ArTextField(svg, { svg = it }, label = "SVG-иконка", placeholder = "<svg viewBox=\"0 0 24 24\">…</svg>", singleLine = false, minLines = 5)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                ArButton("Отмена", { draft = null }, kind = ButtonKind.Ghost)
                Spacer(Modifier.width(8.dp))
                ArButton(if (d.id == 0) "Создать" else "Сохранить", kind = ButtonKind.Primary, enabled = name.isNotBlank() && svg.isNotBlank(), busy = saving, onClick = {
                    saving = true
                    scope.launch {
                        try {
                            val body = buildJsonObject { put("name", name.trim()); put("svg", svg.trim()) }
                            if (d.id == 0) { Api.post<Unit>("/mod/badges", body); toast("Бейдж создан") } else { Api.patch<Unit>("/mod/badges/${d.id}", body); toast("Бейдж обновлён") }
                            draft = null; loader.reload()
                        } catch (e: Exception) { toastError(e) }
                        saving = false
                    }
                })
            }
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = {
        val b = toDelete ?: return@ConfirmDialog
        toDelete = null
        scope.launch { try { Api.delete<Unit>("/mod/badges/${b.id}"); toast("Бейдж удалён"); loader.update { list -> list.filter { it.id != b.id } } } catch (e: Exception) { toastError(e) } }
    }, title = "Удалить бейдж", body = "Удалить бейдж «${toDelete?.name}»? Он будет снят со всех пользователей.", danger = true, confirmLabel = "Удалить")
}

// ---------- app/mod/dmca ----------

@Composable
fun ModDmcaPage() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf("open") }
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(tab, page) { Api.get<Paginated<DmcaRequest>>("/mod/dmca", mapOf("status" to tab, "page" to page)) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    fun resolve(r: DmcaRequest, status: String) { busyId = r.id; scope.launch { try { Api.post<Unit>("/mod/dmca/${r.id}/resolve", buildJsonObject { put("status", status) }); toast(if (status == "resolved") "Заявка DMCA решена" else "Заявка DMCA отклонена"); loader.drop { it.id == r.id } } catch (e: Exception) { toastError(e) }; busyId = null } }
    @Composable fun Field(label: String, value: String) { if (value.isNotBlank()) { Text(label.uppercase(), color = Ar.textMuted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp)); Text(value, color = Ar.text, fontSize = 13.sp, lineHeight = 18.sp) } }
    @Composable fun UrlList(label: String, urls: List<String>) { val clean = urls.filter { it.isNotBlank() }; if (clean.isNotEmpty()) { Text(label.uppercase(), color = Ar.textMuted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp)); clean.forEach { u -> Text(u, color = Ar.accentHover, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { Links.external(context, u) }) } } }
    ArTabs(listOf(TabItem("open", "Ожидающие"), TabItem("resolved", "Решённые")), tab, { tab = it; page = 1 }, Modifier.padding(bottom = 12.dp), variant = TabsVariant.Underline)
    PagedBox(loader, empty = { EmptyState(if (tab == "open") "Нет ожидающих заявок" else "Нет решённых заявок", if (tab == "open") "Все заявки DMCA обработаны." else null, Lucide.Shield) }, onPage = { page = it }) { items ->
        items.forEach { r ->
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Fmt.timeAgo(r.created_at), color = Ar.textMuted, fontSize = 11.sp)
                }
                Field("Email", r.email); Field("Страна", r.country.ifBlank { "—" })
                UrlList("Материалы к удалению", r.content_urls.ifEmpty { listOf(r.content_url) })
                UrlList("Оригинальное размещение", r.original_urls)
                Field("Подтверждение авторства", r.proof_url.ifBlank { "—" })
                Field("Описание", r.description)
                Field("Примечание модератора", r.resolution_note)
                if (tab == "open") Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArButton("Удовлетворить", { resolve(r, "resolved") }, kind = ButtonKind.Primary, small = true, busy = busyId == r.id)
                    ArButton("Отклонить", { resolve(r, "dismissed") }, kind = ButtonKind.Ghost, small = true, enabled = busyId != r.id)
                } else if (r.resolved_at != null) Text("Решено ${Fmt.timeAgo(r.resolved_at)}", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
