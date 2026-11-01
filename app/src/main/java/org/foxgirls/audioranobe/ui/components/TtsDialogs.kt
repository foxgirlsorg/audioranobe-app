package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.tts.TtsModels
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar

/**
 * Asks before the one-time download of the on-device narrator (~160 MB) and shows its progress.
 * [onReady] runs once the models are on the device, so the caller can carry on with what the user asked for.
 */
@Composable
fun TtsDownloadDialog(open: Boolean, onClose: () -> Unit, onReady: () -> Unit) {
    val installed by TtsModels.installed.collectAsStateWithLifecycle()
    val progress by TtsModels.progress.collectAsStateWithLifecycle()
    val error by TtsModels.error.collectAsStateWithLifecycle()
    LaunchedEffect(open, installed) { if (open && installed) { onReady(); onClose() } }
    val busy = progress != null
    ArModal(open && !installed, { if (!busy) onClose() }, "Озвучка на устройстве") {
        Text(
            "Эта книга читается голосом ИИ прямо на телефоне. Для этого нужно один раз скачать модель озвучки (около 160 МБ) — после этого она работает без интернета.",
            color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp,
        )
        error?.let { Text(it, color = Ar.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(14.dp))
        if (busy) {
            LinearProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.fillMaxWidth(), color = Ar.accent, trackColor = Ar.borderStrong)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ArButton("Отменить", { TtsModels.cancelDownload() }, kind = ButtonKind.Ghost) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ArButton("Не сейчас", onClose, kind = ButtonKind.Ghost)
                Spacer(Modifier.width(8.dp))
                ArButton("Скачать", { TtsModels.download() }, kind = ButtonKind.Primary, icon = Lucide.Download)
            }
        }
    }
}
