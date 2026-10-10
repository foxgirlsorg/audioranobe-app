package org.foxgirls.audioranobe.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import org.foxgirls.audioranobe.MainActivity
import org.foxgirls.audioranobe.R
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.AppJson
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.ContinueItem
import org.foxgirls.audioranobe.data.HomeData
import org.foxgirls.audioranobe.data.SearchSuggest
import org.foxgirls.audioranobe.data.TitleFull
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.offline.OfflineStore
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/**
 * Background playback with lock-screen / headset controls, like the web's Media Session, and the
 * Android Auto browse tree: "Продолжить" (default tab) and "Загрузки" (downloaded titles).
 * The notification and Auto show ±10s in the main slots, matching lib/player.tsx; chapter
 * prev/next and speed go to the secondary/overflow slots.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpDataSource.Factory(Api.client).setUserAgent("AudioRanobe-Android")
        val ds = DefaultDataSource.Factory(this, http)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 180_000, 2_500, 5_000)
                    .build(),
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply { putExtra("open_player", true) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val s = MediaLibrarySession.Builder(this, ChapterPlayer(exo), LibraryCallback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(buttons(exo.playbackParameters.speed))
            .build()
        session = s
        exo.addListener(object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                s.setMediaButtonPreferences(buttons(playbackParameters.speed))
            }
        })
        scope.launch { OfflineStore.titles.collect { s.notifyChildrenChanged(DOWNLOADS, Int.MAX_VALUE, null) } }
        scope.launch { PlayerController.current.collect { s.notifyChildrenChanged(CONTINUE, Int.MAX_VALUE, null) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun buttons(speed: Float): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName("Назад на 10 секунд")
            .setSessionCommand(SessionCommand(CMD_BACK, Bundle.EMPTY))
            .setCustomIconResId(R.drawable.ic_replay_10)
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setDisplayName("Вперёд на 10 секунд")
            .setSessionCommand(SessionCommand(CMD_FWD, Bundle.EMPTY))
            .setCustomIconResId(R.drawable.ic_forward_10)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Предыдущая глава")
            .setSessionCommand(SessionCommand(CMD_PREV_CHAPTER, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Следующая глава")
            .setSessionCommand(SessionCommand(CMD_NEXT_CHAPTER, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(speedIcon(speed))
            .setDisplayName("Скорость ${Fmt.trimNum(speed.toDouble())}×")
            .setSessionCommand(SessionCommand(CMD_SPEED, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )

    private fun speedIcon(s: Float) = when {
        s < 0.65f -> CommandButton.ICON_PLAYBACK_SPEED_0_5
        s < 0.9f -> CommandButton.ICON_PLAYBACK_SPEED_0_8
        s < 1.1f -> CommandButton.ICON_PLAYBACK_SPEED_1_0
        s < 1.35f -> CommandButton.ICON_PLAYBACK_SPEED_1_2
        s < 1.65f -> CommandButton.ICON_PLAYBACK_SPEED_1_5
        s < 1.9f -> CommandButton.ICON_PLAYBACK_SPEED_1_8
        s < 2.25f -> CommandButton.ICON_PLAYBACK_SPEED_2_0
        else -> CommandButton.ICON_PLAYBACK_SPEED
    }

    private fun <T> async(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        scope.launch { try { f.set(block()) } catch (e: Exception) { f.setException(e) } }
        return f
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_BACK, Bundle.EMPTY))
                .add(SessionCommand(CMD_FWD, Bundle.EMPTY))
                .add(SessionCommand(CMD_SPEED, Bundle.EMPTY))
                .add(SessionCommand(CMD_PREV_CHAPTER, Bundle.EMPTY))
                .add(SessionCommand(CMD_NEXT_CHAPTER, Bundle.EMPTY))
                .remove(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle,
        ): ListenableFuture<SessionResult> {
            val p = session.player
            when (customCommand.customAction) {
                CMD_BACK -> p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                CMD_FWD -> p.seekTo((p.currentPosition + 10_000).coerceAtMost(if (p.duration > 0) p.duration else Long.MAX_VALUE))
                CMD_PREV_CHAPTER -> PlayerController.prev()
                CMD_NEXT_CHAPTER -> PlayerController.next()
                CMD_SPEED -> {
                    val cur = p.playbackParameters.speed
                    PlayerController.setRate(CAR_RATES.firstOrNull { it > cur + 0.01f } ?: CAR_RATES.first())
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            }
            return Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "AudioRanobe"), LibraryParams.Builder().setExtras(extras).build()))
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (page > 0) return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            return async {
                val items = when (parentId) {
                    ROOT -> listOf(
                        folder(CONTINUE, "Продолжить", playableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM),
                        folder(DOWNLOADS, "Загрузки", browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM),
                    )
                    CONTINUE -> continueItems()
                    DOWNLOADS -> downloadedTitles()
                    else -> parentId.removePrefix(TITLE_PREFIX).toIntOrNull()?.let { titleChapters(it) }
                }
                if (items == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else LibraryResult.ofItemList(items, params)
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            if (mediaItems.all { it.localConfiguration != null }) {
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            mediaItems.firstOrNull()?.requestMetadata?.searchQuery?.let { q ->
                return async { PlayerController.resolve(searchChapter(q) ?: throw UnsupportedOperationException("nothing found")) }
            }
            val id = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId?.toIntOrNull()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("unknown media id"))
            val start = startPositionMs.takeIf { it != C.TIME_UNSET && it > 0 }?.div(1000.0)
            return async { PlayerController.resolve(id, start) }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val saved = Stores.prefs.openChapter
            val id = saved?.substringBefore(':')?.toIntOrNull()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("nothing to resume"))
            return async { PlayerController.resolve(id, saved.substringAfter(':', "").toDoubleOrNull()) }
        }
    }

    private suspend fun continueList(): List<ContinueItem> = try {
        Api.get<HomeData>("/home").continueItems
    } catch (_: Exception) {
        val cached = Stores.prefs.getString("home_continue_${Stores.auth.user.value?.id ?: 0}")
            ?.let { runCatching { AppJson.decodeFromString(ListSerializer(ContinueItem.serializer()), it) }.getOrNull() }
        OfflineStore.continueFallback(cached ?: emptyList())
    }

    /**
     * Voice "play …" / «включи …»: a query with nothing but filler words resumes; otherwise the first
     * title in progress, then downloaded, then from the catalog search whose name matches.
     */
    private suspend fun searchChapter(query: String): Int? {
        val words = searchWords(query)
        if (words.isEmpty()) return Stores.prefs.openChapter?.substringBefore(':')?.toIntOrNull() ?: continueList().firstOrNull()?.chapter?.id
        continueList().firstOrNull { nameMatches(it.title.name, words) }?.let { return it.chapter.id }
        OfflineStore.titles.value.firstOrNull { nameMatches(it.name, words) }?.let { m ->
            return titleChapters(m.titleId)?.firstOrNull { it.mediaMetadata.extras?.getInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS) != MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED }
                ?.mediaId?.toIntOrNull()
        }
        val found = runCatching { Api.get<SearchSuggest>("/search/suggest", mapOf("q" to words.joinToString(" "))) }.getOrNull()?.titles?.firstOrNull() ?: return null
        val t = Api.get<TitleFull>("/titles/${found.id}")
        val chapters = t.volumes.sortedBy { it.number }.flatMap { v -> v.liveChapters.sortedBy { it.number } }.filter { it.playable }
        return (chapters.firstOrNull { (it.my_position ?: 0.0) > 0 && it.my_position!! < it.duration_seconds - 5 } ?: chapters.firstOrNull())?.id
    }

    private suspend fun continueItems(): List<MediaItem> {
        return continueList().map { c ->
            chapterItem(c.chapter.id, c.title.name, Fmt.chapterLabel(c.chapter.number, null, c.chapter.name), c.title.cover_url, c.position_seconds, c.chapter.duration_seconds)
        }
    }

    private suspend fun downloadedTitles(): List<MediaItem> = OfflineStore.titles.value.sortedBy { it.name }.map { m ->
        val n = m.chapters.size
        MediaItem.Builder()
            .setMediaId(TITLE_PREFIX + m.titleId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(m.name)
                    .setSubtitle("$n ${Fmt.plural(n, "глава", "главы", "глав")}")
                    .setArtworkUri(PlayerController.carArt(m.coverUrl))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                    .build(),
            )
            .build()
    }

    private suspend fun titleChapters(titleId: Int): List<MediaItem>? {
        val m = OfflineStore.manifest(titleId) ?: return null
        val t = OfflineStore.offlineTitle(m)
        return t.volumes.sortedBy { it.number }.flatMap { v ->
            val group = listOf("${t.volume_label} ${v.num}", v.name).filter { it.isNotBlank() }.joinToString(" · ")
            v.liveChapters.sortedBy { it.number }.filter { m.chapters.containsKey(it.id) }.map { c ->
                chapterItem(c.id, Fmt.chapterLabel(c.number, c.number_end, c.name), t.name, v.cover_url ?: t.cover_url, c.my_position ?: 0.0, c.duration_seconds, group)
            }
        }
    }

    private suspend fun chapterItem(id: Int, title: String, subtitle: String, cover: String?, position: Double, duration: Double, group: String? = null): MediaItem {
        val extras = Bundle()
        if (duration > 0 && position > 0) {
            val done = position >= duration - 5
            extras.putInt(
                MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                if (done) MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED else MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
            )
            extras.putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, (position / duration).coerceIn(0.0, 1.0))
        }
        group?.let { extras.putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(PlayerController.carArt(cover))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    private fun folder(id: String, title: String, browsableStyle: Int? = null, playableStyle: Int? = null): MediaItem {
        val extras = Bundle().apply {
            browsableStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it) }
            playableStyle?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it) }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    /** Previous/next media buttons (steering wheel, headset) seek ±10s; chapters change through the custom buttons. */
    private class ChapterPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
        override fun getState(): SimpleBasePlayer.State {
            val s = super.getState()
            return s.buildUpon().setAvailableCommands(s.availableCommands.buildUpon().addAll(*SKIP_COMMANDS).build()).build()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> = when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> { player.seekForward(); Futures.immediateVoidFuture() }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> { player.seekBack(); Futures.immediateVoidFuture() }
            else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
    }

    companion object {
        const val CMD_BACK = "audioranobe.back10"
        const val CMD_FWD = "audioranobe.fwd10"
        const val CMD_SPEED = "audioranobe.speed"
        const val CMD_PREV_CHAPTER = "audioranobe.prevChapter"
        const val CMD_NEXT_CHAPTER = "audioranobe.nextChapter"
        private const val ROOT = "root"
        private const val CONTINUE = "continue"
        private const val DOWNLOADS = "downloads"
        private const val TITLE_PREFIX = "t:"
        private val SEARCH_FILLER = setOf(
            "включи", "включить", "поставь", "запусти", "играй", "сыграй", "воспроизведи", "слушать", "послушать",
            "продолжи", "продолжить", "дальше", "аудиокнигу", "аудиокнига", "книгу", "книга", "ранобэ", "ранобе",
            "новеллу", "новелла", "главу", "глава", "аудиоранобэ", "аудиоранобе", "audioranobe", "в", "на", "из", "мне",
            "play", "resume", "continue", "audiobook", "book", "on", "in",
        )

        private fun normalize(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        private fun searchWords(query: String) = normalize(query).split(' ').filter { it.isNotEmpty() && it !in SEARCH_FILLER }

        /** Every spoken word appears in the name; long Russian words match by stem so «войну» finds «Война». */
        private fun nameMatches(name: String, words: List<String>): Boolean {
            val n = normalize(name)
            return words.all { w -> n.contains(if (w.length > 4) w.dropLast(2) else w) }
        }

        private val CAR_RATES = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        private val SKIP_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
    }
}
