package org.foxgirls.audioranobe.offline

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.AppJson
import org.foxgirls.audioranobe.data.ChapterPlay
import org.foxgirls.audioranobe.data.ChapterRow
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.data.TitleFull
import org.foxgirls.audioranobe.data.Volume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One downloaded chapter: the ChapterPlay payload as fetched plus the local audio file name. */
@Serializable
data class OfflineChapter(val play: ChapterPlay, val file: String, val bytes: Long = 0, val savedAt: Long = 0)

/** Everything the app keeps for one title on disk. */
@Serializable
data class OfflineManifest(
    val titleId: Int,
    val slug: String,
    val name: String,
    val savedAt: Long,
    val title: TitleFull,
    /** chapter id → downloaded audio + metadata */
    val chapters: Map<Int, OfflineChapter> = emptyMap(),
    /** remote image url → local file name (covers, banner, volume covers, illustrations) */
    val images: Map<String, String> = emptyMap(),
) {
    val bytes: Long get() = chapters.values.sumOf { it.bytes }
    val coverUrl: String? get() = title.cover_url
}

/** Listening progress made while offline; synced to the server when a connection is back. */
@Serializable
data class PendingProgress(val chapterId: Int, val titleId: Int, val number: Double, val position: Double, val updatedAt: Long)

@Serializable
private data class PendingFile(val items: List<PendingProgress> = emptyList())

sealed class DlState {
    data object Queued : DlState()
    data class Running(val fraction: Float) : DlState()
    data class Failed(val message: String) : DlState()
}

data class DownloadTask(val chapterId: Int, val titleId: Int, val titleName: String, val label: String)

/**
 * The app-only offline library: chapters, volumes or whole books saved on the
 * device together with the title info and images, playable without a network,
 * with progress kept locally until it can be synced.
 */
object OfflineStore {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val io = Mutex()
    private lateinit var root: File

    private val _titles = MutableStateFlow<List<OfflineManifest>>(emptyList())
    /** All downloaded titles, newest first. */
    val titles: StateFlow<List<OfflineManifest>> = _titles.asStateFlow()
    private val _queue = MutableStateFlow<List<DownloadTask>>(emptyList())
    val queue: StateFlow<List<DownloadTask>> = _queue.asStateFlow()
    private val _states = MutableStateFlow<Map<Int, DlState>>(emptyMap())
    /** Per-chapter download state; absent = not queued. */
    val states: StateFlow<Map<Int, DlState>> = _states.asStateFlow()
    private val _pending = MutableStateFlow<List<PendingProgress>>(emptyList())
    /** Progress records that still wait for a sync. */
    val pending: StateFlow<List<PendingProgress>> = _pending.asStateFlow()
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** url → absolute file path, for images that exist on disk. */
    private val imageIndex = ConcurrentHashMap<String, String>()
    private var worker: Job? = null
    private val cancelled = ConcurrentHashMap.newKeySet<Int>()

    fun init(context: Context) {
        appContext = context.applicationContext
        root = File(appContext.filesDir, "offline").apply { mkdirs() }
        scope.launch { load() }
        watchNetwork()
    }

    // ---------- disk ----------

    private fun titleDir(id: Int) = File(root, "titles/$id")
    private fun manifestFile(id: Int) = File(titleDir(id), "manifest.json")
    private fun pendingFile() = File(root, "progress.json")

    private suspend fun load() = io.withLock {
        val list = (File(root, "titles").listFiles() ?: emptyArray()).mapNotNull { dir ->
            runCatching { AppJson.decodeFromString(OfflineManifest.serializer(), File(dir, "manifest.json").readText()) }.getOrNull()
        }.sortedByDescending { it.savedAt }
        list.forEach { m -> m.images.forEach { (url, f) -> imageIndex[url] = File(titleDir(m.titleId), f).absolutePath } }
        _titles.value = list
        _pending.value = runCatching { AppJson.decodeFromString(PendingFile.serializer(), pendingFile().readText()).items }.getOrDefault(emptyList())
    }

