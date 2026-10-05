package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.provider.MediaStore
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.await
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.theme.Ar
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

/** Full-screen pinch-zoom image viewer (react-photo-view replacement). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(open: Boolean, urls: List<String>, initial: Int = 0, captions: List<String?> = emptyList(), onClose: () -> Unit) {
    if (!open || urls.isEmpty()) return
    val context = LocalContext.current
    var pendingUrl by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val storagePermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        pendingUrl?.let { if (ok) downloadImage(context, it) else org.foxgirls.audioranobe.ui.toast.toastError("Нет доступа к памяти устройства") }
        pendingUrl = null
    }
    fun save(url: String) {
        val needsPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsPermission) { pendingUrl = url; storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) } else downloadImage(context, url)
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState(initialPage = initial.coerceIn(0, urls.size - 1), pageCount = { urls.size })
        LaunchedEffect(initial) { pager.scrollToPage(initial.coerceIn(0, urls.size - 1)) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                val zoom = rememberZoomState()
                AsyncImage(
                    model = urls[i], contentDescription = captions.getOrNull(i), contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().zoomable(zoom, onTap = { onClose() }),
                )
            }
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                IconBtn(Lucide.Download, "Скачать", { save(urls[pager.currentPage]) }, tint = Ar.white)
                IconBtn(Lucide.X, "Закрыть", onClose, tint = Ar.white)
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(12.dp)) {
                captions.getOrNull(pager.currentPage)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Ar.white, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                if (urls.size > 1) Text("${pager.currentPage + 1} / ${urls.size}", color = Ar.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Saves an image to Downloads/AudioRanobe through the system download manager (progress + "done" notification). */
private fun downloadImage(context: android.content.Context, url: String) {
    val uri = android.net.Uri.parse(url)
    if (uri.scheme != "http" && uri.scheme != "https") { Links.external(context, url); return }
    val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') } ?: "image-${System.currentTimeMillis()}.jpg"
    if (name.endsWith(".webp", ignoreCase = true)) {
        // Galleries and messengers handle WebP poorly, so it is re-encoded as JPEG.
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    Api.client.newCall(okhttp3.Request.Builder().url(url).build()).await().use { res ->
                        if (!res.isSuccessful) throw java.io.IOException("Сервер ответил ${res.code}")
                        android.graphics.BitmapFactory.decodeStream(res.body!!.byteStream()) ?: throw java.io.IOException("Не удалось прочитать изображение")
                    }
                }
                saveJpegToDownloads(context, name.dropLast(5)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                org.foxgirls.audioranobe.ui.toast.toast("Сохранено в «Загрузки»")
            } catch (e: Exception) {
                org.foxgirls.audioranobe.ui.toast.toastError(e)
            }
        }
        return
    }
    try {
        val request = android.app.DownloadManager.Request(uri)
            .setTitle(name)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "AudioRanobe/$name")
            .addRequestHeader("User-Agent", "AudioRanobe-Android")
        (context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
        org.foxgirls.audioranobe.ui.toast.toast("Изображение сохраняется в «Загрузки»")
    } catch (e: Exception) {
        org.foxgirls.audioranobe.ui.toast.toastError(e)
    }
}

/** Writes a JPEG into Downloads/AudioRanobe. */
suspend fun saveJpegToDownloads(context: android.content.Context, name: String, write: (java.io.OutputStream) -> Unit) = withContext(Dispatchers.IO) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION")
        val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "AudioRanobe").apply { mkdirs() }
        val file = java.io.File(dir, "$name.jpg")
        file.outputStream().use(write)
        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        return@withContext
    }
    val values = android.content.ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
        put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/AudioRanobe")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Не удалось сохранить")
    resolver.openOutputStream(uri)!!.use(write)
    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
}
