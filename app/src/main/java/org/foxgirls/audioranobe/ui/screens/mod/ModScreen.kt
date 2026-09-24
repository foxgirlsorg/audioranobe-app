package org.foxgirls.audioranobe.ui.screens.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.data.DashboardStats
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.CountBubble
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.components.SectionTitle
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.pagePadding
import org.foxgirls.audioranobe.ui.screens.me.RequireAuth
import org.foxgirls.audioranobe.ui.theme.Ar

private val HEADINGS = mapOf(
    "dashboard" to ("Центр" to "управления"), "queue" to ("Очередь" to "заявок"), "reports" to ("Жалобы" to "пользователей"),
    "review" to ("Проверка" to "тайтлов"), "comments" to ("Все" to "комментарии"), "words" to ("Фильтр" to "слов"),
    "usernames" to ("Зарезервированные" to "имена"), "trash" to ("Корзина" to "модерации"), "titles" to ("Тайтлы" to ""),
    "users" to ("Управление" to "пользователями"), "narrators" to ("Управление" to "чтецами"), "authors" to ("Управление" to "авторами"),
    "genres" to ("Управление" to "тегами"), "badges" to ("Управление" to "бейджами"), "dmca" to ("Заявки" to "DMCA"),
    "banners" to ("Баннеры" to ""), "donations" to ("Пожертвования" to ""), "audit" to ("Аудит" to "действий"), "tasks" to ("Задачи" to ""),
)

