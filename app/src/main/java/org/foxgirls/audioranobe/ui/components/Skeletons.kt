package org.foxgirls.audioranobe.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.shimmer

/** Shared shimmer for every [Skeleton] (compose-placeholder). */
internal val SkeletonHighlight = PlaceholderHighlight.shimmer(highlightColor = Color.White.copy(alpha = 0.08f))

/** Placeholder with the shape of [TitleCardC]: cover, two-line name, author. */
@Composable
fun TitleCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier) {
        Skeleton(Modifier.fillMaxWidth().aspectRatio(2f / 3f), RoundedCornerShape(10.dp))
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Skeleton(Modifier.fillMaxWidth().height(14.dp))
            Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp))
        }
        Skeleton(Modifier.padding(top = 4.dp).fillMaxWidth(0.6f).height(12.dp))
    }
}

/** Placeholder for [CardGrid] with the same column count and gaps. Non-lazy, for use inside scrolling columns. */
@Composable
fun CardGridSkeleton(count: Int = 6, modifier: Modifier = Modifier) {
    val cols = gridColumns(LocalConfiguration.current.screenWidthDp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        for (row in (0 until count).chunked(cols)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (i in row) TitleCardSkeleton(Modifier.weight(1f))
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Placeholder for a horizontal [TitleRail]: cards at the rail's card width, clipped at the screen edge. */
@Composable
fun RailSkeleton(count: Int = 5, modifier: Modifier = Modifier, cardWidth: Dp = 138.dp) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp).clipToBounds(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) { TitleCardSkeleton(Modifier.width(cardWidth)) }
    }
}

/** Placeholder for [Section]'s eyebrow and title lines. */
@Composable
fun SectionHeaderSkeleton(modifier: Modifier = Modifier, eyebrowWidth: Dp = 120.dp, titleWidth: Dp = 180.dp) {
    Column(modifier.fillMaxWidth()) {
        Skeleton(Modifier.width(eyebrowWidth).height(11.dp))
        Skeleton(Modifier.padding(top = 6.dp, bottom = 14.dp).width(titleWidth).height(22.dp))
    }
}

/** Placeholder for one [Section] block: header, then a rail of cards. */
@Composable
fun SectionRailSkeleton(modifier: Modifier = Modifier, eyebrowWidth: Dp = 120.dp, titleWidth: Dp = 180.dp, count: Int = 5) {
    Column(modifier.fillMaxWidth()) {
        SectionHeaderSkeleton(Modifier.padding(start = 16.dp), eyebrowWidth, titleWidth)
        RailSkeleton(count)
    }
}

/** Placeholder for [BannerCarousel]: the 3:1 banner block. */
@Composable
fun BannerSkeleton(modifier: Modifier = Modifier) {
    Skeleton(modifier.fillMaxWidth().aspectRatio(3f), RoundedCornerShape(16.dp))
}

/** Placeholder matching the home "continue listening" card ([GlassPanel] 300dp wide). */
@Composable
fun ContinueCardSkeleton(modifier: Modifier = Modifier) {
    GlassPanel(modifier.width(300.dp), padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(64.dp, 88.dp), RoundedCornerShape(8.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Skeleton(Modifier.fillMaxWidth(0.8f).height(14.dp))
                Skeleton(Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(12.dp))
                Skeleton(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp), RoundedCornerShape(2.dp))
                Spacer(Modifier.weight(1f))
                Skeleton(Modifier.width(120.dp).height(11.dp))
            }
        }
    }
}

/** Placeholder for the home catalog controls row: label, sort select, direction and finished toggles. */
@Composable
fun CatalogControlsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Skeleton(Modifier.width(110.dp).height(10.dp))
        Row(Modifier.padding(top = 5.dp, bottom = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.weight(1f).height(40.dp), RoundedCornerShape(9.dp))
            Skeleton(Modifier.size(40.dp), RoundedCornerShape(9.dp))
            Skeleton(Modifier.width(120.dp).height(40.dp), RoundedCornerShape(9.dp))
        }
    }
}

/** Placeholder for a row with an avatar and two lines of text (users, friends, chat). Matches [UserAvatar] at 40dp. */
@Composable
fun AvatarRowSkeleton(modifier: Modifier = Modifier, avatar: Dp = 40.dp) {
    Row(modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Skeleton(Modifier.size(avatar), CircleShape)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.5f).height(13.dp))
            Skeleton(Modifier.fillMaxWidth(0.3f).height(11.dp))
        }
    }
}

