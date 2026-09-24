package com.audioranobe.app.core

import android.content.Context
import android.content.SharedPreferences

/** Small key/value settings that mirror the web's localStorage / cookie flags. */
class Prefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var rate: Float
        get() = p.getFloat("rate", 1f)
        set(v) = p.edit().putFloat("rate", v).apply()

    var volume: Float
        get() = p.getFloat("volume", 1f)
        set(v) = p.edit().putFloat("volume", v).apply()

    /** "chapterId:positionSeconds" of the last open chapter, like the audioranobe_player cookie. */
    var openChapter: String?
        get() = p.getString("open_chapter", null)
        set(v) = p.edit().putString("open_chapter", v).apply()

    var seenAnnouncements: Set<Int>
        get() = p.getStringSet("seen_ann", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = p.edit().putStringSet("seen_ann", v.takeLast(200).map { it.toString() }.toSet()).apply()

    var cookiesAccepted: Boolean
        get() = p.getBoolean("cookies_ok", false)
        set(v) = p.edit().putBoolean("cookies_ok", v).apply()

    var modAlertHiddenAt: Long
        get() = p.getLong("mod_alert_hidden", 0L)
        set(v) = p.edit().putLong("mod_alert_hidden", v).apply()

    var modSidebarCollapsed: Boolean
        get() = p.getBoolean("mod_sidebar_collapsed", false)
        set(v) = p.edit().putBoolean("mod_sidebar_collapsed", v).apply()

    var chatDraft: String?
        get() = p.getString("chat_draft", null)
        set(v) = p.edit().putString("chat_draft", v).apply()

    fun getString(key: String): String? = p.getString(key, null)
    fun putString(key: String, v: String?) = p.edit().putString(key, v).apply()
}

private fun <T> Set<T>.takeLast(n: Int): List<T> = toList().takeLast(n)
