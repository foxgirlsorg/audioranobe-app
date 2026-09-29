package org.foxgirls.audioranobe.ui.screens.mod

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    val fileCallback = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        val uris = if (res.resultCode != Activity.RESULT_OK || data == null) null
            else data.clipData?.let { c -> Array(c.itemCount) { c.getItemAt(it).uri } } ?: data.data?.let { arrayOf(it) }
        fileCallback[0]?.onReceiveValue(uris)
        fileCallback[0] = null
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
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                        // The page must always get an answer, or its <input type=file> never opens again.
                        fileCallback[0]?.onReceiveValue(null)
                        fileCallback[0] = callback
                        val accepts = params.acceptTypes.flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
                        // Extension accepts (".gz") have no reliable MIME match across providers, so they fall back to any file.
                        val exact = accepts.isNotEmpty() && accepts.all { '/' in it }
                        val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).apply {
                            type = if (exact && accepts.size == 1) accepts[0] else "*/*"
                            if (exact && accepts.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, accepts.toTypedArray())
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                        }
                        try { fileLauncher.launch(intent) } catch (_: ActivityNotFoundException) {
                            fileCallback[0] = null
                            callback.onReceiveValue(null)
                        }
                        return true
                    }
                }
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
