package com.audioranobe.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.Fmt
import com.audioranobe.app.data.ChapterPlay
import com.audioranobe.app.data.Stores
import com.audioranobe.app.offline.OfflineStore
import com.audioranobe.app.ui.toast.toastError
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
import kotlin.math.pow

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
    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()
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
        _volume.value = Stores.prefs.volume.coerceIn(0f, 1f)
        connect()
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                c.playbackParameters = PlaybackParameters(_rate.value)
                c.volume = toGain(_volume.value)
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

    private fun toGain(v: Float): Float = v.toDouble().pow(2.5).toFloat()

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

    private fun mediaItem(ch: ChapterPlay): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(Fmt.chapterLabel(ch.number, ch.number_end, ch.name))
            .setArtist(ch.title.name)
            .setAlbumTitle(ch.narrator?.name)
            .setArtworkUri(ch.coverUrl?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder().setMediaId(ch.id.toString()).setUri(ch.audio_url).setMediaMetadata(md).build()
    }

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
        if (cur != null && cur.id != id) saveProgress()

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
        if (seq != loadSeq) return

        _current.value = ch
        _buffered.value = 0.0
        _duration.value = ch.duration_seconds
        val start = if (startAt != null) max(0.0, startAt) else max(0.0, (ch.my_position ?: 0.0) - 10)
        _position.value = start
        switching = true
        pendingStart = null
        c.setMediaItem(mediaItem(ch), (start * 1000).toLong())
        c.playbackParameters = PlaybackParameters(_rate.value)
        c.volume = toGain(_volume.value)
        c.prepare()
        c.playWhenReady = autoplay
        persistOpen()
        ensureSaveTicker()
        if (!autoplay) _loading.value = false
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

    fun setVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _volume.value = clamped
        controller?.volume = toGain(clamped)
        Stores.prefs.volume = clamped
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
