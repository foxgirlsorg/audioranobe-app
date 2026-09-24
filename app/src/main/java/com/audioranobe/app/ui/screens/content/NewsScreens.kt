package com.audioranobe.app.ui.screens.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.Fmt
import com.audioranobe.app.core.Limits
import com.audioranobe.app.data.Announcement
import com.audioranobe.app.data.LocalAuth
import com.audioranobe.app.data.NarratorPost
import com.audioranobe.app.data.Paginated
import com.audioranobe.app.ui.LocalBottomInset
import com.audioranobe.app.ui.components.ArButton
import com.audioranobe.app.ui.components.ArImage
import com.audioranobe.app.ui.components.ArMarkdown
import com.audioranobe.app.ui.components.ArModal
import com.audioranobe.app.ui.components.ArTextField
import com.audioranobe.app.ui.components.ArToggle
import com.audioranobe.app.ui.components.ButtonKind
import com.audioranobe.app.ui.components.CenterSpinner
import com.audioranobe.app.ui.components.ConfirmDialog
import com.audioranobe.app.ui.components.EmptyState
import com.audioranobe.app.ui.components.ErrorState
import com.audioranobe.app.ui.components.GlassPanel
import com.audioranobe.app.ui.components.IconBtn
import com.audioranobe.app.ui.components.InfiniteScrollTrigger
import com.audioranobe.app.ui.components.Load
import com.audioranobe.app.ui.components.MarkdownEditor
import com.audioranobe.app.ui.components.OutlineChip
import com.audioranobe.app.ui.components.PageHeader
import com.audioranobe.app.ui.components.Section
import com.audioranobe.app.ui.components.Spinner
import com.audioranobe.app.ui.components.rememberLoader
import com.audioranobe.app.ui.components.rememberPagedList
import com.audioranobe.app.ui.components.social.CommentSection
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.nav.LocalNav
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.pagePadding
import com.audioranobe.app.ui.screens.NotFoundScreen
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.toast.toast
import com.audioranobe.app.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------- app/news ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewsScreen() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val isAdmin = user?.can("announcements.manage") == true
    val list = rememberPagedList(isAdmin) { page ->
        Api.get<Paginated<Announcement>>(if (isAdmin) "/mod/announcements" else "/announcements", mapOf("page" to page))
    }
    val scope = rememberCoroutineScope()
    var busyId by remember { mutableStateOf<Int?>(null) }
    var editor by remember { mutableStateOf<Pair<Boolean, Announcement?>?>(null) }
    var toDelete by remember { mutableStateOf<Announcement?>(null) }
    val bottom = LocalBottomInset.current

    fun patchWith(path: String, body: kotlinx.serialization.json.JsonObject, a: Announcement, ok: (Announcement) -> String) {
        busyId = a.id
        scope.launch {
            try {
                val updated = Api.patch<Announcement>(path, body)
                list.patch({ it.id == updated.id }) { updated }
                toast(ok(updated))
            } catch (e: Exception) { toastError(e) }
            busyId = null
        }
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = pagePadding()) {
        item {
            PageHeader("Что происходит на AudioRanobe", "Новости", "сайта")
            if (isAdmin) ArButton("Новое объявление", { editor = true to null }, kind = ButtonKind.Primary, icon = Lucide.Plus, modifier = Modifier.padding(bottom = 14.dp))
        }
        val items = list.items
        when {
            list.loading && items == null -> item { CenterSpinner() }
            list.error.isNotEmpty() -> item { ErrorState(list.error, { scope.launch { list.load() } }, title = "Не удалось загрузить новости") }
            items == null || items.isEmpty() -> item { EmptyState("Объявлений пока нет", "Когда команда опубликует новости, они появятся здесь.", Lucide.Newspaper) }
            else -> {
                items(items, key = { it.id }) { a ->
                    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(Ar.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                                Icon(Lucide.Megaphone, null, tint = Ar.accent, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(Fmt.date(a.created_at), color = Ar.textMuted, fontSize = 12.sp)
                            if (a.author != null) Text("  от ${a.author.username}", color = Ar.textMuted, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            if (isAdmin && !a.is_published) OutlineChip("черновик")
                            if (isAdmin && a.is_hidden) OutlineChip("не на главной", Modifier.padding(start = 4.dp))
                        }
                        Text(
                            a.title, color = Ar.white, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 8.dp).clickable { nav.go(Routes.newsItem(a.slug)) },
                        )
                        if (a.body.isNotBlank()) Box(Modifier.padding(top = 6.dp)) { ArMarkdown(a.body, compact = true, media = "both") }
                        if (a.is_published) Text(
                            "Читать полностью →", color = Ar.accentHover, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp).clickable { nav.go(Routes.newsItem(a.slug)) },
                        )
                        if (isAdmin) FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val busy = busyId == a.id
                            ArButton(
                                if (a.is_published) "Снять с публикации" else "Опубликовать", icon = if (a.is_published) Lucide.EyeOff else Lucide.Eye, small = true, busy = busy,
                                onClick = { patchWith("/mod/announcements/${a.id}", buildJsonObject { put("is_published", !a.is_published) }, a) { if (it.is_published) "Опубликовано" else "Снято с публикации" } },
                            )
                            ArButton(
                                if (a.is_hidden) "Вернуть на главную" else "Убрать с главной", icon = Lucide.House, small = true, busy = busy,
                                onClick = { patchWith("/mod/announcements/${a.id}", buildJsonObject { put("is_hidden", !a.is_hidden) }, a) { if (it.is_hidden) "Скрыто с главной" else "Показывается на главной" } },
                            )
                            ArButton("Изменить", { editor = true to a }, icon = Lucide.Pencil, small = true, busy = busy)
                            ArButton("Удалить", { toDelete = a }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true, busy = busy)
                        }
                    }
                }
                item { InfiniteScrollTrigger(list) }
            }
        }
        item { Spacer(Modifier.height(bottom)) }
    }

    editor?.let { (open, initial) ->
        if (open) AnnouncementEditor(initial, onClose = { editor = null }) { saved, created ->
            if (created) scope.launch { list.load() } else list.patch({ it.id == saved.id }) { saved }
            toast(if (created) "Объявление создано" else "Объявление обновлено")
        }
    }
    ConfirmDialog(
        toDelete != null, { toDelete = null },
        onConfirm = {
            val a = toDelete ?: return@ConfirmDialog
            busyId = a.id
            scope.launch {
                try { Api.delete<Unit>("/mod/announcements/${a.id}"); toast("Объявление удалено"); list.remove { it.id == a.id } } catch (e: Exception) { toastError(e) }
                busyId = null; toDelete = null
            }
        },
        title = "Удалить объявление", body = "Удалить „${toDelete?.title}\"? Это действие нельзя отменить.", danger = true, confirmLabel = "Удалить",
    )
}

