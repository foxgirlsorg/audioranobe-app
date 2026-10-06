package org.foxgirls.audioranobe.core

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Xml
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.foxgirls.audioranobe.BuildConfig
import org.foxgirls.audioranobe.data.Stores
import java.io.File
import java.io.IOException

/** A GitHub release that carries an APK asset. */
data class AppRelease(
    val tag: String,
    val version: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkBytes: Long,
    val publishedAt: String,
)

sealed class UpdateDownload {
    data object Idle : UpdateDownload()
    data class Running(val fraction: Float) : UpdateDownload()
    data class Ready(val file: File) : UpdateDownload()
    data class Failed(val message: String) : UpdateDownload()
}

/**
 * Simple OTA: polls the GitHub Releases of [BuildConfig.UPDATE_REPO] (set at build time, never
 * hard-coded here), compares the newest tag with the running version and, on request, downloads the
 * APK asset and hands it to the system package installer.
 *
 * Reads the public releases feed (github.com/<repo>/releases.atom), not the REST API: the API's
 * anonymous limit is 60 requests/hour per IP, which a carrier's shared IP exhausts and answers 403.
 * The APK name is fixed by .github/workflows/release.yml (audioranobe-<tag>.apk); a HEAD request
 * on it gives the size and confirms CI has finished uploading it.
 */
object Updater {
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val KEY_LAST_CHECK = "update_last_check"

    val repo: String = BuildConfig.UPDATE_REPO.trim().trim('/')
    val enabled: Boolean get() = repo.contains('/')

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context
    private var downloadJob: Job? = null