    private suspend fun save(m: OfflineManifest) = io.withLock {
        titleDir(m.titleId).mkdirs()
        manifestFile(m.titleId).writeText(AppJson.encodeToString(m))
        m.images.forEach { (url, f) -> imageIndex[url] = File(titleDir(m.titleId), f).absolutePath }
        _titles.update { list -> (listOf(m) + list.filter { it.titleId != m.titleId }).sortedByDescending { it.savedAt } }
    }

    private suspend fun savePending() = io.withLock {
        pendingFile().writeText(AppJson.encodeToString(PendingFile(_pending.value)))
    }

    fun manifest(titleId: Int): OfflineManifest? = _titles.value.firstOrNull { it.titleId == titleId }
    fun manifestBySlug(slug: String): OfflineManifest? = _titles.value.firstOrNull { it.slug == slug }
    fun manifestForChapter(chapterId: Int): OfflineManifest? = _titles.value.firstOrNull { chapterId in it.chapters }
    fun isDownloaded(chapterId: Int): Boolean = manifestForChapter(chapterId) != null
    fun state(chapterId: Int): DlState? = _states.value[chapterId]

    /** Local path for a remote image when it was saved with a title, else the url itself. */
    fun resolveImage(url: String?): String? = url?.let { imageIndex[it] ?: it }

    /** Total bytes of all downloaded audio. */
    fun totalBytes(): Long = _titles.value.sumOf { it.bytes }

    /** A ChapterPlay that plays from the local file, with the locally known position. */
    fun chapterPlay(chapterId: Int): ChapterPlay? {
        val m = manifestForChapter(chapterId) ?: return null
        val oc = m.chapters[chapterId] ?: return null
        val f = File(titleDir(m.titleId), oc.file)
        if (!f.exists()) return null
        val local = _pending.value.firstOrNull { it.chapterId == chapterId }?.position
            ?: m.title.volumes.flatMap { it.chapters }.firstOrNull { it.id == chapterId }?.my_position
            ?: oc.play.my_position
        val ordered = playableIds(m.title)
        val idx = ordered.indexOf(chapterId)
        return oc.play.copy(
            audio_url = Uri.fromFile(f).toString(),
            my_position = local,
            prev_id = ordered.getOrNull(idx - 1) ?: oc.play.prev_id,
            next_id = ordered.getOrNull(idx + 1) ?: oc.play.next_id,
            illustrations = oc.play.illustrations.map { ill -> ill.copy(url = resolveImage(ill.url) ?: ill.url, thumb_url = resolveImage(ill.thumb_url) ?: ill.thumb_url) },
        )
    }

    private fun playableIds(t: TitleFull) = t.volumes.sortedBy { it.number }.flatMap { v -> v.liveChapters.sortedBy { it.number } }.filter { it.audio_status == "ready" }.map { it.id }

    /** The saved title with the locally known progress folded into its chapters. */
    fun offlineTitle(m: OfflineManifest): TitleFull {
        val local = _pending.value.filter { it.titleId == m.titleId }.associate { it.chapterId to it.position }
        if (local.isEmpty()) return m.title
        return m.title.copy(volumes = m.title.volumes.map { v -> v.copy(chapters = v.chapters.map { c -> local[c.id]?.let { p -> c.copy(my_position = p) } ?: c }) })
    }

    // ---------- downloads ----------

