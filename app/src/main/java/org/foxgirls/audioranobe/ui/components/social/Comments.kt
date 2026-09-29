package org.foxgirls.audioranobe.ui.components.social

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import org.foxgirls.audioranobe.ui.components.enterRise
import org.foxgirls.audioranobe.ui.components.bottomFade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.core.msg
import org.foxgirls.audioranobe.data.Comment
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Paginated
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArMarkdown
import org.foxgirls.audioranobe.ui.components.ArTabs
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.MarkdownEditor
import org.foxgirls.audioranobe.ui.components.TabItem
import org.foxgirls.audioranobe.ui.components.TabsVariant
import org.foxgirls.audioranobe.ui.components.UserAvatar
import org.foxgirls.audioranobe.ui.components.UserBadgesRow
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PER_PAGE = 20
private const val MAX_DEPTH = 5
private const val FOLD_THRESHOLD = 3
private const val REPLIES_PREVIEW = 2

@Serializable
private data class VoteRes(val score: Int = 0, val my_vote: Int = 0)

/** State of one comment thread list; kept in a class so the tree helpers can share it. */
class CommentState(val targetType: String, val targetId: Int, initial: Paginated<Comment>?) {
    var items by mutableStateOf(initial?.items ?: emptyList())
    var total by mutableStateOf(initial?.total ?: 0)
    var page by mutableStateOf(initial?.page ?: 1)
    var sort by mutableStateOf("new")
    var loading by mutableStateOf(initial == null)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf("")
    var replyingId by mutableStateOf<Int?>(null)
    var editingId by mutableStateOf<Int?>(null)
    var expanded by mutableStateOf(setOf<Int>())
    var skipFirst = initial != null
    val hasMore get() = page < (total + PER_PAGE - 1) / PER_PAGE

    suspend fun load() {
        loading = true; error = ""; replyingId = null; editingId = null; expanded = emptySet()
        try {
            val r = Api.get<Paginated<Comment>>("/comments", mapOf("target_type" to targetType, "target_id" to targetId, "sort" to sort, "page" to 1, "per_page" to PER_PAGE))
            items = r.items; total = r.total; page = r.page
        } catch (e: Exception) { items = emptyList(); total = 0; error = e.msg() } finally { loading = false }
    }

    suspend fun loadMore() {
        if (loadingMore) return
        loadingMore = true
        try {
            val r = Api.get<Paginated<Comment>>("/comments", mapOf("target_type" to targetType, "target_id" to targetId, "sort" to sort, "page" to page + 1, "per_page" to PER_PAGE))
            val seen = items.map { it.id }.toSet()
            items = items + r.items.filter { it.id !in seen }; total = r.total; page = r.page
        } catch (e: Exception) { toastError(e) } finally { loadingMore = false }
    }

    fun patch(id: Int, f: (Comment) -> Comment) { items = items.map { if (it.id == id) f(it) else it } }
}

