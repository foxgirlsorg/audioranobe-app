package org.foxgirls.audioranobe.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import org.foxgirls.audioranobe.MainActivity
import org.foxgirls.audioranobe.R
import org.foxgirls.audioranobe.core.Api
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Background playback with lock-screen / headset controls, like the web's Media Session.
 * The notification shows ±10s buttons instead of previous/next, matching lib/player.tsx.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpDataSource.Factory(Api.client).setUserAgent("AudioRanobe-Android")
        val ds = DefaultDataSource.Factory(this, http)
        val player = ExoPlayer.Builder(this)
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

        val back = CommandButton.Builder()
            .setDisplayName("Назад на 10 секунд")
            .setSessionCommand(SessionCommand(CMD_BACK, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_replay_10)
            .build()
        val fwd = CommandButton.Builder()
            .setDisplayName("Вперёд на 10 секунд")
            .setSessionCommand(SessionCommand(CMD_FWD, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_forward_10)
            .build()

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCustomLayout(ImmutableList.of(back, fwd))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_BACK, Bundle.EMPTY))
                        .add(SessionCommand(CMD_FWD, Bundle.EMPTY))
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
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val CMD_BACK = "audioranobe.back10"
        const val CMD_FWD = "audioranobe.fwd10"
    }
}
