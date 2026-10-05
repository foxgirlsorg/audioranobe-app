package org.foxgirls.audioranobe.ui.screens.me

import org.foxgirls.audioranobe.ui.components.ditheredBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.Labels
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.data.Badge
import org.foxgirls.audioranobe.data.CollectionCard
import org.foxgirls.audioranobe.data.Comment
import org.foxgirls.audioranobe.data.LibraryEntry
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Me
import org.foxgirls.audioranobe.data.Paginated
import org.foxgirls.audioranobe.data.TitleCard
import org.foxgirls.audioranobe.data.UserBrief
import org.foxgirls.audioranobe.data.UserProfile
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.dockScrollAware
import org.foxgirls.audioranobe.ui.swipeTabs
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArMarkdown
import org.foxgirls.audioranobe.ui.components.ArModal
import org.foxgirls.audioranobe.ui.components.ArTabs
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CardGrid
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.CollectionCardC
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.ImageViewer
import org.foxgirls.audioranobe.ui.components.InfiniteScrollTrigger
import org.foxgirls.audioranobe.ui.components.ListeningHeatmap
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.PagedList
import org.foxgirls.audioranobe.ui.components.PresenceDot
import org.foxgirls.audioranobe.ui.components.SelectMenu
import org.foxgirls.audioranobe.ui.components.SelectOption
import org.foxgirls.audioranobe.ui.components.TabItem
import org.foxgirls.audioranobe.ui.components.TabsVariant
import org.foxgirls.audioranobe.ui.components.UserAvatar
import org.foxgirls.audioranobe.ui.components.UserBadgesRow
import org.foxgirls.audioranobe.ui.components.UserStatsCharts
import org.foxgirls.audioranobe.ui.components.pickers.Chip
import org.foxgirls.audioranobe.ui.components.pickers.SocialsEditor
import org.foxgirls.audioranobe.ui.components.rememberImageCropper
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.components.rememberPagedList
import org.foxgirls.audioranobe.ui.components.social.SocialLinks
import org.foxgirls.audioranobe.ui.icons.Lucide
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
private data class FriendRes(val status: String = "none")

private val LIB_EMPTY = mapOf("all" to "В библиотеке пока пусто.", "planning" to "В планах пока ничего нет.", "in_progress" to "Сейчас ничего не слушает.", "completed" to "Прослушанных тайтлов пока нет.", "dropped" to "Ничего не брошено.")
private val OWN_LIB_EMPTY = mapOf("all" to "Ваша библиотека пуста. Найдите что-нибудь в каталоге и добавьте в список.", "planning" to "В планах пока пусто. Загляните в каталог и выберите, что послушать дальше.", "in_progress" to "Сейчас вы ничего не слушаете.", "completed" to "Завершённых тайтлов пока нет — они появятся здесь, когда вы что-нибудь дослушаете.", "dropped" to "Ничего не брошено. Так держать!")

/** app/user/[id]: public profile with library, favorites, comments, collections, friends. */
private val USER_TABS = listOf("info", "library", "favorites", "comments", "collections", "friends")