/** Placeholder for a cover-left row (chapters, news, library entries). */
@Composable
fun CardRowSkeleton(modifier: Modifier = Modifier, coverWidth: Dp = 64.dp, coverHeight: Dp = 92.dp) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Skeleton(Modifier.size(coverWidth, coverHeight), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.8f).height(14.dp))
            Skeleton(Modifier.fillMaxWidth(0.5f).height(11.dp))
            Skeleton(Modifier.fillMaxWidth(0.3f).height(11.dp))
        }
    }
}

/** Placeholder for a paragraph of text. */
@Composable
fun TextSkeleton(modifier: Modifier = Modifier, lines: Int = 3) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(lines) { i -> Skeleton(Modifier.fillMaxWidth(if (i == lines - 1) 0.6f else 1f).height(12.dp)) }
    }
}

/**
 * Fills the available height with [row] stacked at [rowHeight] and [gap], so a full-screen placeholder
 * reaches the bottom edge on any screen size. Needs bounded height from its parent.
 */
@Composable
fun FillRowsSkeleton(rowHeight: Dp, gap: Dp = 12.dp, modifier: Modifier = Modifier, row: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val count = ((maxHeight + gap) / (rowHeight + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) { repeat(count) { row() } }
    }
}

/** Fade used when skeleton items are swapped for real ones (see [skeletonFade]). */
const val SkeletonFadeMillis = 220

/** Fade spec for `animateItem`, so skeleton rows fade out and real rows fade in instead of snapping. */
fun skeletonFade() = tween<Float>(SkeletonFadeMillis)

/** Placeholder matching the logged-out hero block on home. */
@Composable
fun HeroSkeleton(modifier: Modifier = Modifier) {
    Skeleton(modifier.fillMaxWidth().height(230.dp), RoundedCornerShape(18.dp))
}

/** Placeholder for the whole home page (top bar, rail, banner, catalog controls, grid), filling the screen. Used by the startup gate. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val cols = gridColumns(LocalConfiguration.current.screenWidthDp)
    Column(modifier.fillMaxSize().clipToBounds()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.width(120.dp).height(18.dp))
            Spacer(Modifier.weight(1f))
            Skeleton(Modifier.size(38.dp), CircleShape)
        }
        SectionRailSkeleton(Modifier.padding(top = 22.dp), eyebrowWidth = 90.dp, titleWidth = 150.dp)
        BannerSkeleton(Modifier.padding(horizontal = 16.dp, vertical = 22.dp))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SectionHeaderSkeleton(eyebrowWidth = 90.dp, titleWidth = 180.dp)
            CatalogControlsSkeleton()
        }
        Box(Modifier.padding(horizontal = 16.dp).weight(1f)) { CardGridSkeleton(catalogSkeletonCount(cols)) }
    }
}

/**
 * Enough placeholder cards to reach the bottom of the screen: whole rows at [cols] columns,
 * with the card height estimated from the width (2:3 cover plus name and author lines).
 */
@Composable
fun catalogSkeletonCount(cols: Int = gridColumns(LocalConfiguration.current.screenWidthDp)): Int {
    val config = LocalConfiguration.current
    val cardWidth = (config.screenWidthDp - 32 - 14 * (cols - 1)) / cols
    val cardHeight = cardWidth * 1.5f + 48f
    val rows = (config.screenHeightDp / (cardHeight + 18f)).toInt() + 1
    return rows * cols
}

/** Lazy-grid placeholder cards, for screens whose cards live in a LazyVerticalGrid. Keys are strings so they never clash with real item ids. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyGridScope.titleCardSkeletons(count: Int = 6) {
    gridItems((0 until count).toList(), key = { "skeleton$it" }) { _ ->
        TitleCardSkeleton(Modifier.animateItem(fadeInSpec = skeletonFade(), fadeOutSpec = skeletonFade()))
    }
}

/** Lazy-list placeholder rows for list screens. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.avatarRowSkeletons(count: Int = 6) {
    items((0 until count).toList(), key = { "skeleton$it" }) { _ ->
        AvatarRowSkeleton(Modifier.animateItem(fadeInSpec = skeletonFade(), fadeOutSpec = skeletonFade()))
    }
}
