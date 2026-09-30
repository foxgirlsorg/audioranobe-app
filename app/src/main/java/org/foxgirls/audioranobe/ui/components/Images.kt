package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.Transformation
import android.graphics.Bitmap
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.Badge
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar
import java.nio.ByteBuffer

/** Remote image with a neutral placeholder. */
@Composable
fun ArImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector? = null,
    blurred: Boolean = false,
    contentDescription: String? = null,
    shape: Shape? = null,
    backdrop: Boolean = false,
) {
    var m = modifier
    if (shape != null) m = m.clip(shape)
    Box(m.background(Ar.fill04), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            if (fallbackIcon != null) Icon(fallbackIcon, null, tint = Color.White.copy(alpha = 0.16f), modifier = Modifier.size(28.dp))
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(org.foxgirls.audioranobe.offline.OfflineStore.resolveImage(url)?.let { if (it.startsWith("/")) java.io.File(it) else it }).crossfade(true)
                    .apply { if (blurred || backdrop) transformations(SoftBlur) }.build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Heavy blur baked into the bitmap (Modifier.blur is a no-op before Android 12): shrink to 48px wide, then three box-blur passes. */
private object SoftBlur : Transformation {
    override val cacheKey = "softblur-48"

    override suspend fun transform(input: Bitmap, size: coil.size.Size): Bitmap {
        val w = 48
        val h = (input.height * w / input.width.coerceAtLeast(1)).coerceIn(1, 256)
        val small = Bitmap.createScaledBitmap(input, w, h, true)
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        repeat(3) { boxPass(px, w, h, 3, horizontal = true); boxPass(px, w, h, 3, horizontal = false) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val line = IntArray(len)
        for (l in 0 until lines) {
            for (i in 0 until len) line[i] = px[if (horizontal) l * w + i else i * w + l]
            for (i in 0 until len) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in (i - r).coerceAtLeast(0)..(i + r).coerceAtMost(len - 1)) {
                    val c = line[k]
                    a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                px[if (horizontal) l * w + i else i * w + l] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
}

/** Green presence dot with a background ring (components/PresenceDot). */
@Composable
fun PresenceDot(status: String, modifier: Modifier = Modifier, size: Dp = 9.dp, ring: Boolean = true, showOffline: Boolean = false) {
    if (status == "offline" && !showOffline) return
    val color = if (status == "online") Ar.ok else Ar.textMuted
    Box(modifier.size(size).background(color, CircleShape).then(if (ring) Modifier.border(2.dp, Ar.bg, CircleShape) else Modifier))
}

/** components/UserAvatar: image or initials, with an optional presence dot. */
@Composable
fun UserAvatar(
    username: String?,
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    presence: String? = null,
    onClick: (() -> Unit)? = null,
    thumbUrl: String? = null,
) {
    val fontSize = maxOf(9f, size.value * 0.34f).sp
    Box(modifier.size(size)) {
        var m = Modifier.size(size).clip(CircleShape).background(Ar.surfaceRaised).border(1.dp, Ar.border, CircleShape)
        if (onClick != null) m = m.clickable(onClick = onClick)
        Box(m, contentAlignment = Alignment.Center) {
            val url = thumbUrl ?: avatarUrl
            when {
                username == null -> Icon(Lucide.User, "Удалённый пользователь", tint = Ar.textMuted, modifier = Modifier.size(size / 2))
                !url.isNullOrBlank() -> AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                    contentDescription = username, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                else -> Text(Fmt.initials(username), color = Ar.accentHover, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
            }
        }
        if (presence != null && presence != "offline") {
            PresenceDot(presence, Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = 1.dp), size = maxOf(7f, size.value * 0.26f).dp)
        }
    }
}

/** A badge SVG shipped inline by the API, decoded by Coil's SVG decoder. */
@Composable
fun BadgeIcon(badge: Badge, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val model = remember(badge.svg) { ByteBuffer.wrap(badge.svg.toByteArray(Charsets.UTF_8)) }
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(model).build(),
        contentDescription = badge.name,
        modifier = modifier.size(size),
    )
}

/** components/UserBadges: banned hammer + badge SVGs. */
@Composable
fun UserBadgesRow(badges: List<Badge>, isBanned: Boolean = false, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    if (badges.isEmpty() && !isBanned) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (isBanned) Icon(Lucide.Hammer, "Заблокирован", tint = Ar.danger, modifier = Modifier.size(size))
        for (b in badges) BadgeIcon(b, size)
    }
}

/** Narrator avatar: image or first letter. */
@Composable
fun NarratorAvatar(name: String, url: String?, size: Dp, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    Box(modifier.size(size).clip(shape).background(Ar.accentSoft).border(1.dp, Ar.border, shape), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1).uppercase(), color = Ar.accent, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