@Composable
fun UserScreen(userRef: String, initialTab: String?) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val viewer by auth.user.collectAsStateWithLifecycle()
    val loader = rememberLoader(userRef, viewer?.id, keepOnReload = true) { Api.get<UserProfile>("/users/${Routes.enc(userRef)}") }
    val bottom = LocalBottomInset.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(initialTab?.takeIf { it in setOf("info", "library", "favorites", "comments", "collections", "friends") } ?: "info") }
    var libStatus by remember { mutableStateOf("all") }
    var friendStatus by remember { mutableStateOf<String?>(null) }
    var friendsCount by remember { mutableStateOf<Int?>(null) }
    var friendBusy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var avatarViewer by remember { mutableStateOf(false) }

    when (val s = loader.state) {
        is Load.Loading -> { CenterSpinner(Modifier.fillMaxSize().statusBarsPadding(), 400.dp); return }
        is Load.Err -> { if (s.notFound) NotFoundScreen("Такого пользователя не существует.") else Box(Modifier.statusBarsPadding()) { ErrorState(s.message, { loader.reload() }, "Пользователь не найден") }; return }
        is Load.Ok -> {}
    }
    val profile = loader.data!!
    val user = profile.user
    val stats = profile.stats
    LaunchedEffect(profile.friendship) { friendStatus = profile.friendship.status; friendsCount = profile.friendship.friends_count }
    val isOwn = viewer?.id == user.id
    val canEditLibrary = viewer != null && (isOwn || auth.can("users.edit"))
    val libraryTotal = stats.planning + stats.in_progress + stats.completed + stats.dropped

    val library = rememberPagedList<LibraryEntry>(userRef, libStatus) { p -> Api.get("/users/${Routes.enc(userRef)}/library", mapOf("status" to libStatus.takeIf { it != "all" }, "page" to p)) }
    val comments = rememberPagedList<Comment>(userRef) { p -> Api.get("/users/${Routes.enc(userRef)}/comments", mapOf("page" to p)) }
    val favorites = rememberPagedList<TitleCard>(userRef) { p -> Api.get("/users/${Routes.enc(userRef)}/favorites", mapOf("page" to p)) }
    val collections = rememberPagedList<CollectionCard>(user.username) { p -> Api.get("/collections", mapOf("user" to user.username, "page" to p)) }
    val friends = rememberPagedList<UserBrief>(userRef) { p -> Api.get("/users/${Routes.enc(userRef)}/friends", mapOf("page" to p)) }

    fun runFriend(method: String, path: String, ok: String) {
        if (friendBusy) return
        friendBusy = true
        scope.launch {
            try {
                val prev = friendStatus
                val r = if (method == "POST") Api.post<FriendRes>(path) else Api.delete<FriendRes>(path)
                friendStatus = r.status
                if (r.status == "friends" && prev != "friends") friendsCount = (friendsCount ?: 0) + 1
                else if (r.status != "friends" && prev == "friends") friendsCount = maxOf(0, (friendsCount ?: 1) - 1)
                org.foxgirls.audioranobe.data.Stores.badges.refresh()
                toast(ok)
            } catch (e: Exception) { toastError(e) } finally { friendBusy = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize().dockScrollAware().swipeTabs(USER_TABS, tab) { tab = it }, contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(170.dp)) {
                    if (user.cover_url != null) ArImage(user.cover_url, Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize().ditheredBackground(Brush.linearGradient(listOf(Ar.accent.copy(alpha = 0.25f), Ar.surfaceSolid))))
                    Box(Modifier.fillMaxSize().ditheredBackground(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, Ar.bg.copy(alpha = 0.85f)))))
                }
                Row(Modifier.statusBarsPadding().padding(8.dp)) {
                    IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text, background = Ar.bg.copy(alpha = 0.5f))
                }
                Column(Modifier.fillMaxWidth().padding(top = 120.dp).padding(horizontal = 16.dp)) {
                    Box {
                        Box(Modifier.size(96.dp).clip(CircleShape).border(3.dp, Ar.bg, CircleShape).clickable(enabled = user.avatar_url != null) { avatarViewer = true }) {
                            UserAvatar(user.username, user.avatar_url, 96.dp, thumbUrl = user.avatar_thumb_url)
                        }
                        PresenceDot(user.presence, Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-6).dp), size = 16.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.shownName, color = Ar.white, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        UserBadgesRow(user.badges, user.is_banned, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        if (isOwn) IconBtn(Lucide.Pencil, "Редактировать", { nav.go(Routes.settings()) }, size = 32.dp, iconSize = 15.dp)
                        else if (auth.can("users.edit")) IconBtn(Lucide.Pencil, "Редактировать", { editing = true }, size = 32.dp, iconSize = 15.dp)
                    }
                    Text(listOfNotNull(
                        if (user.display_name.isNotBlank()) "@${user.username}" else null, user.role_name.takeIf { it.isNotBlank() },
                        Fmt.presenceLabel(user.presence, user.last_seen_at), "На сайте с ${Fmt.date(user.created_at)}",
                    ).joinToString(" · "), color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
                    if (viewer != null && !isOwn) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (profile.can_message) ArButton("Написать", { nav.go(Routes.chat(user.id)) }, icon = Lucide.MessageSquare, small = true)
                        when (friendStatus) {
                            "none" -> ArButton("В друзья", { runFriend("POST", "/friends/${user.id}", "Заявка отправлена") }, kind = ButtonKind.Primary, icon = Lucide.UserPlus, small = true, busy = friendBusy)
                            "outgoing" -> ArButton("Отменить заявку", { runFriend("DELETE", "/friends/${user.id}", "Заявка отменена") }, kind = ButtonKind.Ghost, icon = Lucide.Clock, small = true, busy = friendBusy)
                            "incoming" -> {
                                ArButton("Принять", { runFriend("POST", "/friends/${user.id}/accept", "Заявка принята") }, kind = ButtonKind.Primary, icon = Lucide.Check, small = true, busy = friendBusy)
                                ArButton("Отклонить", { runFriend("DELETE", "/friends/${user.id}", "Заявка отклонена") }, kind = ButtonKind.Ghost, icon = Lucide.X, small = true)
                            }
                            "friends" -> ArButton("Из друзей", { runFriend("DELETE", "/friends/${user.id}", "Удалён из друзей") }, kind = ButtonKind.Ghost, icon = Lucide.UserMinus, small = true, busy = friendBusy)
                        }
                    }
                    if (user.is_banned) GlassPanel(Modifier.padding(top = 10.dp), padding = PaddingValues(10.dp), borderColor = Ar.danger.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.ShieldBan, null, tint = Ar.danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column { Text("Пользователь заблокирован", color = Ar.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text("Этот аккаунт заблокирован администрацией и не может публиковать комментарии, сообщения и другой контент.", color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ArTabs(listOf(
                        TabItem("info", "Информация"), TabItem("library", "Библиотека"), TabItem("favorites", "Избранное", stats.favorites),
                        TabItem("comments", "Комментарии", stats.comments), TabItem("collections", "Коллекции"), TabItem("friends", "Друзья", friendsCount),
                    ), tab, { tab = it }, variant = TabsVariant.Underline)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        when (tab) {
            "info" -> item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (user.bio.isNotBlank() || user.socials.isNotEmpty()) GlassPanel {
                        Eyebrow(if (user.bio.isNotBlank()) "О себе" else "Ссылки")
                        if (user.bio.isNotBlank()) { Spacer(Modifier.height(6.dp)); ArMarkdown(user.bio, media = "image") }
                        SocialLinks(user.socials, Modifier.padding(top = 8.dp))
                    }
                    GlassPanel(Modifier.fillMaxWidth()) {
                        StatRow(Lucide.Headphones, String.format(java.util.Locale.US, "%.2f", stats.seconds_listened / 3600.0).trimEnd('0').trimEnd('.'), "часов прослушано")
                        StatRow(Lucide.Library, Fmt.count(libraryTotal), "${Fmt.plural(libraryTotal, "книга", "книги", "книг")} в библиотеке")
                        StatRow(Lucide.MessageSquare, Fmt.count(stats.comments), Fmt.plural(stats.comments, "комментарий", "комментария", "комментариев"))
                    }
                    GlassPanel { ListeningHeatmap(userRef, profile.activity) }
                    UserStatsCharts(profile.score_stats, stats)
                }
            }
            "library" -> {
                item {
                    val opts = listOf(TabItem("all", "Все", libraryTotal)) + Labels.libraryValues.map { TabItem(it, Labels.libraryStatus[it]!!, when (it) { "planning" -> stats.planning; "in_progress" -> stats.in_progress; "completed" -> stats.completed; else -> stats.dropped }) }
                    ArTabs(opts, libStatus, { libStatus = it }, Modifier.padding(horizontal = 16.dp), variant = TabsVariant.Pill)
                    Spacer(Modifier.height(10.dp))
                }
                pagedItems(library, emptyTitle = "Здесь пусто", emptyBody = (if (isOwn) OWN_LIB_EMPTY else LIB_EMPTY)[libStatus] ?: "", icon = Lucide.Library, key = { it.title.id }) { e ->
                    LibraryRow(e, user.id, canEditLibrary, isOwn, { n -> library.patch({ it.title.id == e.title.id }, { n }) }, { library.remove { it.title.id == e.title.id } })
                }
            }
            "favorites" -> {
                val items = favorites.items
                when {
                    favorites.loading || items == null -> item { CenterSpinner() }
                    items.isEmpty() -> item { EmptyState("Избранного пока нет", "У ${user.username} нет избранных тайтлов.", Lucide.Heart) }
                    else -> item { Box(Modifier.padding(horizontal = 16.dp)) { CardGrid(items) }; InfiniteScrollTrigger(favorites) }
                }
            }
            "comments" -> pagedItems(comments, "Комментариев пока нет", "У ${user.username} пока нет ни одного комментария.", Lucide.MessageSquare, key = { it.id }) { c ->
                GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), padding = PaddingValues(12.dp), onClick = { c.target?.link?.let { nav.openLink(it + "#comment-${c.id}") } }) {
                    Row { Text("к ", color = Ar.textMuted, fontSize = 12.sp); Text(c.target?.name ?: c.target?.type ?: "", color = Ar.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text(Fmt.timeAgo(c.created_at), color = Ar.textMuted, fontSize = 11.sp) }
                    Spacer(Modifier.height(6.dp))
                    if (c.is_deleted) Text("Комментарий удалён", color = Ar.textMuted, fontSize = 13.sp)
                    if (!c.is_deleted || auth.can("comments.moderate")) ArMarkdown(c.body, compact = true)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.ArrowBigUp, null, tint = if (c.score > 0) Ar.ok else if (c.score < 0) Ar.danger else Ar.textMuted, modifier = Modifier.size(14.dp))
                        Text(" ${c.score}", color = Ar.textSecondary, fontSize = 12.sp)
                        if (c.updated_at != null) Text("  изменено", color = Ar.textMuted, fontSize = 11.sp)
                    }
                }
            }
            "collections" -> pagedItems(collections, "Коллекций нет", "У ${user.username} нет опубликованных коллекций.", Lucide.BookOpen, key = { it.id }) { c -> CollectionCardC(c, Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) }
            "friends" -> pagedItems(friends, "Друзей пока нет", if (isOwn) "У вас пока нет друзей. Найдите пользователей и отправьте заявку." else "У ${user.shownName} пока нет друзей.", Lucide.Users, key = { it.id }) { f ->
                PersonRow(f, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {}
            }
        }
    }
    ImageViewer(avatarViewer, listOfNotNull(user.avatar_url), 0, listOf(user.username)) { avatarViewer = false }
    if (editing) UserEditModal(user.id, { editing = false }) { loader.reload() }
}

