package org.foxgirls.audioranobe.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError

/**
 * A site page in a WebView with the app's session; the page drops its own chrome when it sees the
 * app's UA marker. Navigation to paths outside [inside] opens natively instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "ClickableViewAccessibility")
@Composable
fun SiteWebView(path: String, inside: (String) -> Boolean, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val siteHost = remember { Uri.parse(Api.siteUrl).host }

    fun isInside(url: String): Boolean {
        val u = Uri.parse(url)
        return u.host == siteHost && inside(u.path ?: "")
    }

    val fileCallback = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        val uris = if (res.resultCode != Activity.RESULT_OK || data == null) null
            else data.clipData?.let { c -> Array(c.itemCount) { c.getItemAt(it).uri } } ?: data.data?.let { arrayOf(it) }
        fileCallback[0]?.onReceiveValue(uris)
        fileCallback[0] = null
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingSave = remember { arrayOfNulls<Pair<ByteArray, String>>(1) }
    fun save(bytes: ByteArray, name: String) = CoroutineScope(Dispatchers.Main).launch {
        try {
            saveJpegToDownloads(context, name) { it.write(bytes) }
            toast("Сохранено в «Загрузки»")
        } catch (e: Exception) { toastError(e) }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pendingSave[0]?.let { (bytes, name) -> if (ok) save(bytes, name) else toastError("Нет доступа к памяти устройства") }
        pendingSave[0] = null
    }

    BackHandler(enabled = canGoBack) { web?.goBack() }

    // Pull-to-refresh: the WebView doesn't take part in Compose nested scrolling,
    // so a downward drag that starts at the top of the page is tracked by hand.
    val pull = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
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
                val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
                // Same distance as the state's default trigger; snapTo takes pixels, so the drag is passed as-is.
                val threshold = 80 * ctx.resources.displayMetrics.density
                var startX = 0f
                var startY = 0f
                var atTop = false
                var pulling = false
                var pulledPx = 0f
                setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { startX = e.x; startY = e.y; atTop = v.scrollY == 0; pulling = false; pulledPx = 0f }
                        MotionEvent.ACTION_MOVE -> {
                            val dy = e.y - startY
                            if (!pulling && atTop && !refreshing && dy > slop && dy > abs(e.x - startX)) pulling = true
                            if (pulling) {
                                pulledPx = (dy - slop).coerceAtLeast(0f)
                                scope.launch { pull.snapTo(pulledPx) }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pulling) {
                            pulling = false
                            if (e.actionMasked == MotionEvent.ACTION_UP && pulledPx >= threshold) {
                                refreshing = true
                                scope.launch { pull.animateToThreshold() }
                                (v as WebView).reload()
                            } else scope.launch { pull.animateToHidden() }
                        }
                    }
                    false
                }
                // lib/exportImage.ts hands rendered JPEGs here instead of an <a download> the WebView can't save.
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun saveImage(dataUrl: String, filename: String) {
                        val bytes = android.util.Base64.decode(dataUrl.substringAfter(','), android.util.Base64.DEFAULT)
                        val name = filename.removeSuffix(".jpg")
                        val needsPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
                            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!needsPermission) { save(bytes, name); return }
                        CoroutineScope(Dispatchers.Main).launch {
                            pendingSave[0] = bytes to name
                            storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                }, "AudioRanobeApp")
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
                    override fun onPageFinished(view: WebView, url: String) {
                        if (!refreshing) return
                        refreshing = false
                        scope.launch { pull.animateToHidden() }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (isInside(url)) return false
                        Links.open(nav, url)
                        return true
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        // Client-side (pushState) navigation never reaches shouldOverrideUrlLoading.
                        if (!isInside(url)) {
                            Links.open(nav, url)
                            if (view.canGoBack()) view.goBack() else view.loadUrl(Api.siteUrl + path)
                        }
                        canGoBack = view.canGoBack()
                    }
                }
                loadUrl(Api.siteUrl + path)
                web = this
            }
        },
    )
    PullToRefreshDefaults.Indicator(
        state = pull, isRefreshing = refreshing,
        modifier = Modifier.align(Alignment.TopCenter),
        containerColor = Ar.surfaceRaised, color = Ar.accent,
    )
    }
}