@Composable
private fun AnnouncementEditor(initial: Announcement?, onClose: () -> Unit, onSaved: (Announcement, Boolean) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    var published by remember { mutableStateOf(initial?.is_published ?: true) }
    var hidden by remember { mutableStateOf(initial?.is_hidden ?: false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ArModal(true, onClose, if (initial != null) "Редактировать объявление" else "Новое объявление") {
        ArTextField(title, { title = it }, label = "Название", placeholder = "Что нового?", maxLength = Limits.announcementTitle)
        Spacer(Modifier.height(10.dp))
        MarkdownEditor(body, { body = it }, label = "Текст", maxLength = Limits.announcementBody, placeholder = "**Подробности**, [ссылки](https://…) — всё, что стоит знать сообществу…", media = "both", slim = true)
        Spacer(Modifier.height(10.dp))
        ArToggle(published, { published = it }, "Опубликовано")
        ArToggle(hidden, { hidden = it }, "Скрыть с главной (остаётся в разделе «Новости»)")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onClose, kind = ButtonKind.Ghost, enabled = !busy)
            Spacer(Modifier.width(8.dp))
            ArButton(if (initial != null) "Сохранить изменения" else "Создать", kind = ButtonKind.Primary, busy = busy, onClick = {
                if (title.isBlank()) { toastError("Укажите название"); return@ArButton }
                busy = true
                scope.launch {
                    try {
                        val payload = buildJsonObject { put("title", title.trim()); put("body", body); put("is_published", published); put("is_hidden", hidden) }
                        val saved = if (initial != null) Api.patch<Announcement>("/mod/announcements/${initial.id}", payload) else Api.post<Announcement>("/mod/announcements", payload)
                        onSaved(saved, initial == null)
                        onClose()
                    } catch (e: Exception) { toastError(e); busy = false }
                }
            })
        }
    }
}

// ---------- app/news/[slug] ----------

