package org.foxgirls.audioranobe.ui.nav.chrome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.ui.LocalShell
import org.foxgirls.audioranobe.ui.components.CountBubble
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.UserAvatar
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar

private data class Tab(val key: String, val route: String, val label: String, val icon: ImageVector, val match: (String) -> Boolean)

private val TABS = listOf(
    Tab("home", Routes.HOME, "Главная", Lucide.House) { it == Routes.HOME },
    Tab("catalog", Routes.catalog(), "Каталог", Lucide.LibraryBig) { it.startsWith("catalog") },
    Tab("notifications", Routes.ME_NOTIFICATIONS, "Уведомления", Lucide.Bell) { it.startsWith("me/notifications") },
    Tab("messages", Routes.chat(), "Сообщения", Lucide.MessageCircle) { it.startsWith("me/chat") },
    Tab("offline", Routes.OFFLINE, "Загрузки", Lucide.Download) { it == Routes.OFFLINE },
)

/**
 * components/Dock: the installed-app bottom bar. It folds into a compact strip
 * while the page scrolls down and grows back on the first scroll up; the mini
 * player floats above it over the page (see MainShell).
 */
@Composable
fun Dock(route: String) {
    val nav = LocalNav.current
    val shell = LocalShell.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val badges by Stores.badges.badges.collectAsStateWithLifecycle()
    val dlQueue by org.foxgirls.audioranobe.offline.OfflineStore.queue.collectAsStateWithLifecycle()
    val compact = shell.dockCompact
    val height by animateDpAsState(if (compact) Ar.dockHeightCompact else Ar.dockHeight, spring(stiffness = Spring.StiffnessMediumLow), label = "dockHeight")
    val iconScale by animateFloatAsState(if (compact) 0.82f else 1f, tween(220), label = "dockIconScale")

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(Ar.bg.copy(alpha = 0.96f))) {
            HairlineDivider()
            Row(Modifier.fillMaxWidth().height(height).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (t in TABS) {
                    val active = t.match(route)
                    val count = when (t.key) { "messages" -> badges.messages; "notifications" -> badges.notifications; "offline" -> dlQueue.size; else -> 0 }
                    DockTab(active, count, iconScale, Modifier.weight(1f), onClick = {
                        shell.dockCompact = false
                        if (t.key == "home" || t.key == "catalog" || user != null) nav.tab(t.route) else nav.go(Routes.LOGIN)
                    }) {
                        Icon(t.icon, t.label, tint = it, modifier = Modifier.size(26.dp))
                    }
                }
                DockTab(false, badges.friend_requests, iconScale, Modifier.weight(1f), onClick = { shell.menuOpen = true; Stores.myNarrators.ensureLoaded() }) { tint ->
                    if (user != null) UserAvatar(user!!.username, user!!.avatar_url, 27.dp, thumbUrl = user!!.avatar_thumb_url)
                    else Icon(Lucide.Menu, "Меню", tint = tint, modifier = Modifier.size(26.dp))
                }
            }
            Box(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun DockTab(active: Boolean, count: Int, baseScale: Float, modifier: Modifier, onClick: () -> Unit, content: @Composable (tint: Color) -> Unit) {
    val tint by animateColorAsState(if (active) Ar.accent else Ar.textMuted, tween(200), label = "dockTint")
    val scale by animateFloatAsState(if (active) 1.14f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "dockScale")
    val indicator by animateDpAsState(if (active) 28.dp else 0.dp, tween(220), label = "dockIndicator")
    Box(modifier.fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.TopCenter).width(indicator).height(3.dp).background(Ar.accent, RoundedCornerShape(0.dp, 0.dp, 3.dp, 3.dp)))
        Box(Modifier.scale(scale * baseScale)) { content(tint) }
        if (count > 0) CountBubble(count, Modifier.align(Alignment.Center).offset(x = 19.dp, y = (-14).dp))
    }
}
