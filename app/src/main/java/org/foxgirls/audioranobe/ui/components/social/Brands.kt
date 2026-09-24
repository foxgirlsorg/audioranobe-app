package org.foxgirls.audioranobe.ui.components.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.theme.Ar
import java.net.URI

/** components/SocialLinks/brands: brand icons the site draws by hand, plus a host → icon map. */
object Brands {
    private fun filled(name: String, viewport: Float, d: String, vx: Float = 0f, vy: Float = 0f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, viewport, viewport).apply {
            addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }.build()

    private fun stroked(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }.build()

    val Patreon: ImageVector by lazy { filled("patreon", 24f, "M22.957 7.21c-.004-3.064-2.391-5.576-5.191-6.482-3.478-1.125-8.064-.962-11.384.604C2.357 3.231 1.093 7.391 1.046 11.54c-.039 3.411.302 12.396 5.369 12.46 3.765.047 4.326-4.804 6.068-7.141 1.24-1.662 2.836-2.132 4.801-2.618 3.376-.836 5.678-3.501 5.673-7.031Z") }
    val Telegram: ImageVector by lazy {
        ImageVector.Builder("telegram", 24.dp, 24.dp, 315f, 315f).apply {
            addPath(PathParser().parsePathString("M314.814,30.68l-53.458,257.136c-1.259,6.071-8.378,8.822-13.401,5.172l-72.975-52.981c-4.43-3.217-10.471-3.046-14.712,0.412l-40.46,32.981c-4.695,3.84-11.771,1.7-13.569-4.083l-28.094-90.351l-72.583-27.089c-7.373-2.762-7.436-13.171-0.084-16.003L303.36,20.959C309.675,18.517,316.19,24.049,314.814,30.68z M243.567,77.179l-141.854,87.367c-5.437,3.355-7.996,9.921-6.242,16.068l15.337,53.891c1.091,3.818,6.631,3.428,7.162-0.517l3.986-29.553c0.753-5.564,3.406-10.693,7.522-14.522l117.069-108.822C248.739,79.061,246.115,75.614,243.567,77.179z").toNodes(), fill = SolidColor(Color.Black))
        }.build()
    }
    val Vk: ImageVector by lazy { filled("vk", 24f, "M12 0C5.373 0 0 5.373 0 12s5.373 12 12 12 12-5.373 12-12S18.627 0 12 0zm5.87 17.29h-2.84c-.61-1.9-2.13-3.37-4.14-3.57v3.57h-.31c-5.47 0-8.59-3.75-8.72-9.99h2.74c.09 4.58 2.11 6.52 3.71 6.92V7.3h2.58v3.95c1.58-.17 3.24-1.97 3.8-3.95h2.58c-.43 2.44-2.23 4.24-3.51 4.98 1.28.6 3.33 2.17 4.11 5.01z") }
    val Boosty: ImageVector by lazy { filled("boosty", 24f, "M2.661 14.337 6.801 0h6.362L11.88 4.444l-.038.077-3.378 11.733h3.15c-1.321 3.289-2.35 5.867-3.086 7.733-5.816-.063-7.442-4.228-6.02-9.155M8.554 24l7.67-11.035h-3.25l2.83-7.073c4.852.508 7.137 4.33 5.791 8.952C20.16 19.81 14.344 24 8.68 24h-.127z") }
    val Youtube: ImageVector by lazy { stroked("youtube", "M2.5 17a24.12 24.12 0 0 1 0-10 2 2 0 0 1 1.4-1.4 49.56 49.56 0 0 1 16.2 0A2 2 0 0 1 21.5 7a24.12 24.12 0 0 1 0 10 2 2 0 0 1-1.4 1.4 49.55 49.55 0 0 1-16.2 0A2 2 0 0 1 2.5 17", "m10 15 5-3-5-3z") }
    val Twitter: ImageVector by lazy { stroked("twitter", "M22 4s-.7 2.1-2 3.4c1.6 10-9.4 17.3-18 11.6 2.2.1 4.4-.6 6-2C3 15.5.5 9.6 3 5c2.2 2.6 5.6 4.1 9 4-.9-4.2 4-6.6 7-3.8 1.1 0 3-1.2 3-1.2z") }
    val Instagram: ImageVector by lazy { stroked("instagram", "M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5z", "M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z", "M17.5 6.5h.01") }
    val Twitch: ImageVector by lazy { stroked("twitch", "M21 2H3v16h5v4l4-4h5l4-4V2zm-10 9V7m5 4V7") }

    fun hostOf(url: String): String = try { URI(url.trim()).host?.removePrefix("www.")?.lowercase() ?: "" } catch (_: Exception) { "" }

    data class Platform(val icon: ImageVector, val label: String)

    fun platformOf(host: String): Platform = when {
        host.contains("youtube") || host == "youtu.be" -> Platform(Youtube, "YouTube")
        host == "t.me" || host.contains("telegram") -> Platform(Telegram, "Telegram")
        host.contains("twitter") || host == "x.com" -> Platform(Twitter, "Twitter / X")
        Regex("(^|\\.)vk\\.(com|ru)$").containsMatchIn(host) -> Platform(Vk, "VK")
        host.contains("discord") -> Platform(Lucide.MessageCircle, "Discord")
        host.contains("boosty") -> Platform(Boosty, "Boosty")
        host.contains("patreon") -> Platform(Patreon, "Patreon")
        host.contains("tiktok") -> Platform(Lucide.Music2, "TikTok")
        host.contains("instagram") -> Platform(Instagram, "Instagram")
        host.contains("twitch") -> Platform(Twitch, "Twitch")
        else -> Platform(Lucide.Link, "Ссылка")
    }

    fun iconFor(url: String): ImageVector = platformOf(hostOf(url)).icon
}

/** components/SocialLinks */
@Composable
fun SocialLinks(urls: List<String>, modifier: Modifier = Modifier) {
    val list = urls.filter { it.isNotBlank() }
    if (list.isEmpty()) return
    val nav = LocalNav.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (u in list) {
            val p = Brands.platformOf(Brands.hostOf(u))
            IconBtn(p.icon, p.label, { Links.external(nav.context, u) }, size = 38.dp, iconSize = 18.dp, background = Ar.fill04, border = Ar.border, tint = Ar.textSecondary)
        }
    }
}

@Composable
fun BrandIcon(url: String, modifier: Modifier = Modifier) {
    Icon(Brands.iconFor(url), null, tint = Ar.textMuted, modifier = modifier.size(14.dp))
}
