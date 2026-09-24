package org.foxgirls.audioranobe.ui.nav

import android.net.Uri

/** Route strings mirror the site's URL structure so deep links and API `link` fields map 1:1. */
object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog?q={q}&tab={tab}&genre={genre}&sort={sort}&order={order}&finished={finished}&author={author}"
    const val COLLECTIONS = "collections"
    const val NEWS = "news"
    const val DONATE = "donate"
    const val DMCA = "dmca"
    const val NOT_FOUND = "notfound"
    const val OFFLINE = "offline"
    const val OTHER = "other"

    const val TITLE = "title/{slug}?tab={tab}"
    const val TITLE_EDIT = "title/{slug}/edit?tab={tab}"
    const val CHAPTER = "chapter/{id}?t={t}"
    const val NARRATOR = "narrator/{slug}?tab={tab}"
    const val NARRATOR_EDIT = "narrator/{slug}/edit"
    const val AUTHOR = "author/{id}"
    const val AUTHOR_EDIT = "author/{id}/edit"
    const val USER = "user/{id}?tab={tab}"
    const val COLLECTION = "collections/{id}"
    const val NEWS_ITEM = "news/{slug}"
    const val POST = "post/{id}"

    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val FORGOT = "auth/forgot"
    const val RESET = "auth/reset?token={token}"
    const val VERIFY = "auth/verify?token={token}"
    const val SETUP = "auth/setup"
    const val OAUTH = "auth/oauth/{provider}?mode={mode}"

    const val ME_CHAT = "me/chat?user={user}"
    const val ME_FRIENDS = "me/friends"
    const val ME_HISTORY = "me/history"
    const val ME_NOTIFICATIONS = "me/notifications"
    const val ME_REQUESTS = "me/requests"
    const val ME_SETTINGS = "me/settings?tab={tab}"
    const val ME_RECAP = "me/recap"
    const val ME_RECAP_YEAR = "me/recap/{year}"

    const val LEGAL = "legal/{doc}"
    const val MOD = "mod/{page}?arg={arg}"

    fun title(slug: String, tab: String? = null) = "title/${enc(slug)}" + (tab?.let { "?tab=$it" } ?: "")
    fun titleEdit(slug: String, tab: String? = null) = "title/${enc(slug)}/edit" + (tab?.let { "?tab=$it" } ?: "")
    fun chapter(id: Int, t: Int? = null) = "chapter/$id" + (t?.let { "?t=$it" } ?: "")
    fun narrator(slug: String, tab: String? = null) = "narrator/${enc(slug)}" + (tab?.let { "?tab=$it" } ?: "")
    fun narratorEdit(slug: String) = "narrator/${enc(slug)}/edit"
    fun author(id: Any) = "author/$id"
    fun authorEdit(id: Int) = "author/$id/edit"
    fun user(ref: Any, tab: String? = null) = "user/${enc(ref.toString())}" + (tab?.let { "?tab=$it" } ?: "")
    fun collection(id: Int) = "collections/$id"
    fun newsItem(slug: String) = "news/${enc(slug)}"
    fun post(id: Int) = "post/$id"
    fun chat(userId: Int? = null) = "me/chat" + (userId?.let { "?user=$it" } ?: "")
    fun settings(tab: String? = null) = "me/settings" + (tab?.let { "?tab=$it" } ?: "")
    fun recapYear(year: Int) = "me/recap/$year"
    fun legal(doc: String) = "legal/$doc"
    fun mod(page: String = "dashboard", arg: String? = null) = "mod/$page" + (arg?.let { "?arg=${enc(it)}" } ?: "")
    fun oauth(provider: String, mode: String) = "auth/oauth/${enc(provider)}?mode=$mode"
    fun reset(token: String) = "auth/reset?token=${enc(token)}"
    fun verify(token: String) = "auth/verify?token=${enc(token)}"
    fun catalog(q: String? = null, tab: String? = null, genre: String? = null, sort: String? = null, order: String? = null, finished: String? = null, author: String? = null): String {
        val qs = listOfNotNull(
            q?.let { "q=${enc(it)}" }, tab?.let { "tab=$it" }, genre?.let { "genre=${enc(it)}" }, sort?.let { "sort=$it" },
            order?.let { "order=$it" }, finished?.let { "finished=$it" }, author?.let { "author=${enc(it)}" },
        )
        return "catalog" + (if (qs.isEmpty()) "" else "?" + qs.joinToString("&"))
    }

    fun enc(s: String): String = Uri.encode(s)

    /** Maps a site path (from API `link` fields or deep links) to an app route. Null when unknown. */
    fun fromPath(pathWithQuery: String): String? {
        val raw = pathWithQuery.trim()
        val frag = raw.substringAfter('#', "")
        val path = raw.substringBefore('#').substringBefore('?').trimEnd('/')
        val query = raw.substringBefore('#').substringAfter('?', "")
        val qp = query.split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to Uri.decode(it.substringAfter('=')) }
        val seg = path.trimStart('/').split('/').filter { it.isNotEmpty() }
        return when {
            seg.isEmpty() -> HOME
            seg[0] == "catalog" -> catalog(qp["q"], qp["tab"], qp["genre"], qp["sort"], qp["order"], qp["finished"], qp["author"])
            seg[0] == "title" && seg.size == 2 -> title(Uri.decode(seg[1]), qp["tab"] ?: frag.takeIf { it.startsWith("comment") }?.let { "comments" })
            seg[0] == "title" && seg.size == 3 && seg[2] == "edit" -> titleEdit(Uri.decode(seg[1]), qp["tab"])
            seg[0] == "chapter" && seg.size == 2 -> seg[1].toIntOrNull()?.let { chapter(it, qp["t"]?.toIntOrNull()) }
            seg[0] == "narrator" && seg.size == 2 -> narrator(Uri.decode(seg[1]), qp["tab"])
            seg[0] == "narrator" && seg.size == 3 && seg[2] == "edit" -> narratorEdit(Uri.decode(seg[1]))
            seg[0] == "author" && seg.size == 2 -> author(Uri.decode(seg[1]))
            seg[0] == "author" && seg.size == 3 -> seg[1].toIntOrNull()?.let { authorEdit(it) }
            seg[0] == "user" && seg.size == 2 -> user(Uri.decode(seg[1]), qp["tab"])
            seg[0] == "collections" && seg.size == 1 -> COLLECTIONS
            seg[0] == "collections" && seg.size == 2 -> seg[1].toIntOrNull()?.let { collection(it) }
            seg[0] == "news" && seg.size == 1 -> NEWS
            seg[0] == "news" && seg.size == 2 -> newsItem(Uri.decode(seg[1]))
            seg[0] == "post" && seg.size == 2 -> seg[1].toIntOrNull()?.let { post(it) }
            seg[0] == "donate" -> DONATE
            seg[0] == "dmca" -> DMCA
            seg[0] == "legal" && seg.size == 2 -> legal(seg[1])
            seg[0] == "auth" && seg.size >= 2 -> when (seg[1]) {
                "login" -> LOGIN; "register" -> REGISTER; "forgot" -> FORGOT; "setup" -> SETUP
                "reset" -> qp["token"]?.let { reset(it) } ?: FORGOT
                "verify" -> qp["token"]?.let { verify(it) } ?: settings()
                "callback" -> null
                else -> null
            }
            seg[0] == "me" && seg.size >= 2 -> when (seg[1]) {
                "chat" -> chat(qp["user"]?.toIntOrNull())
                "friends" -> ME_FRIENDS; "history" -> ME_HISTORY; "notifications" -> ME_NOTIFICATIONS; "requests" -> ME_REQUESTS
                "settings" -> settings(qp["tab"])
                "recap" -> if (seg.size == 3) seg[2].toIntOrNull()?.let { recapYear(it) } else ME_RECAP
                else -> null
            }
            seg[0] == "mod" -> mod(seg.getOrNull(1) ?: "dashboard", seg.getOrNull(2) ?: qp["id"] ?: qp["q"])
            else -> null
        }
    }
}
