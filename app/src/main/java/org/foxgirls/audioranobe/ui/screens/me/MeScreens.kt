package org.foxgirls.audioranobe.ui.screens.me

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.FriendsData
import org.foxgirls.audioranobe.data.HistoryItem
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.ModRequest
import org.foxgirls.audioranobe.data.Notification
import org.foxgirls.audioranobe.data.Paginated
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.components.AccentBadge
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArTabs
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.KeyValueRow
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.ProgressTrack
import org.foxgirls.audioranobe.ui.components.SectionTitle
import org.foxgirls.audioranobe.ui.components.StatusBadge
import org.foxgirls.audioranobe.ui.components.TabItem
import org.foxgirls.audioranobe.ui.components.TabsVariant
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.catalog.Pagination
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Gate: sends signed-out visitors to the login screen. Returns true while the gate is closed. */
@Composable
fun RequireAuth(): Boolean {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val loading by auth.loading.collectAsStateWithLifecycle()
    LaunchedEffect(loading, user) { if (!loading && user == null) nav.replace(Routes.LOGIN) }
    if (loading || user == null) { CenterSpinner(Modifier.fillMaxSize().statusBarsPadding(), 300.dp); return true }
    return false
}

/** Page header used by the /me pages: back link, eyebrow, big title with accent word. */
@Composable
fun MeHeader(eyebrow: String, title: String, accent: String, trailing: (@Composable () -> Unit)? = null) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(6.dp))
        Eyebrow(eyebrow)
        SectionTitle(title, accent, Modifier.padding(top = 4.dp, bottom = 12.dp), size = 24)
    }
}

// ---------- app/me/friends ----------

@Composable
fun FriendsScreen() {
    if (RequireAuth()) return
    val loader = rememberLoader(Unit) { Api.get<FriendsData>("/me/friends") }
    var tab by remember { mutableStateOf("friends") }
    var busyId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val bottom = LocalBottomInset.current
    fun act(id: Int, method: String, path: String, ok: String, mutate: (FriendsData) -> FriendsData) {
        if (busyId != null) return
        busyId = id
        scope.launch {
            try { if (method == "POST") Api.post<Unit>(path) else Api.delete<Unit>(path); loader.update(mutate); Stores.badges.refresh(); toast(ok) } catch (e: Exception) { toastError(e) } finally { busyId = null }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { MeHeader("Люди", "Друзья", "") }
        when (val s = loader.state) {
            is Load.Loading -> item { CenterSpinner() }
            is Load.Err -> item { ErrorState(s.message, { loader.reload() }, "Не удалось загрузить друзей") }
            is Load.Ok -> {
                val d = s.data
                item {
                    ArTabs(listOf(TabItem("friends", "В друзьях", d.friends.size), TabItem("incoming", "Входящие", d.incoming.size, accent = d.incoming.isNotEmpty()), TabItem("outgoing", "Исходящие", d.outgoing.size)), tab, { tab = it }, Modifier.padding(horizontal = 16.dp), variant = TabsVariant.Underline, scrollable = false)
                    Spacer(Modifier.height(10.dp))
                }
                when (tab) {
                    "friends" -> if (d.friends.isEmpty()) item { EmptyState("Друзей пока нет", "Найдите пользователей и отправьте заявку в друзья.", Lucide.Users) } else items(d.friends, key = { it.id }) { f ->
                        PersonRow(f, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { ArButton("Удалить", { act(f.id, "DELETE", "/friends/${f.id}", "Удалён из друзей") { it.copy(friends = it.friends.filter { x -> x.id != f.id }) } }, kind = ButtonKind.Ghost, icon = Lucide.UserMinus, small = true, enabled = busyId != f.id) }
                    }
                    "incoming" -> if (d.incoming.isEmpty()) item { EmptyState("Входящих заявок нет", "Новые заявки в друзья появятся здесь.", Lucide.Clock) } else items(d.incoming, key = { it.user.id }) { r ->
                        PersonRow(r.user, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), whenAt = r.created_at) {
                            IconBtn(Lucide.Check, "Принять", { act(r.user.id, "POST", "/friends/${r.user.id}/accept", "Заявка принята") { it.copy(incoming = it.incoming.filter { x -> x.user.id != r.user.id }, friends = listOf(r.user) + it.friends) } }, tint = Ar.ok, background = Ar.ok.copy(alpha = 0.12f), enabled = busyId != r.user.id)
                            IconBtn(Lucide.X, "Отклонить", { act(r.user.id, "DELETE", "/friends/${r.user.id}", "Заявка отклонена") { it.copy(incoming = it.incoming.filter { x -> x.user.id != r.user.id }) } }, enabled = busyId != r.user.id)
                        }
                    }
                    else -> if (d.outgoing.isEmpty()) item { EmptyState("Исходящих заявок нет", "Отправленные заявки в друзья появятся здесь.", Lucide.Clock) } else items(d.outgoing, key = { it.user.id }) { r ->
                        PersonRow(r.user, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), whenAt = r.created_at) { ArButton("Отменить", { act(r.user.id, "DELETE", "/friends/${r.user.id}", "Заявка отменена") { it.copy(outgoing = it.outgoing.filter { x -> x.user.id != r.user.id }) } }, kind = ButtonKind.Ghost, icon = Lucide.X, small = true, enabled = busyId != r.user.id) }
                    }
                }
            }
        }
    }
}

