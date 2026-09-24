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
import coil.compose.AsyncImage
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
                IconBtn(Lucide.Download, "Открыть оригинал", { Links.external(context, urls[pager.currentPage]) }, tint = Ar.white)
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