@Composable
private fun StatRow(icon: ImageVector, value: String, label: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Ar.accentSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Ar.accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(value, color = Ar.white, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Ar.textMuted, fontSize = 13.sp)
    }
}

/** Adds a PagedList to a LazyColumn with loading / empty / infinite-scroll states. */
fun <T> androidx.compose.foundation.lazy.LazyListScope.pagedItems(list: PagedList<T>, emptyTitle: String, emptyBody: String, icon: ImageVector, key: (T) -> Any, content: @Composable (T) -> Unit) {
    val items = list.items
    when {
        list.loading || items == null -> item { if (list.error.isNotEmpty()) ErrorState(list.error, null) else CenterSpinner() }
        items.isEmpty() -> item { EmptyState(emptyTitle, emptyBody, icon) }
        else -> {
            items(items, key = key) { content(it) }
            item { InfiniteScrollTrigger(list) }
        }
    }
}

/** A user row with avatar, name, badges, and trailing actions (me/friends PersonRow). */
@Composable
fun PersonRow(user: UserBrief, modifier: Modifier = Modifier, whenAt: String? = null, actions: @Composable () -> Unit) {
    val nav = LocalNav.current
    GlassPanel(modifier, padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(user.username, user.avatar_url, 44.dp, presence = user.presence, thumbUrl = user.avatar_thumb_url, onClick = { nav.go(Routes.user(user.id)) })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { nav.go(Routes.user(user.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.shownName, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(4.dp)); UserBadgesRow(user.badges, user.is_banned, 14.dp)
                }
                if (user.display_name.isNotBlank() && user.display_name != user.username) Text("@${user.username}", color = Ar.textMuted, fontSize = 12.sp)
                if (whenAt != null) Text(Fmt.timeAgo(whenAt), color = Ar.textMuted, fontSize = 11.sp)
            }
            actions()
        }
    }
}

