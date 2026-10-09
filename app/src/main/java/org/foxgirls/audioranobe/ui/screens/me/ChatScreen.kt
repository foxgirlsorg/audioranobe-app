package org.foxgirls.audioranobe.ui.screens.me

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import org.foxgirls.audioranobe.ui.swipeBack
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.data.ChatConversation
import org.foxgirls.audioranobe.data.ChatMessage
import org.foxgirls.audioranobe.data.ChatThread
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArMarkdown
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.CountBubble
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.ImageViewer
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.components.Spinner
import org.foxgirls.audioranobe.ui.components.UserAvatar
import org.foxgirls.audioranobe.ui.components.UserBadgesRow
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
private data class ConvList(val items: List<ChatConversation> = emptyList())

private const val LIST_POLL_MS = 15_000L
private const val THREAD_POLL_MS = 4_000L
private val RU = Locale.forLanguageTag("ru")

private fun dayKey(iso: String): String = Fmt.toDate(iso)?.let { SimpleDateFormat("d MMMM yyyy", RU).format(it) } ?: ""
private fun dayLabel(iso: String): String {
    val d = Fmt.toDate(iso) ?: return ""
    val overYear = System.currentTimeMillis() - d.time > 365L * 24 * 3600 * 1000
    return SimpleDateFormat(if (overYear) "d MMMM yyyy" else "d MMMM", RU).format(d)
}

/** app/me/chat: conversation list, or the thread with [userId] when given. */
@Composable
fun ChatScreen(userId: Int?) {
    if (RequireAuth()) return
    if (userId == null) ConversationList() else Thread(userId)
}

