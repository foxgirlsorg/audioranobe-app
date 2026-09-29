package org.foxgirls.audioranobe.ui.player

import org.foxgirls.audioranobe.ui.components.PlayPauseIcon
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

/**
 * The dock's mini player (components/Dock .player). A horizontal swipe skips
 * ±10 s (left = back, right = forward); tapping opens the full player.
 */
@Composable
fun MiniPlayer() {
    val current by PlayerController.current.collectAsStateWithLifecycle()
    val playing by PlayerController.playing.collectAsStateWithLifecycle()
    val position by PlayerController.position.collectAsStateWithLifecycle()
    val duration by PlayerController.duration.collectAsStateWithLifecycle()
    val cur = current ?: return
    val label = "Гл. ${Fmt.chapterNumber(cur.number, cur.number_end)}" + (if (cur.name.isNotBlank()) " — ${cur.name}" else "")
    val frac by animateFloatAsState((if (duration > 0) (position / duration).toFloat() else 0f).coerceIn(0f, 1f), tween(400), label = "miniProgress")

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Ar.surfaceSolid)) {
        Row(
            Modifier.fillMaxWidth().height(Ar.miniPlayerHeight - 8.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (abs(total) > 80f) PlayerController.skip(if (total > 0) 10.0 else -10.0) }) { _, d -> total += d }
                }
                .clickable { PlayerController.setFull(true) }.padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArImage(cur.coverUrl, Modifier.size(36.dp), fallbackIcon = Lucide.Music, shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(cur.title.name, color = Ar.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = Ar.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(Lucide.RotateCcw, "Назад на 10 секунд", { PlayerController.skip(-10.0) }, size = 36.dp, iconSize = 20.dp)
            Box(Modifier.size(36.dp).clip(CircleShape).background(Ar.accent).clickable { PlayerController.toggle() }, contentAlignment = Alignment.Center) {
                PlayPauseIcon(playing, Ar.accentOn, 18.dp, 1.dp)
            }
            IconBtn(Lucide.RotateCw, "Вперёд на 10 секунд", { PlayerController.skip(10.0) }, size = 36.dp, iconSize = 20.dp)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ar.fill08)) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(Ar.accent))
        }
    }
}

/** What stays of the mini player while it is tucked behind the folded dock: the progress line. */
@Composable
fun MiniProgressLine() {
    val position by PlayerController.position.collectAsStateWithLifecycle()
    val duration by PlayerController.duration.collectAsStateWithLifecycle()
    val frac by animateFloatAsState((if (duration > 0) (position / duration).toFloat() else 0f).coerceIn(0f, 1f), tween(400), label = "miniLineProgress")
    Box(Modifier.fillMaxWidth().height(Ar.miniPlayerHeightCompact).background(Ar.fill08)) {
        Box(Modifier.fillMaxWidth(frac).height(Ar.miniPlayerHeightCompact).background(Ar.accent))
    }
}