    private val _available = MutableStateFlow<AppRelease?>(null)
    /** The newest release that is newer than the running build, or null. */
    val available: StateFlow<AppRelease?> = _available.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val download: StateFlow<UpdateDownload> = _download.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Set when the user dismissed the popup for the current release; cleared by a manual check. */
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        // Stale APKs from an earlier update are useless once installed (or abandoned).
        scope.launch { runCatching { cacheDir().listFiles()?.forEach { it.delete() } } }
    }

    private fun cacheDir() = File(appContext.cacheDir, "updates").apply { mkdirs() }

    /** Called on every app foreground; hits the network at most once per [CHECK_INTERVAL_MS]. */
    fun checkIfDue() {
        if (!enabled) return
        val last = Stores.prefs.getString(KEY_LAST_CHECK)?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(manual = false)
    }

    /**
     * Fetches the latest release. [manual] checks ignore the throttle
     * and always surface the popup (or the "up to date" state) so the user gets feedback.
     */
    fun check(manual: Boolean = true, onResult: ((AppRelease?, String?) -> Unit)? = null) {
        if (!enabled || _checking.value) return
        _checking.value = true
        if (manual) _dismissed.value = false
        scope.launch {
            val result = runCatching { fetchLatest() }
            _checking.value = false
            Stores.prefs.putString(KEY_LAST_CHECK, System.currentTimeMillis().toString())
            val release = result.getOrNull()
            val error = result.exceptionOrNull()?.let { it.message ?: "Не удалось проверить обновления" }
            if (release != null && isNewer(release.version, AppVersion.VERSION)) {
                _available.value = release
            } else if (manual) {
                _available.value = null
            }
            withContext(Dispatchers.Main) { onResult?.invoke(release, error) }
        }
    }

    private class FeedEntry(val tag: String, val pageUrl: String, val notesHtml: String, val updated: String)

    private suspend fun fetchLatest(): AppRelease? {
        val res = Api.client.newCall(Request.Builder().url("https://github.com/$repo/releases.atom").build()).await()
        val text = res.body?.string()
        if (!res.isSuccessful || text == null) throw IOException("GitHub ответил ${res.code}")
        // Newest first; a release whose APK isn't uploaded yet (CI still building) is skipped.
        for (e in parseFeed(text).take(3)) {
            val apkUrl = "https://github.com/$repo/releases/download/${e.tag}/audioranobe-${e.tag}.apk"
            val size = apkSize(apkUrl) ?: continue
            return AppRelease(
                tag = e.tag,
                version = e.tag.removePrefix("v"),
                name = e.tag,
                notes = htmlToMarkdown(e.notesHtml),
                pageUrl = e.pageUrl,
                apkUrl = apkUrl,
                apkBytes = size,
                publishedAt = e.updated,
            )
        }
        return null
    }

    private suspend fun apkSize(url: String): Long? {
        val res = Api.client.newCall(Request.Builder().url(url).head().build()).await()
        res.close()
        return if (res.isSuccessful) res.header("Content-Length")?.toLongOrNull() ?: 0L else null
    }

    private fun parseFeed(xml: String): List<FeedEntry> {
        val p = Xml.newPullParser()
        p.setInput(xml.reader())
        val out = mutableListOf<FeedEntry>()
        var page = ""; var notes = ""; var updated = ""; var inEntry = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "entry" -> { inEntry = true; page = ""; notes = ""; updated = "" }
                "link" -> if (inEntry) page = p.getAttributeValue(null, "href").orEmpty()
                "content" -> if (inEntry) notes = p.nextText()
                "updated" -> if (inEntry) updated = p.nextText()
            } else if (p.eventType == XmlPullParser.END_TAG && p.name == "entry") {
                inEntry = false
                val tag = page.substringAfter("/releases/tag/", "")
                if (tag.isNotBlank()) out += FeedEntry(tag, page, notes, updated)
            }
        }
        return out
    }

    /** The feed carries GitHub's rendered HTML; the dialog renders Markdown — map the few tags release notes use. */
    private fun htmlToMarkdown(html: String): String = html
        .replace(Regex("""<a [^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)) { "[${it.groupValues[2]}](${it.groupValues[1]})" }
        .replace(Regex("</?(strong|b)>"), "**")
        .replace(Regex("<h[1-6][^>]*>"), "\n## ")
        .replace(Regex("<li[^>]*>"), "\n* ")
        .replace(Regex("""<br\s*/?>|</p>|</h[1-6]>|</ul>|</ol>"""), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Semantic-ish compare: numeric dot parts first, a pre-release suffix ranks below the plain version. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun split(v: String): Pair<List<Int>, String> {
            val core = v.substringBefore('-').substringBefore('+')
            val nums = core.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            return nums to v.substringAfter('-', "")
        }
        val (a, aPre) = split(candidate.removePrefix("v"))
        val (b, bPre) = split(current.removePrefix("v"))
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        if (aPre.isEmpty() && bPre.isNotEmpty()) return true
        if (aPre.isNotEmpty() && bPre.isEmpty()) return false
        return aPre > bPre
    }

    /** Hide the popup for now; it comes back on the next check that finds the same release. */
    fun dismiss() { _dismissed.value = true }

    /** Debug builds only: a fake release whose "APK" is the installed one, to walk through the popup, download and installer. */
    fun simulate() {
        if (!BuildConfig.DEBUG) return
        val apk = File(appContext.applicationInfo.sourceDir)
        _download.value = UpdateDownload.Idle
        _dismissed.value = false
        _available.value = AppRelease(
            tag = "v99.0.0", version = "99.0.0", name = "Тестовое обновление",
            notes = "## Что нового\n\n- **Жирный** и *курсивный* текст\n- Список изменений\n- [Ссылка](https://foxgirls.org)",
            pageUrl = "", apkUrl = "file://${apk.absolutePath}", apkBytes = apk.length(), publishedAt = "",
        )
    }

    fun startDownload(release: AppRelease) {
        if (_download.value is UpdateDownload.Running) return
        _download.value = UpdateDownload.Running(0f)
        downloadJob = scope.launch {
            val target = File(cacheDir(), "audioranobe-${release.version}.apk")
            try {
                val local = release.apkUrl.startsWith("file://")
                val res = if (local) null else Api.client.newCall(Request.Builder().url(release.apkUrl).build()).await()
                if (res != null && !res.isSuccessful) throw IOException("Сервер ответил ${res.code}")
                val body = res?.let { it.body ?: throw IOException("Пустой ответ") }
                val total = body?.contentLength()?.takeIf { it > 0 } ?: release.apkBytes
                (body?.byteStream() ?: File(release.apkUrl.removePrefix("file://")).inputStream()).use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (local) kotlinx.coroutines.delay(20)
                            if (total > 0) _download.value = UpdateDownload.Running((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                _download.value = UpdateDownload.Ready(target)
            } catch (e: Exception) {
                target.delete()
                if (e is kotlinx.coroutines.CancellationException) { _download.value = UpdateDownload.Idle; throw e }
                _download.value = UpdateDownload.Failed(e.message ?: "Не удалось скачать обновление")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _download.value = UpdateDownload.Idle
    }

    /** Opens the system installer for a downloaded APK. Android asks for the "install unknown apps" permission itself. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        context.startActivity(intent)
    }
}
