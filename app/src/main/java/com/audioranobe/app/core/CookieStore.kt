package com.audioranobe.app.core

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Persistent cookie jar. The backend keeps the whole session in an HttpOnly cookie
 * and slides it forward by re-setting it, so cookies must survive process death.
 */
class CookieStore(context: Context) : CookieJar {
    private val prefs: SharedPreferences = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val store = HashMap<String, Cookie>()

    init {
        prefs.all.forEach { (k, v) ->
            (v as? String)?.let { raw ->
                val host = k.substringBefore('|')
                val url = HttpUrl.Builder().scheme("https").host(host).build()
                Cookie.parse(url, raw)?.let { c -> if (c.expiresAt > System.currentTimeMillis()) store[k] = c }
            }
        }
    }

    private fun key(c: Cookie) = "${c.domain}|${c.path}|${c.name}"

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val e = prefs.edit()
        for (c in cookies) {
            val k = key(c)
            if (c.expiresAt <= System.currentTimeMillis()) {
                store.remove(k); e.remove(k)
            } else {
                store[k] = c
                if (c.persistent) e.putString(k, c.toString()) else e.remove(k)
            }
        }
        e.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val it = store.entries.iterator()
        val out = ArrayList<Cookie>()
        while (it.hasNext()) {
            val (k, c) = it.next()
            if (c.expiresAt <= now) {
                it.remove(); prefs.edit().remove(k).apply()
            } else if (c.matches(url)) out.add(c)
        }
        return out
    }

    @Synchronized
    fun clear() {
        store.clear()
        prefs.edit().clear().apply()
    }
}
