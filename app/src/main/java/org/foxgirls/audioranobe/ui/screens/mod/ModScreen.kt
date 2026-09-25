package org.foxgirls.audioranobe.ui.screens.mod

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.screens.me.RequireAuth
import org.foxgirls.audioranobe.ui.theme.Ar

/** The site's /mod panel in a WebView; the page drops its own navbar when it sees the app's UA marker. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ModScreen(path: String) {
    if (RequireAuth()) return
    val nav = LocalNav.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val siteHost = remember { Uri.parse(Api.siteUrl).host }

    fun isPanel(url: String): Boolean {
        val u = Uri.parse(url)
        return u.host == siteHost && (u.path ?: "").let { it == "/mod" || it.startsWith("/mod/") }
    }

    BackHandler(enabled = canGoBack) { web?.goBack() }

    AndroidView(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        factory = { ctx ->
            val apiUrl = Api.baseUrl.toHttpUrl()
            val apiOrigin = "${apiUrl.scheme}://${apiUrl.host}:${apiUrl.port}"
            CookieManager.getInstance().run {
                setAcceptCookie(true)
                Api.cookies.loadForRequest(apiUrl).forEach { setCookie(apiOrigin, it.toString()) }
                flush()
            }
            WebView(ctx).apply {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "${settings.userAgentString} AudioRanobeApp"
                setBackgroundColor(Ar.bg.toArgb())
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (isPanel(url)) return false
                        Links.open(nav, url)
                        return true
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        // Client-side (pushState) navigation never reaches shouldOverrideUrlLoading.
                        if (!isPanel(url)) {
                            Links.open(nav, url)
                            if (view.canGoBack()) view.goBack() else view.loadUrl(Api.siteUrl + "/mod")
                        }
                        canGoBack = view.canGoBack()
                    }
                }
                loadUrl(Api.siteUrl + path)
                web = this
            }
        },
    )
}