@Composable
fun NewsItemScreen(slug: String) {
    val nav = LocalNav.current
    val loader = rememberLoader(slug) { Api.get<Announcement>("/announcements/${Routes.enc(slug)}") }
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner(Modifier.fillMaxSize().statusBarsPadding(), 300.dp)
        is Load.Err -> if (s.notFound) NotFoundScreen() else Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) { ErrorState(s.message, { loader.reload() }, title = "Не удалось загрузить новость") }
        is Load.Ok -> {
            val item = s.data
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
                ArButton("Все новости", { nav.back() }, kind = ButtonKind.Ghost, icon = Lucide.ArrowLeft, small = true)
                Spacer(Modifier.height(12.dp))
                GlassPanel(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(Ar.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Lucide.Megaphone, null, tint = Ar.accent, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(Fmt.date(item.created_at), color = Ar.textMuted, fontSize = 12.sp)
                        if (item.author != null) Text("  от ${item.author.username}", color = Ar.textMuted, fontSize = 12.sp)
                    }
                    Text(item.title, color = Ar.white, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
                    ArMarkdown(item.body, media = "both")
                }
                Spacer(Modifier.height(24.dp))
                CommentSection("announcement", item.id, item.comments)
            }
        }
    }
}

// ---------- app/post/[id] ----------

@Composable
fun PostScreen(id: Int) {
    val nav = LocalNav.current
    val loader = rememberLoader(id) { Api.get<NarratorPost>("/posts/$id") }
    var editing by remember { mutableStateOf(false) }
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner(Modifier.fillMaxSize().statusBarsPadding(), 300.dp)
        is Load.Err -> if (s.notFound) NotFoundScreen() else Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) { ErrorState(s.message, { loader.reload() }, title = "Не удалось загрузить запись") }
        is Load.Ok -> {
            val post = s.data
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
                val n = post.narrator
                ArButton(n?.name ?: "Назад", { if (n != null) nav.go(Routes.narrator(n.slug)) else nav.back() }, kind = ButtonKind.Ghost, icon = Lucide.ArrowLeft, small = true)
                Spacer(Modifier.height(12.dp))
                if (editing) PostEditor(post.title, post.body, onCancel = { editing = false }) { title, body ->
                    val updated = Api.patch<NarratorPost>("/posts/$id", buildJsonObject { put("title", title); put("body", body) })
                    loader.set(updated); editing = false; toast("Запись обновлена")
                } else {
                    Text(post.title, color = Ar.white, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (n != null) Row(Modifier.clickable { nav.go(Routes.narrator(n.slug)) }, verticalAlignment = Alignment.CenterVertically) {
                            ArImage(n.avatar_url, Modifier.size(22.dp), fallbackIcon = Lucide.Mic, shape = CircleShape)
                            Spacer(Modifier.width(6.dp))
                            Text(n.name, color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(Fmt.timeAgo(post.created_at), color = Ar.textMuted, fontSize = 12.sp)
                        if (post.is_hidden) OutlineChip("скрыта", Modifier.padding(start = 8.dp))
                        Spacer(Modifier.weight(1f))
                        if (post.can_edit) IconBtn(Lucide.Pencil, "Редактировать", { editing = true }, size = 32.dp, iconSize = 14.dp)
                    }
                    Box(Modifier.padding(top = 14.dp)) { ArMarkdown(post.body, media = "both") }
                }
                Spacer(Modifier.height(24.dp))
                CommentSection("post", post.id, post.comments)
            }
        }
    }
}

@Composable
private fun PostEditor(initialTitle: String, initialBody: String, onCancel: () -> Unit, submitLabel: String = "Сохранить", onSave: suspend (String, String) -> Unit) {
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    GlassPanel(Modifier.fillMaxWidth()) {
        ArTextField(title, { title = it }, label = "Заголовок", maxLength = Limits.postTitle)
        Spacer(Modifier.height(10.dp))
        MarkdownEditor(body, { body = it }, label = "Текст", maxLength = Limits.postBody, placeholder = "**Жирный**, *курсив*, [ссылка](https://…), списки…", media = "both", slim = true)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onCancel, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton(if (busy) "Сохраняем…" else submitLabel, kind = ButtonKind.Primary, busy = busy, onClick = {
                if (title.isBlank()) { toastError("Укажите заголовок"); return@ArButton }
                busy = true
                scope.launch { try { onSave(title.trim(), body) } catch (e: Exception) { toastError(e) }; busy = false }
            })
        }
    }
}

