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
import kotlinx.serialization.Serializable
import okhttp3.Request
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

@Serializable
private data class GhAsset(val name: String = "", val browser_download_url: String = "", val size: Long = 0, val content_type: String = "")

@Serializable
private data class GhRelease(
    val tag_name: String = "",
    val name: String? = null,
    val body: String? = null,
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val published_at: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

/**
 * Simple OTA: polls the GitHub Releases of [BuildConfig.UPDATE_REPO] (set at build time, never
 * hard-coded here), compares the newest tag with the running version and, on request, downloads the
 * APK asset and hands it to the system package installer.
 *
 * No token is needed: releases of a public repository are readable anonymously, and the rate limit
 * (60 requests/hour per IP) is far above the one check per app start we do.
 */
object Updater {
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val KEY_LAST_CHECK = "update_last_check"
    private const val KEY_SKIPPED = "update_skipped_tag"

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
     * Fetches the latest release. [manual] checks ignore the throttle and the "skip this version" mark
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
                val skipped = Stores.prefs.getString(KEY_SKIPPED)
                if (manual || skipped != release.tag) _available.value = release
            } else if (manual) {
                _available.value = null
            }
            withContext(Dispatchers.Main) { onResult?.invoke(release, error) }
        }
    }

    private suspend fun fetchLatest(): AppRelease? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases?per_page=10")
            .header("Accept", "application/vnd.github+json")
            .build()
        val res = Api.client.newCall(req).await()
        val text = res.body?.string()
        if (!res.isSuccessful) throw IOException("GitHub ответил ${res.code}")
        val list = AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GhRelease.serializer()), text ?: "[]")
        // Newest published, non-draft, non-prerelease release that ships an APK.
        val gh = list.firstOrNull { !it.draft && !it.prerelease && it.assets.any { a -> a.name.endsWith(".apk", true) } } ?: return null
        val apk = gh.assets.first { it.name.endsWith(".apk", true) }
        return AppRelease(
            tag = gh.tag_name,
            version = gh.tag_name.removePrefix("v"),
            name = gh.name?.takeIf { it.isNotBlank() } ?: gh.tag_name,
            notes = gh.body.orEmpty(),
            pageUrl = gh.html_url,
            apkUrl = apk.browser_download_url,
            apkBytes = apk.size,
            publishedAt = gh.published_at.orEmpty(),
        )
    }

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

    /** Never show this release again (a newer one still will). */
    fun skip(release: AppRelease) {
        Stores.prefs.putString(KEY_SKIPPED, release.tag)
        _available.value = null
        _dismissed.value = false
    }

    fun startDownload(release: AppRelease) {
        if (_download.value is UpdateDownload.Running) return
        _download.value = UpdateDownload.Running(0f)
        downloadJob = scope.launch {
            val target = File(cacheDir(), "audioranobe-${release.version}.apk")
            try {
                val res = Api.client.newCall(Request.Builder().url(release.apkUrl).build()).await()
                if (!res.isSuccessful) throw IOException("Сервер ответил ${res.code}")
                val body = res.body ?: throw IOException("Пустой ответ")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkBytes
                body.byteStream().use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
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
