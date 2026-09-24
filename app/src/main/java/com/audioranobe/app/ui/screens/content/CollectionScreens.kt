package com.audioranobe.app.ui.screens.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.Fmt
import com.audioranobe.app.core.Limits
import com.audioranobe.app.data.CollectionCard
import com.audioranobe.app.data.CollectionFull
import com.audioranobe.app.data.LocalAuth
import com.audioranobe.app.data.Paginated
import com.audioranobe.app.data.SearchSuggest
import com.audioranobe.app.data.SuggestTitle
import com.audioranobe.app.ui.components.AccentBadge
import com.audioranobe.app.ui.components.ArButton
import com.audioranobe.app.ui.components.ArImage
import com.audioranobe.app.ui.components.ArModal
import com.audioranobe.app.ui.components.ArTabs
import com.audioranobe.app.ui.components.ArTextField
import com.audioranobe.app.ui.components.ArToggle
import com.audioranobe.app.ui.components.ButtonKind
import com.audioranobe.app.ui.components.CenterSpinner
import com.audioranobe.app.ui.components.CollectionCardC
import com.audioranobe.app.ui.components.ConfirmDialog
import com.audioranobe.app.ui.components.EmptyState
import com.audioranobe.app.ui.components.ErrorState
import com.audioranobe.app.ui.components.Eyebrow
import com.audioranobe.app.ui.components.GlassPanel
import com.audioranobe.app.ui.components.IconBtn
import com.audioranobe.app.ui.components.Load
import com.audioranobe.app.ui.components.PageHeader
import com.audioranobe.app.ui.components.Spinner
import com.audioranobe.app.ui.components.TabItem
import com.audioranobe.app.ui.components.TabsVariant
import com.audioranobe.app.ui.components.TextPromptDialog
import com.audioranobe.app.ui.components.TitleCardC
import com.audioranobe.app.ui.components.UserAvatar
import com.audioranobe.app.ui.components.gridColumns
import com.audioranobe.app.ui.components.rememberLoader
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.nav.LocalNav
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.pagePadding
import com.audioranobe.app.ui.screens.NotFoundScreen
import com.audioranobe.app.ui.screens.catalog.Pagination
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.toast.toast
import com.audioranobe.app.ui.toast.toastError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------- app/collections ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionsScreen() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    var qInput by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("popular") }
    var page by remember { mutableIntStateOf(1) }
    LaunchedEffect(qInput) { delay(400); val t = qInput.trim(); if (t != q) { q = t; page = 1 } }
    val loader = rememberLoader(q, sort, page, keepOnReload = true) {
        Api.get<Paginated<CollectionCard>>("/collections", mapOf("q" to q, "sort" to sort, "page" to page))
    }
    var createOpen by remember { mutableStateOf(false) }
    val columns = gridColumns(LocalConfiguration.current.screenWidthDp, minCard = 150)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        PageHeader("Собрано слушателями", "Коллекции", "сообщества")
        ArTextField(qInput, { qInput = it }, placeholder = "Поиск коллекций…", leading = Lucide.Search)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArTabs(listOf(TabItem("popular", "Популярное"), TabItem("new", "Новые")), sort, { sort = it; page = 1 }, Modifier.weight(1f), variant = TabsVariant.Square)
            if (user != null) {
                Spacer(Modifier.width(8.dp))
                ArButton("Новая", { createOpen = true }, kind = ButtonKind.Primary, icon = Lucide.Plus, small = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        when (val s = loader.state) {
            is Load.Loading -> CenterSpinner()
            is Load.Err -> ErrorState(s.message, { loader.reload() }, title = "Не удалось загрузить коллекции")
            is Load.Ok -> {
                val data = s.data
                if (data.items.isEmpty()) EmptyState(
                    "Коллекции не найдены",
                    if (q.isNotEmpty()) "По этому запросу ничего не нашлось." else if (user != null) "Станьте первым — соберите коллекцию из любимых тайтлов." else "Войдите, чтобы создать первую коллекцию.",
                    Lucide.Library,
                ) else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = columns) {
                        data.items.forEach { c -> CollectionCardC(c, Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Pagination(data.page, data.total, data.per_page) { page = it }
                }
            }
        }
    }

    CollectionFormDialog(createOpen, { createOpen = false }, null) { created -> createOpen = false; toast("Коллекция создана"); nav.go(Routes.collection(created.id)) }
}

/** Create / edit dialog for a collection's name, description and (on create) visibility. */
@Composable
private fun CollectionFormDialog(open: Boolean, onClose: () -> Unit, initial: CollectionFull?, onSaved: (CollectionFull) -> Unit) {
    if (!open) return
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var description by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var isPublic by remember(initial) { mutableStateOf(initial?.is_public ?: true) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ArModal(true, onClose, if (initial == null) "Новая коллекция" else "Изменить коллекцию") {
        ArTextField(name, { name = it }, label = "Название", maxLength = Limits.collectionName, placeholder = "Например: Уютная осенняя подборка")
        Spacer(Modifier.height(10.dp))
        ArTextField(description, { description = it }, label = "Описание", maxLength = Limits.collectionDescription, placeholder = "Что объединяет эти тайтлы? (необязательно)", singleLine = false, minLines = 3)
        if (initial == null) {
            Spacer(Modifier.height(10.dp))
            ArToggle(isPublic, { isPublic = it }, "Публичная коллекция", "Любой сможет найти её и поставить лайк")
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onClose, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton(
                if (busy) (if (initial == null) "Создаём…" else "Сохраняем…") else (if (initial == null) "Создать" else "Сохранить"),
                kind = ButtonKind.Primary, enabled = name.isNotBlank() && !busy, busy = busy,
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty() || trimmed.length > 100) { toastError("Название коллекции — от 1 до 100 символов"); return@ArButton }
                    busy = true
                    scope.launch {
                        try {
                            val saved = if (initial == null) Api.post<CollectionFull>("/collections", buildJsonObject { put("name", trimmed); put("description", description.trim()); put("is_public", isPublic) })
                            else Api.patch<CollectionFull>("/collections/${initial.id}", buildJsonObject { put("name", trimmed); put("description", description.trim()) })
                            onSaved(saved)
                        } catch (e: Exception) { toastError(e) }
                        busy = false
                    }
                },
            )
        }
    }
}