@Composable
private fun ConversationList() {
    val nav = LocalNav.current
    var convos by remember { mutableStateOf<List<ChatConversation>?>(null) }
    val bottom = LocalBottomInset.current
    LaunchedEffect(Unit) {
        while (true) {
            convos = runCatching { Api.get<ConvList>("/me/chat").items }.getOrElse { convos ?: emptyList() }
            delay(LIST_POLL_MS)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item { MeHeader("Личные", "Сообщения", "") }
        val c = convos
        when {
            c == null -> item { CenterSpinner() }
            c.isEmpty() -> item { EmptyState("Пока нет переписок", "Откройте профиль пользователя и нажмите «Написать».", Lucide.MessageCircle) }
            else -> items(c, key = { it.user.id }) { conv ->
                Row(Modifier.fillMaxWidth().clickable { nav.push(Routes.chat(conv.user.id)) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(conv.user.username, conv.user.avatar_url, 48.dp, presence = conv.user.presence, thumbUrl = conv.user.avatar_thumb_url)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text(conv.user.shownName, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(4.dp)); UserBadgesRow(conv.user.badges, conv.user.is_banned, 13.dp)
                            }
                            if (conv.last_message_at != null) { Spacer(Modifier.width(8.dp)); Text(Fmt.time(conv.last_message_at), color = Ar.textMuted, fontSize = 11.sp) }
                        }
                        val lm = conv.last_message
                        Text(if (lm == null) "Нет сообщений" else (if (lm.mine) "Вы: " else "") + (if (lm.is_deleted) "сообщение удалено" else lm.body.ifBlank { if (lm.image_url.isNotBlank()) "📷 изображение" else "" }), color = if (conv.unread > 0) Ar.text else Ar.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (conv.unread > 0) { Spacer(Modifier.width(8.dp)); CountBubble(conv.unread) }
                }
                HairlineDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thread(userId: Int) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val isMod = auth.isMod
    var thread by remember(userId) { mutableStateOf<ChatThread?>(null) }
    var loading by remember(userId) { mutableStateOf(true) }
    var text by remember(userId) { mutableStateOf("") }
    var imageUrl by remember(userId) { mutableStateOf("") }
    var showImage by remember(userId) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var replyTo by remember(userId) { mutableStateOf<ChatMessage?>(null) }
    var editing by remember(userId) { mutableStateOf<ChatMessage?>(null) }
    var plainText by remember { mutableStateOf(true) }
    var menuMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var highlight by remember { mutableStateOf<Int?>(null) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    var lastId by remember(userId) { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    LaunchedEffect(userId) {
        try {
            val t = Api.get<ChatThread>("/me/chat/$userId")
            thread = t; lastId = t.messages.lastOrNull()?.id ?: 0
            Stores.badges.refresh()
        } catch (e: Exception) { toastError(e) } finally { loading = false }
        while (true) {
            delay(THREAD_POLL_MS)
            val t = runCatching { Api.get<ChatThread>("/me/chat/$userId") }.getOrNull() ?: continue
            val prior = lastId
            val brandNew = t.messages.filter { it.id > prior }
            if (brandNew.isNotEmpty()) lastId = brandNew.maxOf { it.id }
            thread = thread?.let { prev ->
                val fresh = t.messages.associateBy { it.id }
                val updated = prev.messages.map { fresh[it.id] ?: it }
                prev.copy(user = t.user, messages = updated + brandNew, their_last_read_id = t.their_last_read_id, can_send = t.can_send)
            } ?: t
        }
    }

    // Reversed list: index 0 is the newest message (bottom).
    val visible = remember(thread?.messages) { (thread?.messages ?: emptyList()).filter { !it.is_deleted } }
    val reversed = remember(visible) { visible.reversed() }
    val count = visible.size
    LaunchedEffect(count) { if (count > 0 && listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0) }
    LaunchedEffect(listState, thread?.has_more) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { idx ->
            val t = thread ?: return@collect
            if (idx != null && idx >= reversed.size - 3 && t.has_more && !loadingMore && t.messages.isNotEmpty()) {
                loadingMore = true
                try {
                    val older = Api.get<ChatThread>("/me/chat/$userId", mapOf("before" to t.messages.first().id))
                    thread = thread?.copy(messages = older.messages + (thread?.messages ?: emptyList()), has_more = older.has_more)
                } catch (e: Exception) { toastError(e) } finally { loadingMore = false }
            }
        }
    }

    fun cancelCompose() { replyTo = null; if (editing != null) { text = ""; imageUrl = ""; showImage = false; plainText = true }; editing = null }

    fun deleteMessage(id: Int) {
        scope.launch {
            try {
                Api.delete<Unit>("/me/chat/messages/$id")
                thread = thread?.let { t -> t.copy(messages = t.messages.map { if (it.id == id) it.copy(is_deleted = true, body = "", image_url = "") else it }) }
                if (editing?.id == id) cancelCompose()
            } catch (e: Exception) { toastError(e) }
        }
    }

    fun send() {
        if (sending) return
        val body = text.trim(); val img = imageUrl.trim()
        val ed = editing
        if (ed != null) {
            if (body.isEmpty() && img.isEmpty()) { deleteMessage(ed.id); return }
            sending = true
            scope.launch {
                try {
                    val u = Api.patch<ChatMessage>("/me/chat/messages/${ed.id}", buildJsonObject { put("body", body); put("image_url", img); put("plain_text", plainText) })
                    thread = thread?.let { t -> t.copy(messages = t.messages.map { if (it.id == u.id) u else it }) }
                    editing = null; text = ""; imageUrl = ""; showImage = false; plainText = true
                } catch (e: Exception) { toastError(e) } finally { sending = false }
            }
            return
        }
        if (body.isEmpty() && img.isEmpty()) return
        sending = true
        scope.launch {
            try {
                val m = Api.post<ChatMessage>("/me/chat/$userId", buildJsonObject { put("body", body); put("image_url", img); replyTo?.let { put("reply_to_id", it.id) } ?: put("reply_to_id", JsonNull); put("plain_text", plainText) })
                thread = thread?.let { it.copy(messages = it.messages + m) }
                lastId = maxOf(lastId, m.id)
                text = ""; imageUrl = ""; showImage = false; replyTo = null; plainText = true
            } catch (e: Exception) { toastError(e) } finally { sending = false }
        }
    }

    val t = thread
    Column(Modifier.fillMaxSize().swipeBack { nav.back() }.statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text)
            if (t != null) Row(Modifier.weight(1f).clickable { nav.go(Routes.user(t.user.id)) }, verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(t.user.username, t.user.avatar_url, 38.dp, presence = t.user.presence, thumbUrl = t.user.avatar_thumb_url)
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(t.user.shownName, color = Ar.text, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.width(4.dp)); UserBadgesRow(t.user.badges, t.user.is_banned, 13.dp) }
                    Text(Fmt.presenceLabelCompact(t.user.presence, t.user.last_seen_at), color = if (t.user.presence == "online") Ar.ok else Ar.textMuted, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }
        HairlineDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && t == null -> CenterSpinner(Modifier.fillMaxSize(), 200.dp)
                t == null -> EmptyState("Не удалось открыть переписку", null, Lucide.MessageCircle)
                visible.isEmpty() -> EmptyState("Напишите первое сообщение.", null, Lucide.MessageCircle)
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState, reverseLayout = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    items(reversed, key = { it.id }) { m ->
                        val i = reversed.indexOf(m)
                        val older = reversed.getOrNull(i + 1)
                        val showDay = older == null || dayKey(older.created_at) != dayKey(m.created_at)
                        // New bubbles pop in from the bottom; edits and deletions re-flow the list smoothly.
                        val appear = remember(m.id) { MutableTransitionState(false).apply { targetState = true } }
                        BubbleAppear(appear, Modifier.animateItem()) {
                        Column(Modifier.fillMaxWidth()) {
                            if (showDay) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(dayLabel(m.created_at), color = Ar.textMuted, fontSize = 11.sp, modifier = Modifier.background(Ar.fill06, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
                            }
                            Bubble(m, t, highlighted = highlight == m.id, onLongPress = { menuMsg = m }, onImage = { viewerUrl = it }, onQuoteTap = { id ->
                                val idx = reversed.indexOfFirst { it.id == id }
                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx); highlight = id; delay(1200); if (highlight == id) highlight = null }
                            })
                        }
                        }
                    }
                    if (loadingMore) item { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { Spinner(size = 18.dp) } }
                }
            }
        }
        if (t != null && (t.can_send || editing != null)) Column(Modifier.fillMaxWidth().background(Ar.surfaceSolid)) {
            HairlineDivider()
            val ctx = editing ?: replyTo
            AnimatedVisibility(ctx != null, enter = expandVertically(tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(tween(160)) + fadeOut(tween(120))) {
            if (ctx != null) Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (editing != null) Lucide.Pencil else Lucide.CornerUpLeft, null, tint = Ar.accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (editing != null) "Редактирование" else "Ответ ${if (replyTo?.mine == true) "себе" else t.user.shownName}", color = Ar.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(ctx.body.ifBlank { if (ctx.image_url.isNotBlank()) "📷 изображение" else "" }, color = Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconBtn(Lucide.X, "Отмена", { cancelCompose() }, size = 32.dp, iconSize = 15.dp)
            }
            }
            AnimatedVisibility(showImage, enter = expandVertically(tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(tween(160)) + fadeOut(tween(120))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ArTextField(imageUrl, { imageUrl = it }, Modifier.weight(1f), placeholder = "Ссылка на изображение (https://…)", maxLength = Limits.dmImageUrl, leading = Lucide.ImagePlus)
                IconBtn(Lucide.X, "Убрать изображение", { showImage = false; imageUrl = "" }, size = 32.dp, iconSize = 15.dp)
            }
            }
            if (imageUrl.isNotBlank()) ArImage(imageUrl.trim(), Modifier.padding(horizontal = 12.dp).size(120.dp, 80.dp), shape = RoundedCornerShape(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                IconBtn(Lucide.ImagePlus, "Прикрепить изображение по ссылке", { showImage = !showImage }, tint = if (showImage) Ar.accent else Ar.textSecondary)
                if (isMod) IconBtn(if (plainText) Lucide.Type else Lucide.Sparkles, if (plainText) "Разрешить форматирование" else "Отправить как обычный текст", { plainText = !plainText }, tint = if (plainText) Ar.textSecondary else Ar.accent)
                ArTextField(text, { text = it.take(Limits.dmBody) }, Modifier.weight(1f), placeholder = if (editing != null) "Изменить сообщение…" else "Сообщение…", singleLine = false, maxLines = 6, compact = true)
                Spacer(Modifier.width(4.dp))
                val ready = text.isNotBlank() || imageUrl.isNotBlank() || editing != null
                val sendBg by animateColorAsState(if (ready) Ar.accent else Ar.fill08, tween(200), label = "sendBg")
                val sendScale by animateFloatAsState(if (ready) 1f else 0.9f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "sendScale")
                Box(Modifier.size(44.dp).scale(sendScale).clip(CircleShape).background(sendBg).clickable(enabled = !sending) { send() }, contentAlignment = Alignment.Center) {
                    AnimatedContent(if (sending) "busy" else if (editing != null) "edit" else "send", transitionSpec = { (scaleIn(tween(160)) + fadeIn(tween(160))) togetherWith (scaleOut(tween(120)) + fadeOut(tween(120))) }, label = "sendIcon") { st ->
                        when (st) {
                            "busy" -> Spinner(size = 18.dp)
                            "edit" -> Icon(Lucide.Check, "Сохранить", tint = Ar.accentOn, modifier = Modifier.size(18.dp))
                            else -> Icon(Lucide.Send, "Отправить", tint = Ar.accentOn, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else if (t != null) Text(t.block_reason.ifBlank { "Отправка сообщений этому пользователю недоступна." }, color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(14.dp))
    }

    val mm = menuMsg
    ArSheet(mm != null, { menuMsg = null }) {
        if (mm != null) {
            if (t?.can_send == true) MenuRow(Lucide.CornerUpLeft, "Ответить", { editing = null; replyTo = mm; menuMsg = null })
            if (mm.body.isNotBlank()) MenuRow(Lucide.Copy, "Копировать", { scope.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("", mm.body))) }; menuMsg = null })
            if (mm.mine) MenuRow(Lucide.Pencil, "Изменить", { replyTo = null; editing = mm; text = mm.body; imageUrl = mm.image_url; showImage = mm.image_url.isNotBlank(); plainText = mm.plain_text; menuMsg = null })
            if (mm.mine) MenuRow(Lucide.Trash2, "Удалить", { deleteMessage(mm.id); menuMsg = null }, tint = Ar.danger)
        }
    }
    ImageViewer(viewerUrl != null, listOfNotNull(viewerUrl)) { viewerUrl = null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: ChatMessage, t: ChatThread, highlighted: Boolean, onLongPress: () -> Unit, onImage: (String) -> Unit, onQuoteTap: (Int) -> Unit) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(highlighted) { if (highlighted) { pulse.snapTo(0f); pulse.animateTo(1f, tween(300)); pulse.animateTo(0f, tween(900)) } }
    val nav = LocalNav.current
    val read = m.mine && m.id <= t.their_last_read_id
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .border(3.dp, Ar.accent.copy(alpha = 0.45f * pulse.value), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (m.mine) 14.dp else 4.dp, bottomEnd = if (m.mine) 4.dp else 14.dp))
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (m.mine) 14.dp else 4.dp, bottomEnd = if (m.mine) 4.dp else 14.dp))
                .background(if (m.mine) Ar.accent.copy(alpha = 0.22f) else Ar.surfaceRaised)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            m.reply_to?.let { q ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Ar.fill06).clickable { onQuoteTap(q.id) }.padding(6.dp).padding(start = 4.dp)) {
                    Text(if (q.mine) "Вы" else q.author, color = Ar.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (q.is_deleted) "сообщение удалено" else q.excerpt, color = Ar.textMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (m.image_url.isNotBlank()) { ArImage(m.image_url, Modifier.fillMaxWidth().height(180.dp).clickable { onImage(m.image_url) }, shape = RoundedCornerShape(8.dp)); Spacer(Modifier.height(4.dp)) }
            val meta: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.edited) { Icon(Lucide.Pencil, "изменено", tint = Ar.textMuted, modifier = Modifier.size(9.dp)); Spacer(Modifier.width(3.dp)) }
                    Text(Fmt.time(m.created_at), color = Ar.textMuted, fontSize = 10.sp, lineHeight = 12.sp)
                    if (m.mine) { Spacer(Modifier.width(3.dp)); Icon(if (read) Lucide.CheckCheck else Lucide.Check, null, tint = if (read) Ar.accent else Ar.textMuted, modifier = Modifier.size(12.dp)) }
                }
            }
            if (m.body.isNotBlank()) {
                var lastLine by remember(m.body) { mutableStateOf(-1) }
                val shortMd = m.format == "rich" && m.body.length <= 28 && !m.body.contains('\n')
                MetaFlow(
                    inlineOk = m.format != "rich" || shortMd,
                    lastLineWidth = if (m.format == "rich") -1 else lastLine,
                    meta = meta,
                ) {
                    if (m.format == "rich") ArMarkdown(m.body, compact = true)
                    else LinkifiedText(m.body, onLastLine = { lastLine = it }) { Links.open(nav, it) }
                }
            } else Box(Modifier.align(Alignment.End).padding(top = 2.dp)) { meta() }
        }
    }
}