// ---------- components/NarratorPosts ----------

private const val EXCERPT_LEN = 260

private fun excerpt(markdown: String): Pair<String, Boolean> {
    val plain = markdown
        .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
        .replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("[#>*_`~|]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (plain.length <= EXCERPT_LEN) plain to false else plain.take(EXCERPT_LEN).trimEnd() to true
}

/** Blog posts of a narrator, with inline editors for the narrator's own posts. */
@Composable
fun NarratorPosts(narratorId: Int, canEdit: Boolean, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val loader = rememberLoader(narratorId, keepOnReload = true) { Api.get<Paginated<NarratorPost>>("/narrators/$narratorId/posts", mapOf("per_page" to 50)).items }
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var toDelete by remember { mutableStateOf<NarratorPost?>(null) }
    val posts = loader.data
    if (posts != null && posts.isEmpty() && !canEdit && loader.state !is Load.Err) return

    Section("Публичные", "записи", eyebrow = "Блог", modifier = modifier) {
        if (canEdit && !creating) ArButton("Новая запись", { creating = true }, kind = ButtonKind.Primary, icon = Lucide.Plus, modifier = Modifier.padding(bottom = 12.dp))
        if (creating) Box(Modifier.padding(bottom = 12.dp)) {
            PostEditor("", "", onCancel = { creating = false }, submitLabel = "Опубликовать") { title, body ->
                Api.post<NarratorPost>("/narrators/$narratorId/posts", buildJsonObject { put("title", title); put("body", body) })
                toast("Запись опубликована — подписчики получили уведомление")
                creating = false; loader.reload()
            }
        }
        when (val s = loader.state) {
            is Load.Err -> Text(s.message, color = Ar.danger, fontSize = 13.sp)
            is Load.Loading -> Spinner()
            is Load.Ok -> if (s.data.isEmpty()) EmptyState("Записей пока нет", "Расскажите подписчикам, над чем работаете.") else s.data.forEach { p ->
                if (editingId == p.id) Box(Modifier.padding(bottom = 12.dp)) {
                    PostEditor(p.title, p.body, onCancel = { editingId = null }) { title, body ->
                        Api.patch<NarratorPost>("/posts/${p.id}", buildJsonObject { put("title", title); put("body", body) })
                        toast("Запись обновлена"); editingId = null; loader.reload()
                    }
                } else {
                    val (text, truncated) = remember(p.body) { excerpt(p.body) }
                    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { nav.go(Routes.post(p.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title, color = Ar.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (p.is_hidden) OutlineChip("скрыта", Modifier.padding(start = 8.dp))
                        }
                        if (text.isNotEmpty()) Text(
                            if (truncated) "$text… Читать дальше" else text, color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Fmt.timeAgo(p.created_at), color = Ar.textMuted, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(Lucide.MessageSquare, null, tint = Ar.textMuted, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text((p.comments_count ?: 0).toString(), color = Ar.textMuted, fontSize = 12.sp)
                            if (canEdit) {
                                Spacer(Modifier.width(6.dp))
                                IconBtn(if (p.is_hidden) Lucide.Eye else Lucide.EyeOff, if (p.is_hidden) "Показать" else "Скрыть", {
                                    scope.launch { try { Api.patch<NarratorPost>("/posts/${p.id}", buildJsonObject { put("is_hidden", !p.is_hidden) }); loader.reload() } catch (e: Exception) { toastError(e) } }
                                }, size = 28.dp, iconSize = 14.dp)
                                IconBtn(Lucide.Pencil, "Редактировать", { editingId = p.id }, size = 28.dp, iconSize = 14.dp)
                                IconBtn(Lucide.Trash2, "Удалить", { toDelete = p }, size = 28.dp, iconSize = 14.dp, tint = Ar.danger)
                            }
                        }
                    }
                }
            }
        }
    }
    ConfirmDialog(
        toDelete != null, { toDelete = null },
        onConfirm = {
            val p = toDelete ?: return@ConfirmDialog
            scope.launch { try { Api.delete<Unit>("/posts/${p.id}"); toast("Запись удалена"); toDelete = null; loader.reload() } catch (e: Exception) { toastError(e) } }
        },
        title = "Удалить запись", body = "Удалить «${toDelete?.title}»? Комментарии к ней тоже пропадут.", danger = true, confirmLabel = "Удалить",
    )
}