// ---------- app/collections/[id] ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionScreen(id: Int) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val loader = rememberLoader(id) { Api.get<CollectionFull>("/collections/$id") }
    val scope = rememberCoroutineScope()
    var likeBusy by remember { mutableStateOf(false) }
    var visBusy by remember { mutableStateOf(false) }
    var moveBusy by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf<Int?>(null) }
    var noteInitial by remember { mutableStateOf("") }
    val columns = gridColumns(LocalConfiguration.current.screenWidthDp)

    when (val s = loader.state) {
        is Load.Loading -> { CenterSpinner(Modifier.fillMaxSize().statusBarsPadding(), 300.dp); return }
        is Load.Err -> {
            if (s.notFound) { NotFoundScreen("Коллекция не найдена. Возможно, она приватная или удалена."); return }
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) { ErrorState(s.message, { loader.reload() }, title = "Не удалось загрузить эту коллекцию") }
            return
        }
        is Load.Ok -> {}
    }
    val col = loader.data ?: return
    val isOwner = user != null && user!!.id == col.user.id
    val canEditMeta = isOwner || auth.isMod || col.can_edit
    val inCollection = remember(col) { col.items.map { it.title.id }.toSet() }

    fun apply(fresh: CollectionFull) = loader.set(fresh)
    fun toggleLike() {
        if (likeBusy) return
        if (user == null) { toastError("Войдите, чтобы лайкать коллекции"); return }
        likeBusy = true
        scope.launch {
            try {
                val res = if (col.my_like) Api.delete<LikeResult>("/collections/${col.id}/like") else Api.put<LikeResult>("/collections/${col.id}/like")
                loader.update { it.copy(my_like = res.my_like, likes_count = res.likes_count) }
            } catch (e: Exception) { toastError(e) }
            likeBusy = false
        }
    }
    fun toggleVisibility() {
        if (visBusy) return
        visBusy = true
        scope.launch {
            try {
                val fresh = Api.patch<CollectionFull>("/collections/${col.id}", buildJsonObject { put("is_public", !col.is_public) })
                apply(fresh); toast(if (fresh.is_public) "Коллекция теперь публичная" else "Коллекция теперь приватная")
            } catch (e: Exception) { toastError(e) }
            visBusy = false
        }
    }
    fun removeItem(titleId: Int) = scope.launch {
        try { apply(Api.delete<CollectionFull>("/collections/${col.id}/items/$titleId")) } catch (e: Exception) { toastError(e) }
    }
    fun move(index: Int, dir: Int) {
        if (moveBusy) return
        val j = index + dir
        if (j < 0 || j >= col.items.size) return
        val a = col.items[index]; val b = col.items[j]
        moveBusy = true
        scope.launch {
            try {
                if (a.position == b.position) {
                    apply(Api.put<CollectionFull>("/collections/${col.id}/items/${a.title.id}", buildJsonObject { put("position", b.position + dir) }))
                } else {
                    Api.put<CollectionFull>("/collections/${col.id}/items/${a.title.id}", buildJsonObject { put("position", b.position) })
                    apply(Api.put<CollectionFull>("/collections/${col.id}/items/${b.title.id}", buildJsonObject { put("position", a.position) }))
                }
            } catch (e: Exception) { toastError(e) }
            moveBusy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text)
        }
        GlassPanel {
            Eyebrow("Коллекция")
            Text(col.name, color = Ar.white, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 30.sp, modifier = Modifier.padding(top = 4.dp))
            if (col.description.isNotBlank()) Text(col.description, color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.clickable { nav.go(Routes.user(col.user.id)) }, verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(col.user.username, col.user.avatar_url, 22.dp, thumbUrl = col.user.avatar_thumb_url)
                    Spacer(Modifier.width(6.dp))
                    Text(col.user.display_name.ifBlank { col.user.username }, color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Text("· Тайтлов: ${col.items_count}", color = Ar.textMuted, fontSize = 13.sp)
                Text("· Обновлено ${Fmt.date(col.updated_at)}", color = Ar.textMuted, fontSize = 13.sp)
                if (!col.is_public) AccentBadge("Приватная", icon = Lucide.Lock, color = Ar.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val liked = col.my_like
                Row(
                    Modifier.clip(CircleShape).background(if (liked) Ar.accent.copy(alpha = 0.14f) else Ar.fill04)
                        .border(1.dp, if (liked) Ar.accent.copy(alpha = 0.5f) else Ar.border, CircleShape)
                        .clickable(enabled = !likeBusy) { toggleLike() }.heightIn(min = 34.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.Heart, null, tint = if (liked) Ar.accent else Ar.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(col.likes_count.toString(), color = if (liked) Ar.accent else Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (isOwner) ArButton("Добавить тайтлы", { addOpen = true }, kind = ButtonKind.Primary, icon = Lucide.Plus, small = true)
                if (canEditMeta) {
                    ArButton("Изменить", { editOpen = true }, icon = Lucide.Pencil, small = true)
                    ArButton(if (col.is_public) "Публичная" else "Приватная", { toggleVisibility() }, icon = if (col.is_public) Lucide.Globe else Lucide.Lock, small = true, busy = visBusy)
                    ArButton("Удалить", { deleteOpen = true }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (col.items.isEmpty()) EmptyState(
            "Пока нет тайтлов",
            if (isOwner) "Нажмите «Добавить тайтлы», чтобы начать собирать коллекцию." else "Владелец пока не добавил ни одного тайтла.",
            Lucide.Library,
        ) else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = columns) {
                col.items.forEachIndexed { index, item ->
                    Column(Modifier.weight(1f)) {
                        TitleCardC(item.title)
                        if (item.note.isNotBlank()) Text(item.note, color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                        if (isOwner) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconBtn(Lucide.ArrowUp, "Переместить вверх", { move(index, -1) }, size = 28.dp, iconSize = 14.dp, enabled = index > 0 && !moveBusy)
                            IconBtn(Lucide.ArrowDown, "Переместить вниз", { move(index, 1) }, size = 28.dp, iconSize = 14.dp, enabled = index < col.items.size - 1 && !moveBusy)
                            IconBtn(Lucide.StickyNote, if (item.note.isNotBlank()) "Изменить заметку" else "Добавить заметку", { noteInitial = item.note; noteFor = item.title.id }, size = 28.dp, iconSize = 14.dp)
                            IconBtn(Lucide.X, "Убрать из коллекции", { removeItem(item.title.id) }, size = 28.dp, iconSize = 14.dp, tint = Ar.danger)
                        }
                    }
                }
                // Keep the last row aligned with the grid when it is not full.
                val rem = col.items.size % columns
                if (rem != 0) repeat(columns - rem) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    CollectionFormDialog(editOpen, { editOpen = false }, col) { fresh -> apply(fresh); editOpen = false; toast("Коллекция обновлена") }
    ConfirmDialog(
        deleteOpen, { deleteOpen = false },
        onConfirm = {
            scope.launch {
                try { Api.delete<Unit>("/collections/${col.id}"); toast("Коллекция удалена"); nav.replace(Routes.COLLECTIONS) } catch (e: Exception) { toastError(e) }
            }
        },
        title = "Удалить коллекцию?", body = "Коллекция «${col.name}» будет удалена навсегда. Это действие нельзя отменить.", danger = true, confirmLabel = "Удалить",
    )
    TextPromptDialog(
        noteFor != null, { noteFor = null }, title = "Заметка", initial = noteInitial, placeholder = "Короткая заметка об этом тайтле…", maxLength = Limits.collectionNote,
    ) { text ->
        val tid = noteFor ?: return@TextPromptDialog
        apply(Api.put<CollectionFull>("/collections/${col.id}/items/$tid", buildJsonObject { put("note", text.trim()) }))
        noteFor = null
    }
    AddTitlesDialog(addOpen, { addOpen = false }, inCollection) { titleId ->
        apply(Api.put<CollectionFull>("/collections/${col.id}/items/$titleId", buildJsonObject { }))
        toast("Добавлено в коллекцию")
    }
}

@kotlinx.serialization.Serializable
private data class LikeResult(val my_like: Boolean = false, val likes_count: Int = 0)

@Composable
private fun AddTitlesDialog(open: Boolean, onClose: () -> Unit, inCollection: Set<Int>, onAdd: suspend (Int) -> Unit) {
    if (!open) return
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SuggestTitle>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var addingId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        delay(300)
        searching = true
        results = try { Api.get<SearchSuggest>("/search/suggest", mapOf("q" to q)).titles } catch (_: Exception) { emptyList() }
        searching = false
    }
    ArModal(true, onClose, "Добавить тайтлы") {
        ArTextField(query, { query = it }, placeholder = "Поиск тайтлов… (минимум 2 символа)", leading = Lucide.Search)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().heightIn(min = 80.dp)) {
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Spinner(size = 20.dp) }
                query.trim().length < 2 -> Text("Введите хотя бы 2 символа для поиска.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                results.isEmpty() -> Text("Ничего не найдено.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                else -> results.forEach { st ->
                    val already = st.id in inCollection
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArImage(st.cover_thumb_url ?: st.cover_url, Modifier.width(36.dp).height(48.dp), fallbackIcon = Lucide.Library, shape = RoundedCornerShape(6.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(st.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (st.author != null) Text(st.author.name, color = Ar.textMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (already) Text("Добавлено", color = Ar.ok, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        else ArButton(
                            if (addingId == st.id) "Добавляем…" else "Добавить", kind = ButtonKind.Primary, small = true, enabled = addingId == null,
                            onClick = { addingId = st.id; scope.launch { try { onAdd(st.id) } catch (e: Exception) { toastError(e) }; addingId = null } },
                        )
                    }
                }
            }
        }
    }
}
