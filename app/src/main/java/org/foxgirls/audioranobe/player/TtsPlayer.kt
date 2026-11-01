package org.foxgirls.audioranobe.player

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.foxgirls.audioranobe.tts.SentenceSpeaker

/**
 * The media session's player while a chapter is read aloud on the device instead of played from a file.
 * Positions and the duration are on the stored-length scale (characters ÷ 14, like a text chapter's
 * `duration_seconds`), so the rest of the app treats them like audio seconds.
 */
@UnstableApi
class TtsPlayer(looper: Looper) : SimpleBasePlayer(looper) {
    private var item: MediaItem? = null
    private var speaker: SentenceSpeaker? = null
    private var started = false
    private var cueSeconds = 0.0
    private var playWhenReady = false
    private var rate = 1f
    private var error: PlaybackException? = null

    private val events = object : SentenceSpeaker.Listener {
        override fun onChange() { invalidateState() }
        override fun onEnd() { invalidateState() }
        override fun onError(message: String) {
            error = PlaybackException(message, null, PlaybackException.ERROR_CODE_UNSPECIFIED)
            playWhenReady = false
            invalidateState()
        }
    }

    /** Takes over with a chapter; nothing is synthesized until the first play or seek. */
    fun load(item: MediaItem, startSeconds: Double, play: Boolean, create: (SentenceSpeaker.Listener) -> SentenceSpeaker) {
        unload()
        this.item = item
        error = null
        val s = create(events)
        s.setRate(rate)
        speaker = s
        cueSeconds = startSeconds
        s.cue(s.sentenceAt(startSeconds))
        started = false
        playWhenReady = play
        if (play) begin()
        invalidateState()
    }

    val totalSeconds: Double get() = speaker?.totalSeconds ?: 0.0

    fun unload() {
        speaker?.release()
        speaker = null
        item = null
        started = false
        playWhenReady = false
        invalidateState()
    }

    private fun begin() {
        val s = speaker ?: return
        started = true
        s.start(s.sentenceAt(cueSeconds), true)
    }

    override fun getState(): State {
        val s = speaker
        val b = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackParameters(PlaybackParameters(rate))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
        val mi = item
        if (s == null || mi == null) return b.setPlaybackState(STATE_IDLE).build()
        error?.let { return b.setPlaybackState(STATE_IDLE).setPlayerError(it).build() }
        val state = when {
            s.finished -> STATE_ENDED
            s.busy && started && playWhenReady -> STATE_BUFFERING
            else -> STATE_READY
        }
        return b.setPlaybackState(state)
            .setPlaylist(listOf(MediaItemData.Builder("tts").setMediaItem(mi).setDurationUs((s.totalSeconds * 1_000_000).toLong()).setIsSeekable(true).build()))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(PositionSupplier { if (started || s.finished) (s.progressSeconds() * 1000).toLong() else (cueSeconds * 1000).toLong() })
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        val s = speaker
        if (s != null) {
            if (!playWhenReady) s.pause()
            else if (!started) begin()
            else s.resume()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        speaker?.pause()
        playWhenReady = false
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        unload()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val s = speaker ?: return Futures.immediateVoidFuture()
        val now = s.progressSeconds() * 1000
        val target = when (seekCommand) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> now + 10_000
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> now - 10_000
            else -> positionMs.toDouble()
        }
        cueSeconds = (target / 1000).coerceIn(0.0, s.totalSeconds)
        started = true
        s.start(s.sentenceAt(cueSeconds), playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        rate = playbackParameters.speed
        speaker?.setRate(rate)
        return Futures.immediateVoidFuture()
    }

    private companion object {
        val COMMANDS: Player.Commands = Player.Commands.Builder().addAll(
            COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_RELEASE,
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD,
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA, COMMAND_SET_SPEED_AND_PITCH,
        ).build()
    }
}