    /** Saves the title info + images now and queues the given chapters (all playable ones when null). */
    fun download(title: TitleFull, chapterIds: List<Int>? = null) {
        val wanted = (chapterIds ?: playableIds(title)).filter { id -> title.volumes.flatMap { it.chapters }.any { it.id == id && it.audio_status == "ready" } }
        scope.launch {
            val existing = manifest(title.id)
            save(OfflineManifest(title.id, title.slug, title.name, existing?.savedAt ?: System.currentTimeMillis(), title, existing?.chapters ?: emptyMap(), existing?.images ?: emptyMap()))
            launch { runCatching { saveImages(title) } }
            val tasks = wanted.filter { id -> existing?.chapters?.containsKey(id) != true && _queue.value.none { it.chapterId == id } }.map { id ->
                val ch = title.volumes.flatMap { it.chapters }.first { it.id == id }
                cancelled.remove(id)
                DownloadTask(id, title.id, title.name, org.foxgirls.audioranobe.core.Fmt.chapterLabel(ch.number, ch.number_end, ch.name))
            }
            if (tasks.isEmpty()) return@launch
            _queue.update { it + tasks }
            _states.update { s -> s + tasks.associate { it.chapterId to DlState.Queued } }
            startWorker()
        }
    }

    fun downloadVolume(title: TitleFull, volume: Volume) = download(title, volume.liveChapters.filter { it.audio_status == "ready" }.map { it.id })
    fun downloadChapter(title: TitleFull, chapter: ChapterRow) = download(title, listOf(chapter.id))

    fun cancel(chapterId: Int) {
        cancelled.add(chapterId)
        _queue.update { q -> q.filter { it.chapterId != chapterId } }
        _states.update { it - chapterId }
    }

    fun cancelAll() {
        _queue.value.forEach { cancelled.add(it.chapterId) }
        _queue.value = emptyList()
        _states.value = emptyMap()
    }

    private fun startWorker() {
        if (worker?.isActive == true) return
        DownloadService.start(appContext)
        worker = scope.launch {
            while (isActive) {
                val task = _queue.value.firstOrNull() ?: break
                try {
                    downloadOne(task)
                    _states.update { it - task.chapterId }
                } catch (e: Exception) {
                    if (task.chapterId !in cancelled) _states.update { it + (task.chapterId to DlState.Failed(e.message ?: "Ошибка загрузки")) }
                }
                _queue.update { q -> q.filter { it.chapterId != task.chapterId } }
            }
            DownloadService.stop(appContext)
        }
    }

