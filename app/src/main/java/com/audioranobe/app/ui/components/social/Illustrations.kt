package com.audioranobe.app.ui.components.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioranobe.app.core.Fmt
import com.audioranobe.app.data.Illustration
import com.audioranobe.app.ui.components.ArImage
import com.audioranobe.app.ui.components.ImageViewer
import com.audioranobe.app.ui.theme.Ar

data class VolumeCover(val id: Int, val url: String, val label: String)

fun chapterLine(ill: Illustration, volumeLabel: String = "Том"): String? {
    val ch = ill.chapter ?: return null
    return "$volumeLabel ${ch.volume_number} · ${Fmt.chapterLabel(ch.number, ch.number_end, ch.name)}"
}

/** components/Illustrations/IllustrationGallery: title-wide art first, then one group per chapter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IllustrationGallery(items: List<Illustration>, volumeLabel: String = "Том", volumeCovers: List<VolumeCover> = emptyList(), volumeCoversHeading: String = "Тома", modifier: Modifier = Modifier) {
    val general = items.filter { it.chapter_id == null }
    val byChapter = items.filter { it.chapter_id != null }.groupBy { it.chapter_id!! }.values
        .sortedWith(compareBy({ it[0].chapter?.volume_number ?: 0 }, { it[0].chapter?.number ?: 0.0 }))
    val groups = buildList {
        if (general.isNotEmpty()) add(null to general)
        for (g in byChapter) add(chapterLine(g[0], volumeLabel) to g)
    }
    val showHeadings = groups.any { it.first != null }
    val allUrls = volumeCovers.map { it.url } + groups.flatMap { it.second.map { i -> i.url } }
    val allCaptions = volumeCovers.map { it.label } + groups.flatMap { g -> g.second.map { i -> listOfNotNull(i.caption.takeIf { c -> c.isNotBlank() }, chapterLine(i, volumeLabel)).joinToString(" · ") } }
    var viewer by remember { mutableStateOf<Int?>(null) }
    val width = LocalConfiguration.current.screenWidthDp.dp - 32.dp
    val rowH = 150.dp

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (volumeCovers.isNotEmpty()) {
            Column {
                Text(volumeCoversHeading, color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    volumeCovers.forEachIndexed { i, v ->
                        Column(Modifier.width(rowH * 2 / 3)) {
                            ArImage(v.url, Modifier.width(rowH * 2 / 3).height(rowH).clickable { viewer = i }, shape = RoundedCornerShape(8.dp))
                            Text(v.label, color = Ar.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        var offset = volumeCovers.size
        for ((heading, list) in groups) {
            val base = offset
            offset += list.size
            Column {
                if (showHeadings) Text(heading ?: "К тайтлу", color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEachIndexed { i, ill ->
                        val ratio = ill.width.toFloat() / maxOf(1, ill.height)
                        val w = (rowH * ratio).coerceAtMost(width)
                        Column(Modifier.width(w)) {
                            ArImage(ill.thumb_url, Modifier.width(w).aspectRatio(ratio).clickable { viewer = base + i }, blurred = ill.blurred, shape = RoundedCornerShape(8.dp))
                            if (ill.caption.isNotBlank()) Text(ill.caption, color = Ar.textMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    ImageViewer(viewer != null, allUrls, viewer ?: 0, allCaptions) { viewer = null }
}
