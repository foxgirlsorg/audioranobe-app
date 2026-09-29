package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.CollectionCard
import org.foxgirls.audioranobe.data.NarratorCard
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.data.TitleCard
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar

/** components/TitleCardC */
@Composable
fun TitleCardC(title: TitleCard, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val nav = LocalNav.current
    val isAdmin = Stores.auth.can("titles.edit")
    val rating = Fmt.rating(title.avg_rating)
    Column(modifier.clickable { onClick?.invoke() ?: nav.go(Routes.title(title.slug)) }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).border(1.dp, Ar.border, RoundedCornerShape(10.dp)),
        ) {
            ArImage(title.thumb, Modifier.matchParentSize(), fallbackIcon = Lucide.Headphones, blurred = title.is_restricted)
            Row(
                Modifier.align(Alignment.TopStart).padding(8.dp).clip(CircleShape).background(Color(0xB80A0A0C)).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Star, null, tint = Ar.accent, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(rating, color = Ar.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
            }
            TopRightBadges {
                if (title.is_deleted && isAdmin) Box(Modifier.size(22.dp).background(Color(0xD9B42828), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Trash2, "Удалён", tint = Ar.white, modifier = Modifier.size(12.dp))
                }
                if (title.is_ai) AiBadge()
                if (title.is_nsfw) NsfwBadge()
            }
        }
        Text(title.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (title.author != null) Text(title.author.name, color = Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Horizontal rail of title cards (components/ScrollRail + TitleCardC). */
@Composable
fun TitleRail(titles: List<TitleCard>, modifier: Modifier = Modifier, cardWidth: Dp = 138.dp, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)) {
    if (titles.isEmpty()) {
        Text("Здесь пока пусто.", color = Ar.textMuted, fontSize = 13.sp, modifier = modifier.padding(contentPadding))
        return
    }
    val state = rememberLazyListState()
    LazyRow(modifier.fillMaxWidth().edgeFade(state), state = state, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(titles, key = { it.id }) { t -> TitleCardC(t, Modifier.width(cardWidth)) }
    }
}

/** Responsive card grid: N columns by width (components/CardGrid). Non-lazy, for use inside scrolling columns. */
@Composable
fun CardGrid(titles: List<TitleCard>, modifier: Modifier = Modifier) {
    val cols = gridColumns(LocalConfiguration.current.screenWidthDp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        for (row in titles.chunked(cols)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (t in row) TitleCardC(t, Modifier.weight(1f))
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** components/CardGrid: two columns up to 768dp like the site's phone layout, auto-fill of [minCard] above. */
fun gridColumns(screenWidthDp: Int, minCard: Int = 152, gap: Int = 20, gutter: Int = 32): Int =
    if (screenWidthDp <= 768) 2 else maxOf(2, (screenWidthDp - gutter + gap) / (minCard + gap))

/** Catalog "people" card for a narrator. */
@Composable
fun NarratorPersonCard(n: NarratorCard, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    GlassPanel(modifier.clickable { nav.go(Routes.narrator(n.slug)) }, padding = PaddingValues(14.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NarratorAvatar(n.name, n.avatar_thumb_url ?: n.avatar_url, 64.dp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (n.is_verified) { Spacer(Modifier.width(4.dp)); VerifiedBadge(size = 13.dp) }
            }
            Text("${Fmt.count(n.titles_count)} тайтлов", color = Ar.textMuted, fontSize = 12.sp)
        }
    }
}

/** components/CollectionCardC */
@Composable
fun CollectionCardC(c: CollectionCard, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    GlassPanel(modifier, padding = PaddingValues(0.dp), onClick = { nav.go(Routes.collection(c.id)) }) {
        Row(Modifier.fillMaxWidth().height(96.dp)) {
            for (i in 0 until 3) {
                ArImage(c.cover_urls.getOrNull(i), Modifier.weight(1f).height(96.dp), fallbackIcon = if (i == 0) Lucide.Library else null)
            }
        }
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, color = Ar.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!c.is_public) Icon(Lucide.Lock, "Приватная", tint = Ar.textMuted, modifier = Modifier.size(13.dp))
            }
            if (c.description.isNotBlank()) Text(Fmt.plainSummary(c.description, 120), color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(c.user.username, c.user.avatar_url, 18.dp, thumbUrl = c.user.avatar_thumb_url)
                Spacer(Modifier.width(6.dp))
                Text(c.user.shownName, color = Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Lucide.ListMusic, null, tint = Ar.textMuted, modifier = Modifier.size(12.dp))
                Text(" ${c.items_count}", color = Ar.textMuted, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Lucide.Heart, null, tint = Ar.textMuted, modifier = Modifier.size(12.dp))
                Text(" ${c.likes_count}", color = Ar.textMuted, fontSize = 12.sp)
            }
        }
    }
}
