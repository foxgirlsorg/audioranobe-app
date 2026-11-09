package org.foxgirls.audioranobe.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/**
 * Silero voice: [TtsEngine] synthesizes ahead, an AudioTrack plays it (speed without pitch change comes from the
 * track itself). Reports which sentence is audible.
 */
class SileroSpeaker(
    context: Context,
    sentences: List<String>,
    skipped: Int,
    private val voice: String,
    private val pauseSeconds: Double,
    listener: Listener,
) : SentenceSpeaker(sentences, skipped, listener) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var track: AudioTrack? = null
    private var jobs: List<Job> = emptyList()
    private var generation = 0
    private val startFrame = LongArray(sentences.size) { Long.MAX_VALUE }
    private val lengthFrames = LongArray(sentences.size)
    private val queued = AtomicLong()
    @Volatile private var produced = false
    private var focus: AudioFocusRequest? = null
    private var noisy: BroadcastReceiver? = null

    override fun progressSeconds(): Double {
        val i = index.coerceIn(0, sentences.size - 1)
        val t = track
        val within = if (t != null && i >= skipped && lengthFrames[i] > 0 && startFrame[i] != Long.MAX_VALUE) {
            ((frames(t) - startFrame[i]).toDouble() / lengthFrames[i]).coerceIn(0.0, 1.0)
        } else 0.0
        return seconds(within)
    }

    override fun start(from: Int, play: Boolean) {
        halt()
        if (sentences.isEmpty()) { finished = true; listener.onEnd(); return }
        generation++
        index = from.coerceIn(0, sentences.size - 1)
        finished = false
        busy = true
        playing = play
        produced = false
        queued.set(0)
        startFrame.fill(Long.MAX_VALUE)
        val t = newTrack()
        track = t
        t.applyRate()
        if (play) { requestFocus(); t.play(); watchNoisy() }
        val g = generation
        val chunks = Channel<Pair<Int, ShortArray>>(Channel.UNLIMITED)
        jobs = listOf(
            scope.launch(Dispatchers.Default) { produce(g, index, chunks) },
            scope.launch(Dispatchers.IO) { write(g, t, chunks) },
            scope.launch { follow(g, t) },
        )
        listener.onChange()
    }

    override fun pause() {
        if (!playing) return
        playing = false
        track?.pause()
        abandonFocus()
        listener.onChange()
    }

    override fun resume() {
        if (playing || finished) return
        val t = track ?: return
        playing = true
        requestFocus()
        t.applyRate()
        t.play()
        watchNoisy()
        listener.onChange()
    }

    override fun setRate(r: Float) {
        super.setRate(r)
        if (playing) track?.applyRate()
    }

    override fun release() {
        halt()
        scope.cancel()
    }

    private fun halt() {
        generation++
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        runCatching { track?.pause(); track?.flush() }
        track?.release()
        track = null
        abandonFocus()
        unwatchNoisy()
        playing = false
    }

    private fun newTrack(): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(TtsEngine.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(TtsEngine.SAMPLE_RATE * 2 * 2)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private fun AudioTrack.applyRate() {
        runCatching { playbackParams = PlaybackParams().setSpeed(rate).setPitch(1f) }
    }

    private fun frames(t: AudioTrack): Long = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    /** Synthesizes sentence after sentence, staying at most [LOOKAHEAD_SECONDS] of audio ahead of the listener. */
    private suspend fun produce(g: Int, from: Int, out: Channel<Pair<Int, ShortArray>>) {
        var i = from
        try {
            while (i < sentences.size && g == generation) {
                val t = track
                while (t != null && g == generation && (queued.get() - frames(t)) / TtsEngine.SAMPLE_RATE > LOOKAHEAD_SECONDS) delay(250)
                if (g != generation) return
                val parts = ArrayList<ShortArray>()
                TtsEngine.synth(sentences[i], voice, pauseSeconds) { parts.add(it) }
                val joined = ShortArray(parts.sumOf { it.size })
                var at = 0
                for (p in parts) { p.copyInto(joined, at); at += p.size }
                if (joined.isNotEmpty()) {
                    queued.addAndGet(joined.size.toLong())
                    out.send(i to joined)
                }
                i++
            }
            produced = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (g == generation) scope.launch { fail(e.message ?: "Озвучка не удалась") }
        } finally {
            out.close()
        }
    }

    private suspend fun write(g: Int, t: AudioTrack, chunks: Channel<Pair<Int, ShortArray>>) {
        var written = 0L
        try {
            for ((i, pcm) in chunks) {
                if (g != generation) return
                startFrame[i] = written
                lengthFrames[i] = pcm.size.toLong()
                written += pcm.size
                var off = 0
                while (off < pcm.size && g == generation) {
                    val n = t.write(pcm, off, pcm.size - off, AudioTrack.WRITE_BLOCKING)
                    if (n < 0) return
                    off += n
                }
            }
        } catch (_: Exception) {
            // the track was released by halt()
        }
    }

    /** Follows the playback head: which sentence is audible, whether nothing is, and when the chapter is over. */
    private suspend fun follow(g: Int, t: AudioTrack) {
        while (scope.isActive && g == generation) {
            delay(100)
            val head = frames(t)
            var now = -1
            for (i in index.coerceAtMost(sentences.size - 1) until sentences.size) { if (startFrame[i] <= head) now = i else break }
            var changed = false
            if (now >= 0 && now != index) { index = now; changed = true }
            val starved = !produced && queued.get() - head < TtsEngine.SAMPLE_RATE / 8
            if (starved != busy) { busy = starved; changed = true }
            if (produced && head >= queued.get()) {
                finished = true
                playing = false
                busy = false
                halt()
                listener.onChange()
                listener.onEnd()
                return
            }
            if (changed) listener.onChange()
        }
    }

    private fun fail(message: String) {
        halt()
        busy = false
        listener.onChange()
        listener.onError(message)
    }

    private fun requestFocus() {
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) scope.launch { pause() }
            }
            .build()
        audio.requestAudioFocus(req)
        focus = req
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun watchNoisy() {
        if (noisy != null) return
        noisy = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { pause() }
        }.also { app.registerReceiver(it, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) }
    }

    private fun unwatchNoisy() {
        noisy?.let { runCatching { app.unregisterReceiver(it) } }
        noisy = null
    }

    private companion object {
        const val LOOKAHEAD_SECONDS = 25
    }
}