/** app/mod/*: one route, the page picked by [page]; the sidebar becomes a bottom sheet. */
@Composable
fun ModScreen(page: String, arg: String?) {
    if (RequireAuth()) return
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val can: (String) -> Boolean = { auth.can(it) }
    val tab = ModNav.find(page)
    var menuOpen by remember { mutableStateOf(false) }
    val counts = rememberLoader(user?.id) { runCatching { Api.get<DashboardStats>("/mod/dashboard") }.getOrNull() }
    val stats = counts.data

    if (!auth.isMod) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) { EmptyState("Нет доступа", "Панель модерации доступна только модераторам.", Lucide.Lock) }; return }
    if (tab == null) { org.foxgirls.audioranobe.ui.screens.NotFoundScreen(); return }
    if (!ModNav.visible(tab, can)) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) { EmptyState("Нет доступа", "У вас нет права на этот раздел.", Lucide.Lock) }; return }

    val (title, accent) = HEADINGS[page] ?: (tab.label to "")
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Eyebrow("Панель модерации")
                SectionTitle(title, accent.ifBlank { null }, size = 20)
            }
            Box {
                IconBtn(Lucide.Menu, "Разделы", { menuOpen = true }, tint = Ar.text)
                val total = stats?.let { it.pending_requests + it.open_reports + it.review_queue + it.comments_unchecked } ?: 0
                if (total > 0) CountBubble(total, Modifier.align(Alignment.TopEnd))
            }
        }
        // Quick strip of the group's sibling pages.
        val group = ModNav.GROUPS.firstOrNull { g -> g.tabs.any { it.page == page } }
        if (group != null && group.tabs.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            group.tabs.filter { ModNav.visible(it, can) }.forEach { t ->
                val active = t.page == page
                Row(
                    Modifier.clip(CircleShape).background(if (active) Ar.accentSoft else Ar.fill04).clickable { if (!active) nav.replace(Routes.mod(t.page)) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(t.icon, null, tint = if (active) Ar.accentHover else Ar.textMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t.label, color = if (active) Ar.accentHover else Ar.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    val c = t.countKey?.let { key -> stats?.count(key) } ?: 0
                    if (c > 0) { Spacer(Modifier.width(6.dp)); Text(c.toString(), color = Ar.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding())) {
            when (page) {
                "dashboard" -> Dashboard()
                "queue" -> ModQueuePage(arg)
                "reports" -> ModReportsPage()
                "review" -> ModReviewPage()
                "comments" -> ModCommentsPage()
                "words" -> ModWordsPage()
                "usernames" -> ModUsernamesPage()
                "trash" -> ModTrashPage()
                "titles" -> ModTitlesPage()
                "users" -> ModUsersPage()
                "narrators" -> ModNarratorsPage()
                "authors" -> ModAuthorsPage()
                "genres" -> ModGenresPage()
                "badges" -> ModBadgesPage()
                "dmca" -> ModDmcaPage()
                "banners" -> ModBannersPage()
                "donations" -> ModDonationsPage()
                "audit" -> ModAuditPage()
                "tasks" -> ModTasksPage()
            }
        }
    }

    ArSheet(menuOpen, { menuOpen = false }, "Панель модерации") {
        ModNav.GROUPS.forEach { g ->
            val tabs = g.tabs.filter { ModNav.visible(it, can) }
            if (tabs.isEmpty()) return@forEach
            Eyebrow(g.label, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp))
            tabs.forEach { t ->
                MenuRow(t.icon, t.label, { menuOpen = false; if (t.page != page) nav.replace(Routes.mod(t.page)) }, count = t.countKey?.let { stats?.count(it) } ?: 0, tint = if (t.page == page) Ar.accent else Ar.textSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun DashboardStats.count(key: String): Int = when (key) {
    "pending_requests" -> pending_requests; "open_reports" -> open_reports; "review_queue" -> review_queue
    "comments_unchecked" -> comments_unchecked; "jobs_error" -> jobs_error; else -> 0
}

private data class Tile(val key: String, val label: String, val page: String? = null, val route: String? = null, val accent: Boolean = false)

private val TILES = listOf(
    Tile("pending_requests", "Заявки на модерации", page = "queue", accent = true),
    Tile("open_reports", "Открытые жалобы", page = "reports", accent = true),
    Tile("review_queue", "Тайтлы на проверку", page = "review", accent = true),
    Tile("comments_unchecked", "Новые комментарии", page = "comments", accent = true),
    Tile("jobs_error", "Задачи с ошибкой", page = "tasks", accent = true),
    Tile("mods_online", "Модераторов онлайн"),
    Tile("users", "Пользователи", page = "users"),
    Tile("new_users_7d", "Новые за 7 дней", page = "users"),
    Tile("push_subscribers", "Подписчиков на push"),
    Tile("titles_total", "Тайтлы", route = Routes.catalog()),
    Tile("chapters_total", "Главы"),
    Tile("narrators_total", "Чтецы"),
    Tile("comments_total", "Комментарии"),
    Tile("collections_total", "Коллекции", route = Routes.COLLECTIONS),
    Tile("listens_total", "Прослушивания"),
    Tile("jobs_queued", "Задачи в очереди"),
    Tile("jobs_processing", "Задачи в обработке"),
)

private fun DashboardStats.value(key: String): Long = when (key) {
    "users" -> users.toLong(); "new_users_7d" -> new_users_7d.toLong(); "titles_total" -> titles_total.toLong(); "chapters_total" -> chapters_total.toLong()
    "narrators_total" -> narrators_total.toLong(); "comments_total" -> comments_total.toLong(); "collections_total" -> collections_total.toLong()
    "listens_total" -> listens_total; "pending_requests" -> pending_requests.toLong(); "open_reports" -> open_reports.toLong(); "mods_online" -> mods_online.toLong()
    "jobs_queued" -> jobs_queued.toLong(); "jobs_processing" -> jobs_processing.toLong(); "jobs_error" -> jobs_error.toLong(); "review_queue" -> review_queue.toLong()
    "comments_unchecked" -> comments_unchecked.toLong(); "push_subscribers" -> push_subscribers.toLong(); else -> 0
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard() {
    val nav = LocalNav.current
    val loader = rememberLoader(Unit) { Api.get<DashboardStats>("/mod/dashboard") }
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner()
        is Load.Err -> ErrorState(s.message, { loader.reload() })
        is Load.Ok -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
            TILES.forEach { t ->
                val target = t.page?.let { Routes.mod(it) } ?: t.route
                GlassPanel(Modifier.weight(1f), borderColor = if (t.accent) Ar.accent.copy(alpha = 0.35f) else Ar.border, onClick = target?.let { r -> { nav.go(r) } }) {
                    Text(t.label, color = Ar.textMuted, fontSize = 12.sp)
                    Text(String.format(java.util.Locale.US, "%,d", s.data.value(t.key)), color = if (t.accent) Ar.accent else Ar.white, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Shared small helpers for the mod pages. */
@Composable
fun ModHint(text: String) = Text(text, color = Ar.textMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 12.dp))

@Composable
fun ModSearch(value: String, onChange: (String) -> Unit, placeholder: String) =
    org.foxgirls.audioranobe.ui.components.ArTextField(value, onChange, Modifier.fillMaxWidth().padding(bottom = 12.dp), placeholder = placeholder, leading = Lucide.Search)

/** Rounded small tag used in the mod tables. */
@Composable
fun Tag(text: String, color: androidx.compose.ui.graphics.Color = Ar.textMuted) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