// ---------- app/me/history ----------

@Composable
fun HistoryScreen() {
    if (RequireAuth()) return
    val nav = LocalNav.current
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(page) { Api.get<Paginated<HistoryItem>>("/me/history", mapOf("page" to page)) }
    val bottom = LocalBottomInset.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { MeHeader("Где вы остановились", "История", "прослушивания") }
        when (val s = loader.state) {
            is Load.Loading -> item { CenterSpinner() }
            is Load.Err -> item { ErrorState(s.message, { loader.reload() }) }
            is Load.Ok -> {
                val d = s.data
                if (d.items.isEmpty()) item { EmptyState("Истории прослушивания пока нет", "Включите любую главу — и прогресс будет сохраняться здесь.", Lucide.History) }
                items(d.items, key = { it.chapter.id }) { h ->
                    val dur = h.chapter.duration_seconds
                    val pct = if (dur > 0) (h.position_seconds / dur).toFloat().coerceIn(0f, 1f) else 0f
                    GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), padding = PaddingValues(10.dp)) {
                        Row {
                            ArImage(h.title.cover_url, Modifier.size(56.dp, 80.dp).clickable { nav.go(Routes.title(h.title.slug)) }, fallbackIcon = Lucide.Headphones, shape = RoundedCornerShape(6.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.title.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { nav.go(Routes.title(h.title.slug)) })
                                Text("Глава ${Fmt.trimNum(h.chapter.number)}" + (if (h.chapter.name.isNotBlank()) " — ${h.chapter.name}" else ""), color = Ar.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { nav.go(Routes.chapter(h.chapter.id)) })
                                Spacer(Modifier.height(6.dp)); ProgressTrack(pct); Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${Fmt.duration(h.position_seconds)}${if (dur > 0) " / ${Fmt.duration(dur)}" else ""} · ${Fmt.timeAgo(h.updated_at)}", color = Ar.textMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Row(Modifier.background(Ar.accent, CircleShape).clickable { PlayerController.play(h.chapter.id) }.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Lucide.Play, null, tint = Ar.accentOn, modifier = Modifier.size(12.dp)); Text(" Продолжить", color = Ar.accentOn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Pagination(d.page, d.total, d.per_page) { page = it } }
            }
        }
    }
}

// ---------- app/me/notifications ----------

private val TYPE_ICONS: Map<String, ImageVector> = mapOf(
    "new_chapter" to Lucide.Headphones, "narrator_release" to Lucide.Mic, "comment_reply" to Lucide.Reply, "narrator_comment" to Lucide.MessageCircle,
    "mention" to Lucide.MessageCircle, "system" to Lucide.Megaphone, "request_reviewed" to Lucide.ClipboardCheck, "request_approved" to Lucide.ClipboardCheck,
    "request_rejected" to Lucide.CircleX, "entity_modified" to Lucide.Pencil, "entity_deleted" to Lucide.FileX, "narrator_post" to Lucide.Newspaper,
    "friend_request" to Lucide.UserPlus, "friend_accept" to Lucide.UserCheck, "narration_ready" to Lucide.Headphones, "badge_earned" to Lucide.Award,
)
private val TYPE_LABELS = mapOf(
    "new_chapter" to "Новая глава", "narrator_release" to "Релиз чтеца", "comment_reply" to "Ответ", "narrator_comment" to "Комментарий", "mention" to "Упоминание",
    "system" to "AudioRanobe", "request_reviewed" to "Заявка рассмотрена", "request_approved" to "Заявка одобрена", "request_rejected" to "Заявка отклонена",
    "entity_modified" to "Объект изменён", "entity_deleted" to "Объект удалён", "narrator_post" to "Запись чтеца", "friend_request" to "Заявка в друзья",
    "friend_accept" to "Заявка принята", "narration_ready" to "Озвучка готова", "badge_earned" to "Новый бейдж",
)

@Composable
fun NotificationsScreen() {
    if (RequireAuth()) return
    val nav = LocalNav.current
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(page) { Api.get<Paginated<Notification>>("/me/notifications", mapOf("page" to page)) }
    val badges by Stores.badges.badges.collectAsStateWithLifecycle()
    var markingAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bottom = LocalBottomInset.current
    LaunchedEffect(Unit) { Stores.badges.refresh() }
    fun markLocal(id: Int) { loader.update { d -> d.copy(items = d.items.map { if (it.id == id) it.copy(is_read = true) else it }) } }
    fun markRead(n: Notification) {
        if (n.is_read) return
        markLocal(n.id); Stores.badges.patch { it.copy(notifications = maxOf(0, it.notifications - 1)) }
        scope.launch { runCatching { Api.post<Unit>("/me/notifications/read", buildJsonObject { put("ids", JsonArray(listOf(JsonPrimitive(n.id)))) }) } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item {
            MeHeader("Входящие", "Мои", "уведомления") {
                ArButton(if (markingAll) "Отмечаем…" else "Прочитать все", {
                    if (markingAll) return@ArButton
                    markingAll = true
                    scope.launch { try { Api.post<Unit>("/me/notifications/read"); loader.update { d -> d.copy(items = d.items.map { it.copy(is_read = true) }) }; Stores.badges.patch { it.copy(notifications = 0) }; toast("Все уведомления отмечены прочитанными") } catch (e: Exception) { toastError(e) } finally { markingAll = false } }
                }, kind = ButtonKind.Ghost, icon = Lucide.CheckCheck, small = true, enabled = badges.notifications > 0)
            }
        }
        when (val s = loader.state) {
            is Load.Loading -> item { CenterSpinner() }
            is Load.Err -> item { ErrorState(s.message, { loader.reload() }) }
            is Load.Ok -> {
                val d = s.data
                if (d.items.isEmpty()) item { EmptyState("Уведомлений нет", "Новые главы, ответы и релизы чтецов будут появляться здесь.", Lucide.Bell) }
                items(d.items, key = { it.id }) { n ->
                    GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), padding = PaddingValues(12.dp), borderColor = if (n.is_read) Ar.border else Ar.accent.copy(alpha = 0.35f), onClick = { markRead(n); if (n.link.isNotBlank()) nav.openLink(n.link) }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(34.dp).background(if (n.is_read) Ar.fill04 else Ar.accentSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(TYPE_ICONS[n.type] ?: Lucide.Bell, null, tint = if (n.is_read) Ar.textMuted else Ar.accent, modifier = Modifier.size(16.dp)) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text((TYPE_LABELS[n.type] ?: n.type).uppercase(), color = Ar.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                Text(n.body, color = if (n.is_read) Ar.textSecondary else Ar.text, fontSize = 13.sp, lineHeight = 18.sp)
                                Text(Fmt.timeAgo(n.created_at), color = Ar.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                            if (!n.is_read) IconBtn(Lucide.Check, "Отметить прочитанным", { markRead(n) }, size = 30.dp, iconSize = 16.dp)
                        }
                    }
                }
                item { Pagination(d.page, d.total, d.per_page) { page = it } }
            }
        }
    }
}

// ---------- app/me/requests ----------

val ENTITY_LABELS = mapOf("narrator" to "чтец", "title" to "тайтл", "chapter" to "глава", "author" to "автор")
val ACTION_LABELS = mapOf("create" to "создание", "update" to "изменение", "delete" to "удаление", "transfer" to "передача")
val FIELD_LABELS = mapOf(
    "name" to "название", "bio" to "описание", "socials" to "соцсети", "alt_names" to "альтернативные названия", "author" to "автор", "description" to "описание",
    "year" to "год", "release_status" to "статус выхода", "genre_ids" to "теги", "narrator_id" to "ID чтеца", "volume_id" to "ID тома", "number" to "номер",
)

fun jsonValueText(v: kotlinx.serialization.json.JsonElement?): String = when (v) {
    null, JsonNull -> "—"
    is JsonPrimitive -> v.content.ifEmpty { "—" }
    is JsonArray -> v.joinToString(", ") { if (it is JsonPrimitive) it.content else it.toString() }.ifEmpty { "—" }
    is JsonObject -> v.toString()
}

fun ModRequest.entityName(): String? = entity?.get("name")?.let { (it as? JsonPrimitive)?.content }?.takeIf { it.isNotBlank() }

@Composable
fun RequestCard(r: ModRequest, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    GlassPanel(modifier, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AccentBadge(ENTITY_LABELS[r.entity_type] ?: r.entity_type, color = Ar.blue)
            AccentBadge(ACTION_LABELS[r.action] ?: r.action)
            Text(r.entityName() ?: r.entity_id?.let { "#$it" } ?: "", color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            StatusBadge(r.status)
        }
        Spacer(Modifier.height(8.dp))
        if (r.payload.isEmpty()) Text("Изменений полей нет.", color = Ar.textMuted, fontSize = 12.sp)
        for ((k, v) in r.payload) KeyValueRow(FIELD_LABELS[k] ?: k.replace('_', ' '), jsonValueText(v).let { if (it.length > 140) it.take(139) + "…" else it })
        if (r.review_note.isNotBlank()) Column(Modifier.padding(top = 6.dp).fillMaxWidth().background(Ar.fill04, RoundedCornerShape(8.dp)).padding(8.dp)) {
            Text((if (r.status == "rejected") "Причина отклонения" else "Комментарий модератора").uppercase(), color = Ar.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(r.review_note, color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        extra?.invoke()
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Отправлено ${Fmt.timeAgo(r.created_at)}", color = Ar.textMuted, fontSize = 11.sp)
            Text(if (r.reviewed_at != null) "Рассмотрено ${Fmt.timeAgo(r.reviewed_at)}" else "Ожидает рассмотрения", color = Ar.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun RequestsScreen() {
    if (RequireAuth()) return
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(page) { Api.get<Paginated<ModRequest>>("/panel/requests", mapOf("page" to page)) }
    var retrying by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val bottom = LocalBottomInset.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { MeHeader("История модерации", "Мои", "заявки") }
        when (val s = loader.state) {
            is Load.Loading -> item { CenterSpinner() }
            is Load.Err -> item { ErrorState(s.message, { loader.reload() }, "Не удалось загрузить заявки") }
            is Load.Ok -> {
                val d = s.data
                if (d.items.isEmpty()) item { EmptyState("Пока нет заявок", "Когда вы создаёте или редактируете чтецов, тайтлы и главы, ваши заявки на модерацию появляются здесь.", Lucide.ClipboardList) }
                items(d.items, key = { it.id }) { r ->
                    RequestCard(r, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        if (r.status == "rejected" && r.can_retry) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ArButton(if (retrying == r.id) "Отправляем…" else "Отправить повторно", {
                                retrying = r.id
                                scope.launch { try { val u = Api.post<ModRequest>("/panel/requests/${r.id}/retry"); loader.update { d2 -> d2.copy(items = d2.items.map { if (it.id == r.id) u else it }) }; toast("Заявка отправлена повторно") } catch (e: Exception) { toastError(e) } finally { retrying = null } }
                            }, icon = Lucide.RotateCcw, small = true, busy = retrying == r.id)
                            if (r.retry_count > 0) Text("  Попытка ${r.retry_count} из 3", color = Ar.textMuted, fontSize = 11.sp)
                        }
                        if (r.status == "rejected" && !r.can_retry && r.retry_count >= 3) Text("Исчерпано попыток (${r.retry_count}/3)", color = Ar.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item { Pagination(d.page, d.total, d.per_page) { page = it } }
            }
        }
    }
}

/** Compact JSON value printing for details maps (audit). */
fun JsonObject.shortText(): String = entries.joinToString(", ") { (k, v) -> "$k: ${jsonValueText(v)}" }

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content
fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
fun kotlinx.serialization.json.JsonElement?.asText(): String = if (this == null) "" else jsonValueText(this).let { if (it == "—") "" else it }
fun kotlinx.serialization.json.JsonElement?.primitiveContent(): String? = (this as? JsonPrimitive)?.jsonPrimitive?.content
