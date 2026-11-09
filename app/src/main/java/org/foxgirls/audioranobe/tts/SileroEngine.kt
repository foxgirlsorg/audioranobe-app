package org.foxgirls.audioranobe.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.jtransforms.fft.DoubleFFT_1D
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.math.min

/** What the Python text front end calls back for its two neural steps (JSON in, JSON out). */
class NeuralCallback(private val f: (String) -> String) {
    fun call(json: String): String = f(json)
}

/**
 * Silero v5.5 RU on the device: ONNX Runtime for the nets, the downloaded Python front end (Chaquopy) for text
 * normalization, stress and homographs, JTransforms for the inverse STFT. A port of the site's engine.js
 * (itself verified against the original package), so both produce the same voice.
 * Not thread-safe: use from one thread.
 */
class SileroEngine(context: Context, private val dir: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors())) }
    private fun session(name: String) = env.createSession(File(dir, "$name.onnx").path, options)

    private val dur = session("dur")
    private val pitch = session("pitch")
    private val enc = session("enc")
    private val dec = session("dec")
    private val voc = session("voc")
    private val accent = session("accent")
    private val homo = session("homo")
    private val window = floats("istft_window.f32")
    private val emb = floats("accent_emb.f32")
    private val fft = DoubleFFT_1D(N_FFT.toLong())

    private val bridge: PyObject
    private val data: PyObject
    val speakers: Map<String, Int>

    init {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        bridge = Python.getInstance().getModule("narrator_bridge")
        data = bridge.callAttr("load", dir.path)
        val s = JSONObject(File(dir, "meta.json").readText()).getJSONObject("speakers")
        speakers = s.keys().asSequence().associateWith { s.getInt(it) }
    }

    private fun floats(name: String): FloatArray {
        val b = ByteBuffer.wrap(File(dir, name).readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(b.remaining()).also { b.get(it) }
    }

    private fun longs(a: LongArray, vararg shape: Long) = OnnxTensor.createTensor(env, LongBuffer.wrap(a), shape)
    private fun floats(a: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.wrap(a), shape)
    private fun OnnxTensor.toFloats() = FloatArray(floatBuffer.remaining()).also { floatBuffer.get(it) }
    private fun List<Int>.toLongs() = LongArray(size) { this[it].toLong() }

    /** Accent net: mean embedding of each word's n-grams → stress and yo scores. */
    private fun accent(json: String): String {
        val req = JSONObject(json)
        val ind = req.getJSONArray("ind").let { a -> IntArray(a.length()) { a.getInt(it) } }
        val offsets = req.getJSONArray("offsets").let { a -> IntArray(a.length()) { a.getInt(it) } }
        val n = offsets.size
        val e = FloatArray(n * 16)
        for (w in 0 until n) {
            val from = offsets[w]
            val to = if (w + 1 < n) offsets[w + 1] else ind.size
            for (i in from until to) for (d in 0 until 16) e[w * 16 + d] += emb[ind[i] * 16 + d]
            for (d in 0 until 16) e[w * 16 + d] /= (to - from).toFloat()
        }
        floats(e, n.toLong(), 16).use { t ->
            accent.run(mapOf("emb" to t)).use { r ->
                val stress = r.get("stress").get() as OnnxTensor
                val yo = r.get("yo").get() as OnnxTensor
                return JSONObject().put("stress", rows(stress)).put("yo", rows(yo)).toString()
            }
        }
    }

    private fun rows(t: OnnxTensor): JSONArray {
        val cols = t.info.shape[1].toInt()
        val flat = t.toFloats()
        val out = JSONArray()
        for (r in 0 until flat.size / cols) out.put(JSONArray().also { row -> for (c in 0 until cols) row.put(flat[r * cols + c].toDouble()) })
        return out
    }

    /** Homograph BERT: one logit per candidate sentence. */
    private fun homo(json: String): String {
        val req = JSONObject(json)
        val ids = req.getJSONArray("ids")
        val b = ids.length()
        val l = ids.getJSONArray(0).length()
        val flat = LongArray(b * l) { ids.getJSONArray(it / l).getLong(it % l) }
        val start = req.getJSONArray("start").let { a -> LongArray(a.length()) { a.getLong(it) } }
        val end = req.getJSONArray("end").let { a -> LongArray(a.length()) { a.getLong(it) } }
        longs(flat, b.toLong(), l.toLong()).use { i ->
            longs(start, b.toLong()).use { s ->
                longs(end, b.toLong()).use { e ->
                    homo.run(mapOf("ids" to i, "start" to s, "end" to e)).use { r ->
                        return JSONArray((r.get("logits").get() as OnnxTensor).toFloats().map { it.toDouble() }).toString()
                    }
                }
            }
        }
    }

    /** Sequence and type ids of one text piece → mono float audio at [TtsEngine.SAMPLE_RATE]. */
    private fun synthChunk(seq: List<Int>, typeIds: List<Int>, speakerId: Int): FloatArray {
        val t = seq.size
        val sq = seq.toLongs()
        longs(sq, 1, t.toLong()).use { sqT ->
            longs(longArrayOf(speakerId.toLong()), 1).use { spk ->
                val logDur = dur.run(mapOf("seq" to sqT, "spk" to spk)).use { (it.get("log_dur").get() as OnnxTensor).toFloats() }
                val d = FloatArray(t) { i -> Math.rint(maxOf(0.0, exp(logDur[i].toDouble()) - 1)).toFloat() }
                if (d[0] > 5) d[0] = 5f
                if (d[t - 1] > 7) d[t - 1] = 7f
                d[t - 2] = 13f
                if (d[t - 3] > 13) d[t - 3] = 13f

                val p = longs(typeIds.toLongs(), 1, t.toLong()).use { tid ->
                    pitch.run(mapOf("seq" to sqT, "spk" to spk, "tid" to tid)).use { it.get("pitch").get().let { v -> v as OnnxTensor }.toFloats() }
                }
                for (i in p.indices) if (Math.abs(p[i]) < 0.001f) p[i] = 0f
                val encoded = floats(p, 1, 1, t.toLong()).use { pt ->
                    enc.run(mapOf("seq" to sqT, "spk" to spk, "pitch" to pt)).use { (it.get("enc").get() as OnnxTensor).toFloats() }
                }
                val reps = IntArray(t) { (d[it] + 0.5f).toInt() }
                val frames = reps.sum()
                val x = FloatArray(frames * 128)
                var o = 0
                for (i in 0 until t) repeat(reps[i]) { System.arraycopy(encoded, i * 128, x, o, 128); o += 128 }

                floats(x, 1, frames.toLong(), 128).use { xt ->
                    dec.run(mapOf("x" to xt)).use { decoded ->
                        voc.run(mapOf("mel" to (decoded.get("mel").get() as OnnxTensor))).use { v ->
                            val re = v.get("re").get() as OnnxTensor
                            return istft(re.toFloats(), (v.get("im").get() as OnnxTensor).toFloats(), re.info.shape[2].toInt())
                        }
                    }
                }
            }
        }
    }

    /** torch.istft(padding="same") for spectra laid out [bins, frames]. */
    private fun istft(re: FloatArray, im: FloatArray, frames: Int): FloatArray {
        val len = (frames - 1) * HOP + N_FFT
        val y = DoubleArray(len)
        val env = DoubleArray(len)
        val a = DoubleArray(N_FFT)
        for (t in 0 until frames) {
            a[0] = re[t].toDouble()
            a[1] = re[(N_FFT / 2) * frames + t].toDouble()
            for (k in 1 until N_FFT / 2) {
                a[2 * k] = re[k * frames + t].toDouble()
                a[2 * k + 1] = im[k * frames + t].toDouble()
            }
            fft.realInverse(a, true)
            val base = t * HOP
            for (n in 0 until N_FFT) {
                y[base + n] += a[n] * window[n]
                env[base + n] += (window[n] * window[n]).toDouble()
            }
        }
        return FloatArray(len - 2 * PAD) { y[it + PAD].div(env[it + PAD]).toFloat() }
    }

    /**
     * Narrates [text] piece by piece, [pauseSeconds] of silence after each; [onChunk] gets 16-bit PCM.
     * A piece the front end cannot handle is skipped, like the narrator node does.
     */
    fun narrate(text: String, speaker: String, pauseSeconds: Double, cancelled: () -> Boolean, onChunk: (ShortArray) -> Unit) {
        val speakerId = speakers[speaker] ?: 0
        val pause = (TtsEngine.SAMPLE_RATE * pauseSeconds).toInt()
        val accentCb = NeuralCallback { accent(it) }
        val homoCb = NeuralCallback { homo(it) }
        for (piece in bridge.callAttr("pieces", text).asList().map { it.toString() }) {
            if (cancelled()) return
            val audio = try {
                val r = bridge.callAttr("prepare", piece, data, accentCb, homoCb).asList()
                synthChunk(r[0].asList().map { it.toInt() }, r[1].asList().map { it.toInt() }, speakerId)
            } catch (_: Exception) {
                continue
            }
            val out = ShortArray(audio.size + pause)
            for (i in audio.indices) out[i] = (audio[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            onChunk(out)
        }
    }

    override fun close() {
        listOf(dur, pitch, enc, dec, voc, accent, homo).forEach { it.close() }
        options.close()
    }

    private companion object {
        const val N_FFT = 2400
        const val HOP = 600
        const val PAD = (N_FFT - HOP) / 2
    }
}