/** components/CommentSection */
@Composable
fun CommentSection(targetType: String, targetId: Int, initial: Paginated<Comment>? = null, modifier: Modifier = Modifier, showHeading: Boolean = true) {
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val isAdmin = auth.can("comments.moderate")
    val isMod = auth.isMod
    val nav = LocalNav.current
    val st = remember(targetType, targetId) { CommentState(targetType, targetId, initial) }
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<Comment?>(null) }

    LaunchedEffect(st, st.sort) {
        if (st.skipFirst) { st.skipFirst = false; return@LaunchedEffect }
        st.load()
    }

    val byId = remember(st.items) { st.items.associateBy { it.id } }
    val children = remember(st.items) {
        val m = HashMap<Int, MutableList<Comment>>()
        for (c in st.items) if (c.parent_id != null) m.getOrPut(c.parent_id) { mutableListOf() }.add(c)
        m.values.forEach { it.sortBy { c -> c.id } }
        m
    }
    val roots = remember(st.items, isAdmin) {
        st.items.filter { c ->
            if (c.is_deleted && !isAdmin && c.parent_id == null && children[c.id].isNullOrEmpty()) return@filter false
            c.parent_id == null || !byId.containsKey(c.parent_id)
        }
    }
    val descendantCount = remember(st.items, isAdmin) {
        val m = HashMap<Int, Int>()
        fun count(id: Int): Int {
            val kids = children[id] ?: return 0
            var t = 0
            for (k in kids) t += (if (isAdmin || !k.is_deleted) 1 else 0) + count(k.id)
            m[id] = t
            return t
        }
        for (c in st.items) if (c.parent_id == null || !byId.containsKey(c.parent_id)) count(c.id)
        m
    }
    val mentionUsers = remember(st.items) { st.items.mapNotNull { it.user }.distinctBy { it.id } }

    fun vote(c: Comment, dir: Int) {
        if (user == null) { toastError("Войдите, чтобы голосовать за комментарии"); return }
        val next = if (c.my_vote == dir) 0 else dir
        val prev = c
        st.patch(c.id) { it.copy(my_vote = next, score = it.score - it.my_vote + next) }
        scope.launch {
            try {
                val r = Api.put<VoteRes>("/comments/${c.id}/vote", buildJsonObject { put("value", next) })
                st.patch(c.id) { it.copy(score = r.score, my_vote = r.my_vote) }
            } catch (e: Exception) { st.patch(c.id) { it.copy(score = prev.score, my_vote = prev.my_vote) }; toastError(e) }
        }
    }

    suspend fun post(body: String, parentId: Int?): Boolean {
        val b = body.trim()
        if (b.isEmpty()) { toastError("Комментарий не может быть пустым"); return false }
        if (b.length > Limits.commentBody) { toastError("Комментарий слишком длинный (максимум ${Limits.commentBody} символов)"); return false }
        return try {
            val c = Api.post<Comment>("/comments", buildJsonObject {
                put("target_type", targetType); put("target_id", targetId); put("body", b); if (parentId != null) put("parent_id", parentId)
            })
            if (parentId != null) { st.items = st.items + c; st.replyingId = null } else { st.items = if (st.sort == "old") st.items + c else listOf(c) + st.items; st.total++ }
            true
        } catch (e: Exception) { toastError(e); false }
    }

    suspend fun saveEdit(id: Int, body: String): Boolean {
        val b = body.trim()
        if (b.isEmpty()) { toastError("Комментарий не может быть пустым"); return false }
        return try {
            val r = Api.patch<Comment>("/comments/$id", buildJsonObject { put("body", b) })
            st.patch(id) { it.copy(body = r.body, updated_at = r.updated_at, edited_by_staff = r.edited_by_staff) }
            st.editingId = null
            true
        } catch (e: Exception) { toastError(e); false }
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showHeading) {
                Text("Комментарии", color = Ar.white, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("  ${st.total}", color = Ar.textMuted, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
            }
            ArTabs(listOf(TabItem("new", "Новые"), TabItem("old", "Старые"), TabItem("top", "Топ")), st.sort, { st.sort = it }, scrollable = false, modifier = if (showHeading) Modifier else Modifier.fillMaxWidth(), variant = if (showHeading) TabsVariant.Pill else TabsVariant.Square)
        }
        if (user != null) {
            Composer(placeholder = "Поделитесь впечатлениями… (спойлеры оборачивайте в ||двойные палочки||)", submitLabel = "Опубликовать", onSubmit = { post(it, null) })
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ar.fill04).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.LogIn, null, tint = Ar.textMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("Присоединяйтесь к обсуждению — ", color = Ar.textSecondary, fontSize = 13.sp)
                Text("войдите", color = Ar.accent, fontSize = 13.sp, modifier = Modifier.clickable { nav.go(Routes.LOGIN) })
                Text(" или ", color = Ar.textSecondary, fontSize = 13.sp)
                Text("создайте аккаунт", color = Ar.accent, fontSize = 13.sp, modifier = Modifier.clickable { nav.go(Routes.REGISTER) })
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            st.loading -> CenterSpinner(minHeight = 80.dp)
            st.error.isNotEmpty() -> EmptyState("Не удалось загрузить комментарии", st.error, Lucide.MessageSquare)
            roots.isEmpty() -> EmptyState("Комментариев пока нет", "Будьте первым, кто поделится впечатлениями.", Lucide.MessageSquare)
            else -> {
                for (root in roots) {
                    NestedComment(root, children, descendantCount, 0, false, null, st, user?.id, isMod, isAdmin, mentionUsers,
                        onVote = ::vote, onDelete = { toDelete = it },
                        onRestore = { c -> scope.launch { try { Api.post<Unit>("/mod/trash/comment/${c.id}/restore"); st.patch(c.id) { it.copy(is_deleted = false) }; toast("Комментарий восстановлен") } catch (e: Exception) { toastError(e) } } },
                        onSubmitReply = { b, pid -> post(b, pid) }, onSubmitEdit = { id, b -> saveEdit(id, b) })
                    Spacer(Modifier.height(14.dp))
                }
                if (st.hasMore) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ArButton(if (st.loadingMore) "Загрузка…" else "Показать ещё комментарии", { scope.launch { st.loadMore() } }, kind = ButtonKind.Ghost, busy = st.loadingMore)
                }
            }
        }
    }
    ConfirmDialog(toDelete != null, { toDelete = null }, {
        val c = toDelete ?: return@ConfirmDialog
        scope.launch { try { Api.delete<Unit>("/comments/${c.id}"); st.patch(c.id) { it.copy(is_deleted = true, body = if (isAdmin) it.body else "") }; toast("Комментарий удалён") } catch (e: Exception) { toastError(e) } }
    }, "Удалить комментарий", if (toDelete?.user != null && toDelete!!.user!!.id != user?.id) "Удалить этот комментарий?" else "Удалить ваш комментарий?", danger = true)
}

