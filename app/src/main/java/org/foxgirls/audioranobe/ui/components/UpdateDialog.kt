package org.foxgirls.audioranobe.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.AppVersion
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.core.UpdateDownload
import org.foxgirls.audioranobe.core.Updater
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar

/**
 * The OTA popup. Mounted once at the app root; shows itself whenever [Updater.available] holds a
 * release the user has not dismissed, walks through download → install.
 */
@Composable
fun UpdateDialog() {
    val release by Updater.available.collectAsStateWithLifecycle()
    val dismissed by Updater.dismissed.collectAsStateWithLifecycle()
    val download by Updater.download.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val r = release ?: return
    if (dismissed) return
    val busy = download is UpdateDownload.Running

    ArModal(open = true, onClose = { if (!busy) Updater.dismiss() }, title = "Доступно обновление") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, color = Ar.white, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("v${r.version}", color = Ar.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            listOfNotNull(
                "Установлена ${AppVersion.VERSION}",
                r.apkBytes.takeIf { it > 0 }?.let { Fmt.bytes(it) },
                r.publishedAt.takeIf { it.isNotBlank() }?.let { Fmt.date(it) },
            ).joinToString(" · "),
            color = Ar.textMuted, fontSize = 12.sp,
        )
        if (r.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp)) { ArMarkdown(r.notes, compact = true) }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(download, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "update-state") { d ->
            Column(Modifier.fillMaxWidth()) {
                when (d) {
                    is UpdateDownload.Running -> {
                        Text("Скачиваем… ${(d.fraction * 100).toInt()}%", color = Ar.textSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        ProgressTrack(d.fraction, Modifier.fillMaxWidth(), height = 6.dp)
                        Spacer(Modifier.height(12.dp))
                        ArButton("Отменить", { Updater.cancelDownload() }, kind = ButtonKind.Ghost, fullWidth = true)
                    }
                    is UpdateDownload.Ready -> {
                        Text("Файл скачан. Android попросит разрешить установку из этого приложения, если оно ещё не разрешено.", color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        ArButton("Установить", { Updater.install(context, d.file) }, kind = ButtonKind.Primary, icon = Lucide.Download, fullWidth = true)
                        Spacer(Modifier.height(8.dp))
                        ArButton("Позже", { Updater.dismiss() }, kind = ButtonKind.Ghost, fullWidth = true)
                    }
                    else -> {
                        if (d is UpdateDownload.Failed) {
                            Text(d.message, color = Ar.danger, fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                        }
                        ArButton(if (d is UpdateDownload.Failed) "Повторить" else "Скачать и установить", { Updater.startDownload(r) }, kind = ButtonKind.Primary, icon = Lucide.Download, fullWidth = true)
                        Spacer(Modifier.height(8.dp))
                        ArButton("Позже", { Updater.dismiss() }, kind = ButtonKind.Ghost, fullWidth = true)
                        Spacer(Modifier.height(4.dp))
                        if (r.pageUrl.isNotEmpty()) AltLinkRow("Открыть на GitHub") { Links.external(context, r.pageUrl) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AltLinkRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        ArButton(text, onClick, kind = ButtonKind.Ghost, small = true, icon = Lucide.ExternalLink)
    }
}
