package com.audioranobe.app.ui.screens.mod

import androidx.compose.ui.graphics.vector.ImageVector
import com.audioranobe.app.ui.icons.Lucide

/** app/mod/navGroups.tsx */
object ModNav {
    val TRASH_VIEW_PERMS = listOf("trash.view.title", "trash.view.chapter", "trash.view.narrator", "trash.view.author", "trash.view.comment")
    val NOTIFY_PERMS = listOf("notify.user", "notify.narrator_subs", "notify.title_followers", "notify.all")

    data class Tab(val page: String, val label: String, val icon: ImageVector, val perm: String? = null, val anyPerm: List<String>? = null, val countKey: String? = null)
    data class Group(val label: String, val tabs: List<Tab>)

    val GROUPS = listOf(
        Group("Главное", listOf(Tab("dashboard", "Обзор", Lucide.LayoutDashboard))),
        Group("Модерация", listOf(
            Tab("queue", "Очередь", Lucide.Inbox, "moderation.queue", countKey = "pending_requests"),
            Tab("reports", "Жалобы", Lucide.Flag, "reports.handle", countKey = "open_reports"),
            Tab("review", "Проверка тайтлов", Lucide.ShieldCheck, "content.review", countKey = "review_queue"),
            Tab("comments", "Комментарии", Lucide.MessageSquare, "comments.moderate", countKey = "comments_unchecked"),
            Tab("words", "Фильтр слов", Lucide.Filter, "words.manage"),
            Tab("usernames", "Имена", Lucide.Lock, "usernames.manage"),
            Tab("trash", "Корзина", Lucide.Trash2, anyPerm = TRASH_VIEW_PERMS),
        )),
        Group("Люди и контент", listOf(
            Tab("titles", "Тайтлы", Lucide.Library, "titles.import"),
            Tab("users", "Пользователи", Lucide.Users, "users.edit"),
            Tab("narrators", "Чтецы", Lucide.Mic, "narrators.edit"),
            Tab("authors", "Авторы", Lucide.Feather, "authors.edit"),
            Tab("genres", "Теги", Lucide.BookMarked, "tags.edit"),
            Tab("badges", "Бейджи", Lucide.Award, "badges.manage"),
            Tab("dmca", "DMCA", Lucide.Shield, "dmca.manage"),
        )),
        Group("Система", listOf(
            Tab("banners", "Баннеры", Lucide.GalleryHorizontal, "banners.manage"),
            Tab("donations", "Пожертвования", Lucide.Heart, "donations.manage"),
            Tab("audit", "Аудит", Lucide.ScrollText, "audit.view"),
            Tab("tasks", "Задачи", Lucide.ListChecks, "narration.jobs", countKey = "jobs_error"),
        )),
    )

    fun countable() = GROUPS.flatMap { it.tabs }.filter { it.countKey != null }
    fun find(page: String) = GROUPS.flatMap { it.tabs }.firstOrNull { it.page == page }
    fun visible(tab: Tab, can: (String) -> Boolean): Boolean = when {
        tab.perm != null -> can(tab.perm)
        tab.anyPerm != null -> tab.anyPerm.any { can(it) }
        else -> true
    }
}