    private suspend fun downloadOne(task: DownloadTask) {
        _states.update { it + (task.chapterId to DlState.Running(0f)) }
        val play = Api.get<ChapterPlay>("/chapters/${task.chapterId}")
        if (play.audio_url.isBlank()) error("У главы нет аудио")
        val ext = Uri.parse(play.audio_url).lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.length in 2..4 } ?: "m4a"
        val dir = File(titleDir(task.titleId), "chapters").apply { mkdirs() }
        val name = "chapters/${task.chapterId}.$ext"
        val tmp = File(dir, "${task.chapterId}.part")
        val bytes = fetchToFile(play.audio_url, tmp, task.chapterId) { f -> _states.update { it + (task.chapterId to DlState.Running(f)) } }
        if (task.chapterId in cancelled) { tmp.delete(); return }
        val dst = File(dir, "${task.chapterId}.$ext")
        if (!tmp.renameTo(dst)) { tmp.copyTo(dst, overwrite = true); tmp.delete() }
        // Illustrations of the chapter, for the full player.
        val images = mutableMapOf<String, String>()
        play.illustrations.forEach { ill -> listOfNotNull(ill.thumb_url.takeIf { it.isNotBlank() }, ill.url.takeIf { it.isNotBlank() }).forEach { u -> saveImage(task.titleId, u)?.let { images[u] = it } } }
        val m = manifest(task.titleId) ?: return
        save(m.copy(chapters = m.chapters + (task.chapterId to OfflineChapter(play, name, bytes, System.currentTimeMillis())), images = m.images + images))
    }

    private suspend fun fetchToFile(url: String, dst: File, chapterId: Int, onProgress: (Float) -> Unit): Long = withContext(Dispatchers.IO) {
        val res = Api.client.newCall(Request.Builder().url(url).header("User-Agent", "AudioRanobe-Android").build()).execute()
        res.use { r ->
            if (!r.isSuccessful) error("Сервер ответил ${r.code}")
            val body = r.body ?: error("Пустой ответ")
            val total = body.contentLength()
            var done = 0L
            var lastReport = 0L
            body.byteStream().use { input ->
                dst.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (chapterId in cancelled) throw kotlinx.coroutines.CancellationException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 256 * 1024) { lastReport = done; onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
            done
        }
    }

    private suspend fun saveImage(titleId: Int, url: String): String? {
        val m = manifest(titleId)
        m?.images?.get(url)?.let { if (File(titleDir(titleId), it).exists()) return it }
        return runCatching {
            val dir = File(titleDir(titleId), "images").apply { mkdirs() }
            val name = "images/" + Integer.toHexString(url.hashCode()) + "_" + (Uri.parse(url).lastPathSegment ?: "img").take(60)
            fetchToFile(url, File(dir, name.substringAfter("images/")), -1) {}
            name
        }.getOrNull()
    }

    private suspend fun saveImages(title: TitleFull) {
        val urls = buildList {
            title.cover_url?.let { add(it) }; title.cover_thumb_url?.let { add(it) }; title.bg_url?.let { add(it) }
            title.volumes.forEach { v -> v.cover_url?.let { add(it) }; v.cover_thumb_url?.let { add(it) } }
            title.illustrations.forEach { ill -> if (ill.thumb_url.isNotBlank()) add(ill.thumb_url); if (ill.url.isNotBlank()) add(ill.url) }
        }.distinct().filter { it.isNotBlank() }
        val saved = mutableMapOf<String, String>()
        urls.forEach { u -> saveImage(title.id, u)?.let { saved[u] = it } }
        val m = manifest(title.id) ?: return
        save(m.copy(images = m.images + saved))
    }

    /** Refreshes the saved title info from the server (keeps the audio files). */
    suspend fun refreshTitle(titleId: Int) {
        val m = manifest(titleId) ?: return
        val fresh = runCatching { Api.get<TitleFull>("/titles/$titleId") }.getOrNull() ?: return
        save(m.copy(title = fresh, slug = fresh.slug, name = fresh.name))
        runCatching { saveImages(fresh) }
    }

    fun removeChapter(chapterId: Int) {
        cancel(chapterId)
        scope.launch {
            val m = manifestForChapter(chapterId) ?: return@launch
            m.chapters[chapterId]?.let { File(titleDir(m.titleId), it.file).delete() }
            save(m.copy(chapters = m.chapters - chapterId))
        }
    }

    fun removeVolume(titleId: Int, volume: Volume) = volume.chapters.forEach { removeChapter(it.id) }

    fun removeTitle(titleId: Int) {
        _queue.value.filter { it.titleId == titleId }.forEach { cancel(it.chapterId) }
        scope.launch {
            io.withLock {
                val m = _titles.value.firstOrNull { it.titleId == titleId }
                m?.images?.keys?.forEach { imageIndex.remove(it) }
                titleDir(titleId).deleteRecursively()
                _titles.update { list -> list.filter { it.titleId != titleId } }
            }
        }
    }

    // ---------- progress ----------

    /** Called by the player when a save could not reach the server (or the chapter is offline). */
    fun recordProgress(play: ChapterPlay, position: Double) {
        val m = manifestForChapter(play.id) ?: manifest(play.title.id)
        val rec = PendingProgress(play.id, play.title.id, play.number, position, System.currentTimeMillis())
        _pending.update { list -> list.filter { it.chapterId != play.id } + rec }
        scope.launch {
            savePending()
            // Keep the saved title in step so the offline title page shows the progress.
            if (m != null) save(m.copy(title = m.title.copy(volumes = m.title.volumes.map { v -> v.copy(chapters = v.chapters.map { c -> if (c.id == play.id) c.copy(my_position = position) else c }) })))
        }
    }

    /** A save reached the server: the record is no longer pending. */
    fun markSynced(chapterId: Int) {
        if (_pending.value.none { it.chapterId == chapterId }) return
        _pending.update { list -> list.filter { it.chapterId != chapterId } }
        scope.launch { savePending() }
    }

    /**
     * Pushes the offline progress to the server, one chapter at a time. A record is
     * dropped without a write when the server already is further in the book:
     * a later chapter has progress there, or the same chapter is further along.
     */
    fun syncProgress() {
        if (_syncing.value || _pending.value.isEmpty() || Stores.auth.user.value == null) return
        _syncing.value = true
        scope.launch {
            try {
                val byTitle = _pending.value.groupBy { it.titleId }
                for ((titleId, recs) in byTitle) {
                    val server = runCatching { Api.get<TitleFull>("/titles/$titleId") }.getOrNull() ?: continue
                    val serverChapters = server.volumes.flatMap { it.liveChapters }
                    val furthest = serverChapters.filter { (it.my_position ?: 0.0) > 0 }.maxByOrNull { it.number }
                    for (rec in recs) {
                        val serverSame = serverChapters.firstOrNull { it.id == rec.chapterId }
                        val serverAhead = (furthest != null && furthest.number > rec.number) || ((serverSame?.my_position ?: 0.0) >= rec.position)
                        if (!serverAhead) {
                            val ok = runCatching { Api.put<Unit>("/me/progress/${rec.chapterId}", buildJsonObject { put("position", rec.position) }) }.isSuccess
                            if (!ok) continue
                        }
                        _pending.update { list -> list.filter { it.chapterId != rec.chapterId } }
                    }
                    savePending()
                    manifest(titleId)?.let { m -> runCatching { Api.get<TitleFull>("/titles/$titleId") }.getOrNull()?.let { save(m.copy(title = it)) } }
                }
            } finally { _syncing.value = false }
        }
    }

    // ---------- connectivity ----------

    private fun watchNetwork() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        fun current(): Boolean = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        _online.value = current()
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = true; syncProgress(); if (_queue.value.isNotEmpty()) startWorker() }
                override fun onLost(network: Network) { _online.value = current() }
            })
        }
    }
}

