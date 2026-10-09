package org.foxgirls.audioranobe.ui.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toastError

/** Thin wrapper around NavController with the few calls screens need. */
class AppNav(val controller: NavHostController, val context: Context, val home: String = Routes.HOME) {
    /** Routes that are not in this graph (e.g. the signed-out shell has no catalog) are ignored: the auth gate swaps the shell instead. */
    private inline fun safely(block: () -> Unit) { try { block() } catch (_: IllegalArgumentException) {} }

    fun go(route: String) = safely {
        controller.navigate(route) { launchSingleTop = true }
    }

    /** Like [go] but always adds a back-stack entry, even when the target shares the current destination (list -> thread of the same route). */
    fun push(route: String) = safely { controller.navigate(route) }

    fun replace(route: String) = safely {
        controller.navigate(route) {
            launchSingleTop = true
            controller.currentBackStackEntry?.destination?.route?.let { popUpTo(it) { inclusive = true } }
        }
    }

    fun back() {
        if (!controller.popBackStack()) go(home)
    }

    /** Switches to a dock tab: always lands on the tab's root, whatever screen is open. */
    fun tab(route: String) = safely {
        controller.navigate(route) {
            popUpTo(home) { inclusive = false }
            launchSingleTop = true
        }
    }

    /** Opens a site path or absolute URL: in-app when known, otherwise in a Custom Tab. */
    fun openLink(url: String) = Links.open(this, url)
}

val LocalNav = staticCompositionLocalOf<AppNav> { error("no nav") }

object Links {
    val site: String get() = Api.siteUrl

    fun isSiteUrl(url: String): Boolean {
        val u = url.trim()
        if (u.startsWith("/")) return true
        return try {
            val h = Uri.parse(u).host?.lowercase() ?: return false
            val sh = Uri.parse(site).host?.lowercase() ?: return false
            h == sh || h == "www.$sh" || h == "audioranobe.com" || h == "www.audioranobe.com"
        } catch (_: Exception) { false }
    }

    fun open(nav: AppNav, url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        if (isSiteUrl(u)) {
            val path = if (u.startsWith("/")) u else {
                val p = Uri.parse(u)
                (p.path ?: "/") + (p.query?.let { "?$it" } ?: "") + (p.fragment?.let { "#$it" } ?: "")
            }
            val route = Routes.fromPath(path)
            if (route != null) { nav.go(route); return }
        }
        external(nav.context, u)
    }

    fun external(context: Context, url: String) {
        try {
            val uri = Uri.parse(if (url.startsWith("http")) url else "https://$url")
            if (uri.scheme == "mailto" || uri.scheme == "tel") {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            val tab = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(Ar.bg.hashCode()).build())
                .setShowTitle(true)
                .build()
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(context, uri)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                toastError("Не удалось открыть ссылку")
            }
        }
    }

    fun share(context: Context, text: String, subject: String? = null) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
