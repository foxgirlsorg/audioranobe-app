package com.audioranobe.app.ui.screens.editing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.audioranobe.app.core.Api
import com.audioranobe.app.data.UploadSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A file the user picked, with the metadata the upload endpoints need. */
data class PickedFile(val uri: Uri, val name: String, val size: Long)

private val AUDIO_RE = Regex("\\.(mp3|m4a|m4b|aac|wav|ogg|opus|flac)$", RegexOption.IGNORE_CASE)
const val MAX_AUDIO_BYTES = 2L * 1024 * 1024 * 1024
val AUDIO_MIME = arrayOf("audio/*", "application/octet-stream")

fun Context.describeFile(uri: Uri): PickedFile {
    var name = uri.lastPathSegment ?: "file"
    var size = 0L
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ni >= 0) c.getString(ni)?.let { name = it }
            val si = c.getColumnIndex(OpenableColumns.SIZE); if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
        }
    }
    if (size == 0L) size = runCatching { contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L }.getOrDefault(0L)
    return PickedFile(uri, name, size)
}

/** Mirrors TitleContentManager.validAudio: null when fine, else the error text. */
fun validAudio(f: PickedFile): String? {
    if (!AUDIO_RE.containsMatchIn(f.name)) return "${f.name}: неподдерживаемый формат"
    if (f.size > MAX_AUDIO_BYTES) return "${f.name}: файл слишком большой (макс. 2 ГБ)"
    return null
}

private const val MAX_CHUNK_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 1200L

/** lib/upload.ts: resumable chunked upload through POST /panel/uploads + PUT /panel/uploads/{id}?offset=. Returns the upload id. */
suspend fun uploadInChunks(context: Context, file: PickedFile, kind: String? = null, onProgress: (Float) -> Unit = {}): Int = withContext(Dispatchers.IO) {
    val session = Api.post<UploadSession>("/panel/uploads", buildJsonObject {
        put("filename", file.name); put("size", file.size); if (kind != null) put("kind", kind)
    })
    val chunkSize = (if (session.chunk_size > 0) session.chunk_size else 5L * 1024 * 1024).toInt()
    var offset = session.received
    fun report(sent: Long) = onProgress(if (file.size > 0) minOf(1f, sent.toFloat() / file.size) else 1f)
    report(offset)
    var failures = 0
    while (offset < file.size) {
        val start = offset
        val len = minOf(chunkSize.toLong(), file.size - start).toInt()
        val bytes = readRange(context, file.uri, start, len)
        try {
            val next = Api.putBytes<UploadSession>("/panel/uploads/${session.id}", mapOf("offset" to start), bytes)
            offset = next.received
            failures = 0
            report(offset)
        } catch (e: Exception) {
            failures++
            if (failures >= MAX_CHUNK_ATTEMPTS) throw e
            delay(RETRY_DELAY_MS * failures)
            runCatching { Api.get<UploadSession>("/panel/uploads/${session.id}") }.getOrNull()?.let { offset = it.received; report(offset) }
        }
    }
    session.id
}

private fun readRange(context: Context, uri: Uri, start: Long, len: Int): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
    input.use {
        var skipped = 0L
        while (skipped < start) {
            val s = it.skip(start - skipped)
            if (s <= 0) { if (it.read() < 0) break; skipped++ } else skipped += s
        }
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val r = it.read(buf, read, len - read)
            if (r < 0) break
            read += r
        }
        return if (read == len) buf else buf.copyOf(read)
    }
}

suspend fun abortUpload(uploadId: Int) { runCatching { Api.delete<Unit>("/panel/uploads/$uploadId") } }