@Composable
private fun Composer(
    placeholder: String, submitLabel: String, initial: String = "", onSubmit: suspend (String) -> Boolean, onCancel: (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MarkdownEditor(value, { value = it }, placeholder = placeholder, maxLength = Limits.commentBody, slim = true) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            if (onCancel != null) { ArButton("Отмена", onCancel, kind = ButtonKind.Ghost, small = true); Spacer(Modifier.width(6.dp)) }
            ArButton(if (busy) "Отправка…" else submitLabel, {
                if (busy) return@ArButton
                scope.launch { busy = true; val ok = onSubmit(value); busy = false; if (ok) value = "" }
            }, kind = ButtonKind.Primary, small = true, enabled = value.isNotBlank() && !busy)
        }
    }
}

@Composable
private fun NestedComment(
    comment: Comment,
    children: Map<Int, List<Comment>>,
    descendantCount: Map<Int, Int>,
    depth: Int,
    forceOpen: Boolean,
    previewIds: Set<Int>?,
    st: CommentState,
    currentUserId: Int?,
    canModerate: Boolean,
    isAdmin: Boolean,
    mentionUsers: List<org.foxgirls.audioranobe.data.UserBrief>,
    onVote: (Comment, Int) -> Unit,
    onDelete: (Comment) -> Unit,
    onRestore: (Comment) -> Unit,
    onSubmitReply: suspend (String, Int) -> Boolean,
    onSubmitEdit: suspend (Int, String) -> Boolean,
) {
    val nav = LocalNav.current
    val own = currentUserId != null && comment.user?.id == currentUserId
    val avatarSize = maxOf(22, 34 - depth * 2).dp
    val canReply = depth < MAX_DEPTH
    var bodyExpanded by remember(comment.id) { mutableStateOf(comment.body.length <= 600) }

    @Composable
    fun child(c: Comment, force: Boolean, preview: Set<Int>?) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp)) {
            NestedComment(c, children, descendantCount, depth + 1, force, preview, st, currentUserId, canModerate, isAdmin, mentionUsers, onVote, onDelete, onRestore, onSubmitReply, onSubmitEdit)
        }
    }

    Column(Modifier.fillMaxWidth().enterRise(6.dp, 300)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(comment.user?.username, comment.user?.avatar_url, avatarSize, presence = comment.user?.presence, thumbUrl = comment.user?.avatar_thumb_url, onClick = comment.user?.let { u -> { nav.go(Routes.user(u.username)) } })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val u = comment.user
                    if (u != null) Text(u.shownName, color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { nav.go(Routes.user(u.username)) })
                    else Text("удалённый пользователь", color = Ar.textMuted, fontSize = 13.sp)
                    if (u != null) { Spacer(Modifier.width(4.dp)); UserBadgesRow(u.badges, u.is_banned, 13.dp) }
                }
                Row {
                    Text(Fmt.timeAgoShort(comment.created_at), color = Ar.textMuted, fontSize = 11.sp)
                    if (comment.updated_at != null && !comment.is_deleted) Text("  · ${if (comment.edited_by_staff) "изменено модерацией" else "изменено"}", color = Ar.textMuted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            comment.is_deleted -> {
                Text(if (isAdmin) "[удалено] — видно только администрации" else "[удалено]", color = Ar.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (isAdmin && comment.body.isNotBlank()) ArMarkdown(comment.body, compact = true)
            }
            st.editingId == comment.id -> Composer("", "Сохранить", comment.body, { onSubmitEdit(comment.id, it) }, onCancel = { st.editingId = null })
            else -> {
                Box(Modifier.animateContentSize(tween(350, easing = FastOutSlowInEasing)).then(if (bodyExpanded) Modifier else Modifier.bottomFade(0.35f))) {
                    ArMarkdown(if (bodyExpanded) comment.body else comment.body.take(600) + "…", compact = true)
                }
                if (!bodyExpanded) Text("Показать полностью", color = Ar.accent, fontSize = 12.sp, modifier = Modifier.clickable { bodyExpanded = true }.padding(vertical = 4.dp))
            }
        }
        if (comment.is_deleted) {
            if (isAdmin) Row(Modifier.padding(top = 4.dp)) { ActionBtn(Lucide.RotateCcw, "Восстановить") { onRestore(comment) } }
        } else if (st.editingId != comment.id) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape).background(Ar.fill04).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.ArrowBigUp, "Голос за", tint = if (comment.my_vote == 1) Ar.accent else Ar.textMuted, modifier = Modifier.size(26.dp).clip(CircleShape).clickable { onVote(comment, 1) }.padding(4.dp))
                    Text(comment.score.toString(), color = if (comment.my_vote != 0) Ar.accent else Ar.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Lucide.ArrowBigDown, "Голос против", tint = if (comment.my_vote == -1) Ar.accent else Ar.textMuted, modifier = Modifier.size(26.dp).clip(CircleShape).clickable { onVote(comment, -1) }.padding(4.dp))
                }
                if (canReply) ActionBtn(Lucide.CornerDownRight, "Ответить") {
                    if (currentUserId == null) toastError("Войдите, чтобы ответить") else { st.replyingId = if (st.replyingId == comment.id) null else comment.id; st.editingId = null }
                }
                if (own || canModerate) ActionBtn(Lucide.Pencil, "Изменить") { st.editingId = if (st.editingId == comment.id) null else comment.id; st.replyingId = null }
                if (own || canModerate) ActionBtn(Lucide.Trash2, "Удалить", danger = true) { onDelete(comment) }
                Spacer(Modifier.weight(1f))
                ReportButton("comment", comment.id, compact = true)
            }
        }
        if (st.replyingId == comment.id) Box(Modifier.padding(top = 8.dp)) {
            Composer(comment.user?.let { "Ответ для ${it.shownName}…" } ?: "Напишите ответ…", "Ответить", onSubmit = { onSubmitReply(it, comment.id) }, onCancel = { st.replyingId = null })
        }

        if (depth < MAX_DEPTH) {
            val kids = children[comment.id] ?: emptyList()
            if (kids.isNotEmpty()) {
                if (previewIds != null) {
                    for (k in kids.filter { it.id in previewIds }) child(k, false, previewIds)
                } else {
                    val totalBelow = descendantCount[comment.id] ?: 0
                    val foldable = !forceOpen && totalBelow > FOLD_THRESHOLD
                    if (!foldable) {
                        for (k in kids) child(k, forceOpen, null)
                    } else if (comment.id in st.expanded) {
                        for (k in kids) child(k, true, null)
                        Text("Свернуть ветку", color = Ar.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 8.dp).clickable { st.expanded = st.expanded - comment.id })
                    } else {
                        val preview = HashSet<Int>()
                        var visible = 0
                        fun walk(id: Int) {
                            for (k in children[id] ?: emptyList()) {
                                if (visible >= REPLIES_PREVIEW) return
                                val kVisible = isAdmin || !k.is_deleted
                                val hasDesc = (descendantCount[k.id] ?: 0) > 0
                                if (!kVisible && !hasDesc) continue
                                preview.add(k.id)
                                if (kVisible) visible++
                                walk(k.id)
                            }
                        }
                        walk(comment.id)
                        for (k in kids.filter { it.id in preview }) child(k, false, preview)
                        val hidden = totalBelow - visible
                        if (hidden > 0) Text("Показать ещё $hidden ${Fmt.plural(hidden, "ответ", "ответа", "ответов")}", color = Ar.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 8.dp).clickable { st.expanded = st.expanded + comment.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = if (danger) Ar.danger.copy(alpha = 0.8f) else Ar.textMuted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (danger) Ar.danger.copy(alpha = 0.8f) else Ar.textMuted, fontSize = 12.sp)
    }
}