/** Foreground service that keeps the process alive while chapters download and shows the progress. */
class DownloadService : android.app.Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Загрузки", android.app.NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, build("Подготовка…", 0f))
        scope.launch {
            OfflineStore.queue.collect { q ->
                if (q.isEmpty()) { stopSelf(); return@collect }
                val head = q.first()
                val frac = (OfflineStore.states.value[head.chapterId] as? DlState.Running)?.fraction ?: 0f
                nm.notify(NOTIFICATION_ID, build("${head.titleName} · ${head.label} (осталось: ${q.size})", frac))
            }
        }
        scope.launch {
            OfflineStore.states.collect { st ->
                val head = OfflineStore.queue.value.firstOrNull() ?: return@collect
                val frac = (st[head.chapterId] as? DlState.Running)?.fraction ?: 0f
                nm.notify(NOTIFICATION_ID, build("${head.titleName} · ${head.label} (осталось: ${OfflineStore.queue.value.size})", frac))
            }
        }
    }

    private fun build(text: String, frac: Float): android.app.Notification =
        androidx.core.app.NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Загрузка глав")
            .setContentText(text)
            .setProgress(100, (frac * 100).toInt(), frac <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(android.app.PendingIntent.getActivity(this, 0, Intent(this, org.foxgirls.audioranobe.MainActivity::class.java).putExtra("open_route", "offline"), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
            .build()

    override fun onDestroy() { scope.coroutineContext[Job]?.cancel(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 41
        fun start(context: Context) { runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) } }
        fun stop(context: Context) { runCatching { context.stopService(Intent(context, DownloadService::class.java)) } }
    }
}