/** Body with the time riding its last line when it fits (like the site), otherwise on its own line below. */
@Composable
private fun MetaFlow(inlineOk: Boolean, lastLineWidth: Int, meta: @Composable () -> Unit, body: @Composable () -> Unit) {
    Layout({ Box { body() }; Box { meta() } }) { (bm, mm), c ->
        val gap = 6.dp.roundToPx()
        val m = mm.measure(Constraints())
        val b = bm.measure(c.copy(minWidth = 0, minHeight = 0))
        val known = lastLineWidth >= 0
        val inline = inlineOk && (if (known) lastLineWidth + gap + m.width <= c.maxWidth else b.height <= 24.dp.roundToPx())
        val last = if (known) lastLineWidth else 0
        if (inline) {
            val w = maxOf(b.width, last + gap + m.width)
            layout(w, b.height) { b.place(0, 0); m.place(w - m.width, b.height - m.height - 1.dp.roundToPx()) }
        } else {
            val w = maxOf(b.width, m.width)
            layout(w, b.height + m.height + 2.dp.roundToPx()) { b.place(0, 0); m.place(w - m.width, b.height + 2.dp.roundToPx()) }
        }
    }
}

private val URL_RE = Regex("https?://[^\\s<]+")

/** Plain-text message body with bare URLs made tappable. */
@Composable
fun LinkifiedText(body: String, onLastLine: ((Int) -> Unit)? = null, onLink: (String) -> Unit) {
    val annotated = remember(body) {
        androidx.compose.ui.text.buildAnnotatedString {
            var last = 0
            for (m in URL_RE.findAll(body)) {
                if (m.range.first > last) append(body.substring(last, m.range.first))
                var url = m.value
                val trail = Regex("[.,!?)\\]]+$").find(url)?.value ?: ""
                if (trail.isNotEmpty()) url = url.dropLast(trail.length)
                withLink(androidx.compose.ui.text.LinkAnnotation.Url(url, androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = Ar.accent))) { onLink(url) }) { append(url) }
                if (trail.isNotEmpty()) append(trail)
                last = m.range.last + 1
            }
            if (last < body.length) append(body.substring(last))
        }
    }
    Text(annotated, color = Ar.text, fontSize = 14.sp, lineHeight = 19.sp, onTextLayout = { r -> onLastLine?.invoke(kotlin.math.ceil(r.getLineRight(r.lineCount - 1)).toInt()) })
}

/** Pop-in for a chat bubble; lives outside any Column/Row scope so the plain [AnimatedVisibility] overload is used. */
@Composable
private fun BubbleAppear(state: MutableTransitionState<Boolean>, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visibleState = state, modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + scaleIn(tween(220), initialScale = 0.94f),
        exit = fadeOut(),
    ) { content() }
}
