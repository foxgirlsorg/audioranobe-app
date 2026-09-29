package org.foxgirls.audioranobe.ui.components.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.Illustration
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ImageViewer
import org.foxgirls.audioranobe.ui.theme.Ar

data class VolumeCover(val id: Int, val url: String, val label: String)

fun chapterLine(ill: Illustration, volumeLabel: String = "Том"): String? {
    val ch = ill.chapter ?: return null
    return "$volumeLabel ${ch.volume_number} · ${Fmt.chapterLabel(ch.number, ch.number_end, ch.name)}"
}

/** components/Illustrations/IllustrationGallery: title-wide art first, then one group per chapter. */
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
    val gap = 8.dp

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val full = maxWidth
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (volumeCovers.isNotEmpty()) {
                val cols = maxOf(3, ((full + gap) / (120.dp + gap)).toInt())
                val cell = (full - gap * (cols - 1)) / cols
                Column {
                    Text(volumeCoversHeading, color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        volumeCovers.withIndex().chunked(cols).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { (i, v) ->
                                    Column(Modifier.width(cell)) {
                                        ArImage(v.url, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clickable { viewer = i }, shape = RoundedCornerShape(8.dp))
                                        Text(v.label, color = Ar.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
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
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for ((row, h) in justifiedRows(list.map { it.width.toFloat() / maxOf(1, it.height) }, full, gap, 150.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (i in row) {
                                    val ill = list[i]
                                    val ratio = ill.width.toFloat() / maxOf(1, ill.height)
                                    Column(Modifier.width(h * ratio)) {
                                        ArImage(ill.thumb_url, Modifier.fillMaxWidth().height(h).clickable { viewer = base + i }, blurred = ill.blurred, shape = RoundedCornerShape(8.dp))
                                        if (ill.caption.isNotBlank()) Text(ill.caption, color = Ar.textMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    ImageViewer(viewer != null, allUrls, viewer ?: 0, allCaptions) { viewer = null }
}

/** Greedy justified layout: fills rows to [width] at about [target] height; the last row keeps [target]. */
private fun justifiedRows(ratios: List<Float>, width: Dp, gap: Dp, target: Dp): List<Pair<List<Int>, Dp>> {
    val out = mutableListOf<Pair<List<Int>, Dp>>()
    var row = mutableListOf<Int>()
    var sum = 0f
    for ((i, r) in ratios.withIndex()) {
        row += i
        sum += r
        val fit = (width - gap * (row.size - 1)) / sum
        if (fit <= target) { out += row to fit; row = mutableListOf(); sum = 0f }
    }
    if (row.isNotEmpty()) out += row to minOf(target, (width - gap * (row.size - 1)) / sum)
    return out
}
