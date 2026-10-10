package org.foxgirls.audioranobe.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import org.foxgirls.audioranobe.R
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.ChapterPlay
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.offline.OfflineStore
import org.foxgirls.audioranobe.ui.toast.toastError
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.max

sealed class Sleep {
    data class Minutes(val minutes: Int) : Sleep()
    data object Chapter : Sleep()
}

/**
 * The global audio player, port of lib/player.tsx. Drives a Media3 session that lives in
 * PlaybackService so playback continues in the background and on the lock screen.
 */
object PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private lateinit var appContext: Context

    private val _current = MutableStateFlow<ChapterPlay?>(null)
    val current: StateFlow<ChapterPlay?> = _current.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _position = MutableStateFlow(0.0)
    val position: StateFlow<Double> = _position.asStateFlow()
    private val _buffered = MutableStateFlow(0.0)
    val buffered: StateFlow<Double> = _buffered.asStateFlow()
    private val _duration = MutableStateFlow(0.0)
    val duration: StateFlow<Double> = _duration.asStateFlow()
    private val _rate = MutableStateFlow(1f)
    val rate: StateFlow<Float> = _rate.asStateFlow()
    private val _sleep = MutableStateFlow<Sleep?>(null)
    val sleep: StateFlow<Sleep?> = _sleep.asStateFlow()
    private val _sleepRemaining = MutableStateFlow<Int?>(null)
    val sleepRemaining: StateFlow<Int?> = _sleepRemaining.asStateFlow()
    private val _full = MutableStateFlow(false)
    val full: StateFlow<Boolean> = _full.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loadSeq = 0
    private var pendingStart: Double? = null
    private var sleepUntil = 0L
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    private var restored = false

    val RATES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

    fun init(context: Context) {
        appContext = context.applicationContext
        _rate.value = Stores.prefs.rate.coerceIn(0.5f, 3f)
        connect()
    }

    @OptIn(UnstableApi::class)
    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                c.playbackParameters = PlaybackParameters(_rate.value)
                c.volume = 1f
                // The service may already be playing (app process was recreated).
                if (c.mediaItemCount > 0) {
                    val id = c.currentMediaItem?.mediaId?.toIntOrNull()
                    if (id != null && _current.value?.id != id) {
                        scope.launch { runCatching { _current.value = OfflineStore.chapterPlay(id) ?: Api.get<ChapterPlay>("/chapters/$id") } }
                    }
                    _playing.value = c.isPlaying
                    if (c.duration > 0) _duration.value = c.duration / 1000.0
                    startTicker()
                } else {
                    restoreLastOpen()
                }
            } catch (_: Exception) {
                controller = null
            }
        }, MoreExecutors.directExecutor())
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
            if (isPlaying) startTicker() else {
                stopTicker()
                tick()
                val c = controller ?: return
                if (c.playbackState != Player.STATE_ENDED && _current.value != null && !switching) saveProgress()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    switching = false
                    _loading.value = false
                    val c = controller ?: return
                    if (c.duration > 0) _duration.value = c.duration / 1000.0
                    pendingStart?.let { s -> pendingStart = null; c.seekTo((s * 1000).toLong()) }
                }
                Player.STATE_ENDED -> onEnded()
                Player.STATE_BUFFERING -> _loading.value = true
                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            switching = false
            _loading.value = false
            _playing.value = false
            if (_current.value != null) toastError("Не удалось воспроизвести главу")
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _rate.value = playbackParameters.speed
        }
    }

    private var switching = false


    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                tick()
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel(); ticker = null
    }

    private fun tick() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        _position.value = max(0.0, c.currentPosition / 1000.0)
        _buffered.value = max(0.0, c.bufferedPosition / 1000.0)
        if (c.duration > 0) _duration.value = c.duration / 1000.0
        if (_sleep.value is Sleep.Chapter && _duration.value > 0) {
            _sleepRemaining.value = max(0.0, _duration.value - _position.value).toInt()
        }
    }

    private var saveTicker: Job? = null
    private fun ensureSaveTicker() {
        if (saveTicker?.isActive == true) return
        saveTicker = scope.launch {
            while (isActive) {
                delay(10_000)
                if (_playing.value) { saveProgress(); persistOpen() }
            }
        }
    }

    private fun saveProgress(positionOverride: Double? = null) {
        val cur = _current.value ?: return
        if (Stores.auth.user.value == null) return
        val pos = positionOverride ?: _position.value
        if (pos.isNaN()) return
        val offline = OfflineStore.isDownloaded(cur.id)
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                Api.put<Unit>("/me/progress/${cur.id}", buildJsonObject { put("position", max(0.0, pos)) })
            }.isSuccess
            // Keep the progress on the device when the server is unreachable (and for downloaded
            // chapters, so the offline title page shows it); it syncs when a connection is back.
            if (ok) OfflineStore.markSynced(cur.id)
            if (!ok || offline) OfflineStore.recordProgress(cur, max(0.0, pos))
        }
    }

    private fun persistOpen() {
        val cur = _current.value ?: return
        Stores.prefs.openChapter = "${cur.id}:${_position.value.toInt()}"
    }

    private fun restoreLastOpen() {
        if (restored) return
        restored = true
        val saved = Stores.prefs.openChapter ?: return
        val id = saved.substringBefore(':').toIntOrNull() ?: return
        val pos = saved.substringAfter(':', "").toDoubleOrNull()
        scope.launch { runCatching { playChapter(id, pos, autoplay = false) } }
    }

    private fun mediaItem(ch: ChapterPlay, art: Uri): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(ch.title.name)
            .setArtist(Fmt.chapterLabel(ch.number, ch.number_end, ch.name))
            .setAlbumTitle(ch.narrator?.name)
            .setArtworkUri(art)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder().setMediaId(ch.id.toString()).setUri(ch.audio_url).setMediaMetadata(md).build()
    }

    /**
     * Android Auto can't load http or file artwork, so covers are downloaded (or copied from a
     * downloaded title) into the cache and handed out through the FileProvider; no cover → app icon.
     * [square] center-crops it for the browse list; the player gets the cover as is.
     */
    suspend fun carArt(url: String?, square: Boolean = true): Uri {
        val fallback = Uri.parse("android.resource://${appContext.packageName}/${R.drawable.ic_launcher_foreground}")
        val src = OfflineStore.resolveImage(url) ?: return fallback
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(appContext.cacheDir, "art").apply { mkdirs() }
                val file = File(dir, MessageDigest.getInstance("SHA-1").digest(src.toByteArray()).joinToString("") { "%02x".format(it) } + if (square) ".jpg" else ".img")
                if (!file.exists()) {
                    val tmp = File(dir, file.name + ".tmp")
                    if (src.startsWith("/")) File(src).copyTo(tmp, overwrite = true)
                    else Api.client.newCall(Request.Builder().url(src).build()).execute().use { r ->
                        check(r.isSuccessful)
                        val body = r.body ?: error("empty body")
                        tmp.outputStream().use { body.byteStream().copyTo(it) }
                    }
                    if (square) { squareCover(tmp, file); tmp.delete() } else tmp.renameTo(file)
                }
                val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
                CAR_HOSTS.forEach { runCatching { appContext.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                uri
            }.getOrDefault(fallback)
        }
    }

    /** Auto shows artwork in squares and stretches it, so covers are center-cropped to a square. */
    private fun squareCover(src: File, dst: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= COVER_PX) sample *= 2
        val bmp = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("not an image")
        val side = minOf(bmp.width, bmp.height)
        val x = (bmp.width - side) / 2
        val y = (bmp.height - side) / 2
        val out = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bmp, Rect(x, y, x + side, y + side), Rect(0, 0, COVER_PX, COVER_PX), Paint(Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
        dst.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        out.recycle()
    }

    private val CAR_HOSTS = listOf("com.google.android.projection.gearhead", "com.android.car.media")
    private const val COVER_PX = 512

    suspend fun playChapter(id: Int, startAt: Double? = null, autoplay: Boolean = true) {
        val c = controller ?: throw IllegalStateException("Плеер ещё не готов")
        val cur = _current.value
        if (cur != null && cur.id == id && c.mediaItemCount > 0) {
            if (startAt != null) {
                c.seekTo((max(0.0, startAt) * 1000).toLong())
                _position.value = max(0.0, startAt)
            }
            if (autoplay) c.play()
            return
        }
        val seq = ++loadSeq
        _loading.value = true
        // Downloaded chapters play from the device; otherwise stream, and fall back to the
        // saved copy of the chapter if the network is down.
        val ch = OfflineStore.chapterPlay(id) ?: try {
            Api.get<ChapterPlay>("/chapters/$id")
        } catch (e: Exception) {
            _loading.value = false
            throw e
        }
        val art = carArt(ch.coverUrl, square = false)
        if (seq != loadSeq) return

        val start = adopt(ch, startAt)
        c.setMediaItem(mediaItem(ch, art), (start * 1000).toLong())
        c.playbackParameters = PlaybackParameters(_rate.value)
        c.prepare()
        c.playWhenReady = autoplay
        if (!autoplay) _loading.value = false
    }

    /** Makes [ch] the current chapter (saving the one it replaces) and returns where to start it. */
    private fun adopt(ch: ChapterPlay, startAt: Double?): Double {
        val cur = _current.value
        if (cur != null && cur.id != ch.id) saveProgress()
        loadSeq++
        _current.value = ch
        _buffered.value = 0.0
        _duration.value = ch.duration_seconds
        val start = if (startAt != null) max(0.0, startAt) else max(0.0, (ch.my_position ?: 0.0) - 10)
        _position.value = start
        switching = true
        pendingStart = null
        persistOpen()
        ensureSaveTicker()
        return start
    }

    /** A chapter picked outside the app (Android Auto, media resumption): the session plays the returned item. */
    @OptIn(UnstableApi::class)
    suspend fun resolve(id: Int, startAt: Double? = null): MediaSession.MediaItemsWithStartPosition {
        val ch = OfflineStore.chapterPlay(id) ?: Api.get<ChapterPlay>("/chapters/$id")
        val art = carArt(ch.coverUrl, square = false)
        val start = adopt(ch, startAt)
        return MediaSession.MediaItemsWithStartPosition(listOf(mediaItem(ch, art)), 0, (start * 1000).toLong())
    }

    fun play(id: Int, startAt: Double? = null) {
        scope.launch {
            try { playChapter(id, startAt) } catch (e: Exception) { toastError(e) }
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (_current.value == null) return
        if (c.playbackState == Player.STATE_ENDED) { c.seekTo(0); c.play(); return }
        if (c.playbackState == Player.STATE_IDLE) { c.prepare(); c.play(); return }
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seek(seconds: Double) {
        val c = controller ?: return
        val cur = _current.value ?: return
        val d = if (_duration.value > 0) _duration.value else cur.duration_seconds
        val clamped = if (d > 0) seconds.coerceIn(0.0, d) else max(0.0, seconds)
        c.seekTo((clamped * 1000).toLong())
        _position.value = clamped
        saveProgress(clamped)
    }

    fun skip(delta: Double) {
        if (_current.value == null) return
        seek(_position.value + delta)
    }

    fun next() {
        _current.value?.next_id?.let { play(it) }
    }

    fun prev() {
        val cur = _current.value ?: return
        if (cur.prev_id != null) play(cur.prev_id) else seek(0.0)
    }

    fun setRate(r: Float) {
        val clamped = r.coerceIn(0.5f, 3f)
        _rate.value = clamped
        controller?.playbackParameters = PlaybackParameters(clamped)
        Stores.prefs.rate = clamped
    }

    fun setSleep(s: Sleep?) {
        _sleep.value = s
        sleepJob?.cancel()
        when (s) {
            null -> { sleepUntil = 0; _sleepRemaining.value = null }
            is Sleep.Chapter -> {
                sleepUntil = 0
                _sleepRemaining.value = if (_duration.value > 0) max(0.0, _duration.value - _position.value).toInt() else null
            }
            is Sleep.Minutes -> {
                sleepUntil = System.currentTimeMillis() + s.minutes * 60_000L
                _sleepRemaining.value = s.minutes * 60
                sleepJob = scope.launch {
                    while (isActive) {
                        delay(1000)
                        val remaining = max(0L, (sleepUntil - System.currentTimeMillis() + 500) / 1000).toInt()
                        _sleepRemaining.value = remaining
                        if (remaining <= 0) {
                            controller?.pause()
                            _sleep.value = null; sleepUntil = 0; _sleepRemaining.value = null
                            break
                        }
                    }
                }
            }
        }
    }

    fun setFull(v: Boolean) {
        _full.value = v && _current.value != null
    }

    fun stop() {
        val c = controller
        loadSeq++
        if (c != null && _current.value != null) {
            saveProgress()
            c.pause()
            c.stop()
            c.clearMediaItems()
        }
        Stores.prefs.openChapter = null
        _current.value = null
        _playing.value = false
        _position.value = 0.0
        _duration.value = 0.0
        _buffered.value = 0.0
        _full.value = false
        setSleep(null)
        stopTicker()
    }

    private fun onEnded() {
        val cur = _current.value ?: return
        val endPos = if (_duration.value > 0) _duration.value else cur.duration_seconds
        saveProgress(endPos)
        if (_sleep.value is Sleep.Chapter) {
            _sleep.value = null; _sleepRemaining.value = null
            _playing.value = false
            return
        }
        if (cur.next_id != null) play(cur.next_id) else _playing.value = false
    }
}