/** components/LibraryRow */
@Composable
fun LibraryRow(entry: LibraryEntry, userId: Int, canEdit: Boolean, isOwnShelf: Boolean, onChange: (LibraryEntry) -> Unit, onRemove: () -> Unit) {
    val nav = LocalNav.current
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember(entry.note) { mutableStateOf(entry.note) }
    val scope = rememberCoroutineScope()
    val t = entry.title
    val libraryPath = if (isOwnShelf) "/me/library/${t.id}" else "/mod/users/$userId/library/${t.id}"
    val ratingPath = if (isOwnShelf) "/titles/${t.id}/rating" else "/mod/users/$userId/rating/${t.id}"
    val favoritePath = if (isOwnShelf) "/me/favorites/${t.id}" else "/mod/users/$userId/favorites/${t.id}"
    fun run(block: suspend () -> Unit) { if (busy) return; busy = true; scope.launch { try { block() } catch (e: Exception) { toastError(e) } finally { busy = false } } }

    GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), padding = PaddingValues(10.dp)) {
        Row {
            ArImage(t.thumb, Modifier.size(56.dp, 80.dp).clickable { nav.go(Routes.title(t.slug)) }, shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { nav.go(Routes.title(t.slug)) })
                Text(listOfNotNull(t.year?.toString(), t.author?.name, Labels.libraryStatus[entry.status]).joinToString(" · "), color = Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.note.isNotBlank()) Text(entry.note, color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.is_favorite) { Icon(Lucide.Heart, "В избранном", tint = Ar.accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)) }
                    Icon(Lucide.Star, null, tint = if (entry.rating != null) Ar.accent else Ar.textMuted, modifier = Modifier.size(13.dp))
                    Text(" ${entry.rating ?: "—"}", color = Ar.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    if (canEdit) Text(if (open) "Закрыть" else "Изменить", color = Ar.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { open = !open })
                }
            }
        }
        if (canEdit && open) Column(Modifier.padding(top = 10.dp)) {
            SelectMenu(entry.status, Labels.libraryValues.map { SelectOption(it, Labels.libraryStatus[it]!!) }, { s -> run { val r = Api.put<LibraryEntry>(libraryPath, buildJsonObject { put("status", s) }); onChange(entry.copy(status = r.status, note = r.note)) } }, label = "Список", enabled = !busy, small = true)
            Spacer(Modifier.height(8.dp))
            Text("Оценка".uppercase(), color = Ar.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..10) Icon(Lucide.Star, "Оценить на $i", tint = if (i <= (entry.rating ?: 0)) Ar.accent else Ar.borderStrong, modifier = Modifier.size(24.dp).clickable(enabled = !busy) {
                    run { val v = if (i == entry.rating) null else i; if (v == null) Api.delete<Unit>(ratingPath) else Api.put<Unit>(ratingPath, buildJsonObject { put("value", v) }); onChange(entry.copy(rating = v)) }
                }.padding(3.dp))
                Text("  ${entry.rating ?: "—"}", color = Ar.textSecondary, fontSize = 12.sp)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArButton(if (entry.is_favorite) "В избранном" else "В избранное", { run { val next = !entry.is_favorite; if (next) Api.put<Unit>(favoritePath) else Api.delete<Unit>(favoritePath); onChange(entry.copy(is_favorite = next)) } }, kind = if (entry.is_favorite) ButtonKind.Primary else ButtonKind.Default, icon = Lucide.Heart, small = true, enabled = !busy)
                ArButton("Убрать", { run { Api.delete<Unit>(libraryPath); onRemove() } }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true, enabled = !busy)
            }
            Spacer(Modifier.height(8.dp))
            ArTextField(note, { note = it }, placeholder = if (isOwnShelf) "Заметка — видна всем в вашем профиле…" else "Заметка пользователя…", singleLine = false, minLines = 2, maxLength = Limits.libraryNote, showCounter = true)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                ArButton("Сохранить", { run { val r = Api.put<LibraryEntry>(libraryPath, buildJsonObject { put("note", note) }); onChange(entry.copy(status = r.status, note = r.note)); toast("Заметка сохранена") } }, kind = ButtonKind.Primary, small = true, enabled = !busy && note != entry.note)
            }
        }
    }
}

@Serializable
private data class BadgesRes(val items: List<Badge> = emptyList())

@Serializable
private data class SentRes(val sent: Boolean = false)

/** components/UserEditModal: the mod's user editor. Full editors get the whole settings page scoped to the user. */
@Composable
fun UserEditModal(userId: Int, onClose: () -> Unit, onSaved: (Me) -> Unit) {
    val auth = LocalAuth.current
    val isAdmin = auth.can("*")
    val canFullEdit = auth.can("users.full_edit")
    val canResetTotp = auth.can("users.totp_reset")
    if (canFullEdit) {
        ArModal(true, onClose, "Настройки пользователя") { SettingsBody(scopeUserId = userId, onSaved = onSaved) }
        return
    }
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Me?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var socials by remember { mutableStateOf<List<String>>(emptyList()) }
    var password by remember { mutableStateOf("") }
    var badges by remember { mutableStateOf<List<Badge>>(emptyList()) }
    var badgeIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(userId) {
        try {
            val u = Api.get<Me>("/mod/users/$userId"); target = u; username = u.username; displayName = u.display_name; email = u.email ?: ""; bio = u.bio; socials = u.socials; badgeIds = u.badges.map { it.id }
            if (isAdmin) badges = runCatching { Api.get<BadgesRes>("/mod/badges").items }.getOrDefault(emptyList())
        } catch (e: Exception) { error = e.message ?: "Ошибка" }
    }
    fun apply(u: Me, keepOpen: Boolean) { target = u; onSaved(u); if (!keepOpen) onClose() }
    fun run(block: suspend () -> Unit) { if (busy) return; busy = true; scope.launch { try { block() } catch (e: Exception) { toastError(e) } finally { busy = false } } }
    val avatarPick = rememberImageCropper(1, 1, 1024, 1024, circle = true) { img -> run { apply(Api.upload<Me>("/mod/users/$userId/avatar") { addPart(img.part(filename = "avatar.webp")) }, true); toast("Аватар обновлён") } }
    val coverPick = rememberImageCropper(3, 1, 2048, 2048) { img -> run { apply(Api.upload<Me>("/mod/users/$userId/cover") { addPart(img.part(filename = "cover.webp")) }, true); toast("Обложка обновлена") } }

    ArModal(true, onClose, "Редактировать пользователя") {
        val t = target
        when {
            error.isNotEmpty() -> Text(error, color = Ar.danger, fontSize = 13.sp)
            t == null -> Text("Загрузка…", color = Ar.textMuted, fontSize = 13.sp)
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ArTextField(username, { username = it }, label = "Имя пользователя", maxLength = Limits.username)
                ArTextField(displayName, { displayName = it }, label = "Отображаемое имя", maxLength = Limits.displayName)
                ArTextField(bio, { bio = it }, label = "О себе", singleLine = false, minLines = 3, maxLength = Limits.bio)
                SocialsEditor(socials, { socials = it }, label = "Ссылки")
                if (isAdmin) {
                    ArTextField(email, { email = it }, label = "Email", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                    ArTextField(password, { password = it }, label = "Новый пароль", placeholder = "Оставьте пустым, чтобы не менять")
                }
                ArButton("Отправить ссылку для сброса", { run { val r = Api.post<SentRes>("/mod/users/$userId/reset-password"); toast(if (r.sent) "Ссылка для сброса отправлена на ${t.email}" else "Почта не настроена на сервере — ссылка записана в лог") } }, kind = ButtonKind.Ghost, icon = Lucide.KeyRound, small = true, enabled = !busy)
                if (canResetTotp && t.totp_enabled) ArButton("Отключить двухфакторную аутентификацию", { run { apply(Api.delete<Me>("/mod/users/$userId/totp"), true); toast("Двухфакторная аутентификация отключена") } }, kind = ButtonKind.Ghost, icon = Lucide.ShieldOff, small = true, enabled = !busy)
                if (isAdmin) {
                    Text("Бейджи".uppercase(), color = Ar.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (b in badges) {
                            val on = b.id in badgeIds
                            Box(Modifier.clickable { badgeIds = if (on) badgeIds - b.id else badgeIds + b.id }) { Chip(b.name, null, leading = { org.foxgirls.audioranobe.ui.components.BadgeIcon(b, 16.dp) }, modifier = Modifier.then(if (on) Modifier else Modifier.background(Ar.fill04, CircleShape))) }
                        }
                    }
                    ArToggle(t.email_verified, { v -> run { apply(Api.patch<Me>("/mod/users/$userId", buildJsonObject { put("email_verified", v) }), true); toast(if (v) "Почта отмечена подтверждённой" else "Отметка подтверждения снята") } }, "Почта подтверждена", enabled = !busy)
                }
                Text("Изображения профиля".uppercase(), color = Ar.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserAvatar(t.username, t.avatar_url, 40.dp)
                    Text("Аватар", color = Ar.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    ArButton("Заменить", { avatarPick.pick() }, kind = ButtonKind.Ghost, small = true, icon = Lucide.ImagePlus)
                    IconBtn(Lucide.Trash2, "Удалить аватар", { run { apply(Api.patch<Me>("/mod/users/$userId", buildJsonObject { put("remove_avatar", true) }), true); toast("Аватар удалён") } }, enabled = t.avatar_url != null && !busy)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArImage(t.cover_url, Modifier.size(72.dp, 24.dp), shape = RoundedCornerShape(4.dp))
                    Text("Обложка", color = Ar.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    ArButton("Заменить", { coverPick.pick() }, kind = ButtonKind.Ghost, small = true, icon = Lucide.ImagePlus)
                    IconBtn(Lucide.Trash2, "Удалить обложку", { run { apply(Api.patch<Me>("/mod/users/$userId", buildJsonObject { put("remove_cover", true) }), true); toast("Обложка удалена") } }, enabled = t.cover_url != null && !busy)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ArButton("Отмена", onClose, kind = ButtonKind.Ghost); Spacer(Modifier.width(8.dp))
                    ArButton("Сохранить", { run {
                        val u = Api.patch<Me>("/mod/users/$userId", buildJsonObject {
                            if (username.isNotBlank()) put("username", username.trim()); put("display_name", displayName); put("bio", bio)
                            put("socials", kotlinx.serialization.json.JsonArray(socials.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                            if (isAdmin) { if (email.isNotBlank()) put("email", email.trim()); if (password.isNotEmpty()) put("password", password); put("badge_ids", kotlinx.serialization.json.JsonArray(badgeIds.map { kotlinx.serialization.json.JsonPrimitive(it) })) }
                        })
                        apply(u, false); toast(if (password.isNotEmpty()) "Профиль и пароль обновлены" else "Профиль обновлён")
                    } }, kind = ButtonKind.Primary, busy = busy)
                }
            }
        }
    }
}
