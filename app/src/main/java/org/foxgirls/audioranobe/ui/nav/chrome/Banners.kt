package org.foxgirls.audioranobe.ui.nav.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Support
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Me
import org.foxgirls.audioranobe.data.Recap
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.mod.ModNav
import org.foxgirls.audioranobe.ui.theme.Ar
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun BannedBanner() {
    val user by LocalAuth.current.user.collectAsStateWithLifecycle()
    val u = user ?: return
    if (!u.is_banned) return
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().background(Ar.bg.copy(alpha = 0.95f)).statusBarsPadding().border(1.dp, Ar.accent.copy(alpha = 0.4f)).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.ShieldBan, null, tint = Ar.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Ваш аккаунт заблокирован${u.ban_reason?.let { ": $it" } ?: ""}. Некоторые функции недоступны.",
            color = Ar.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, modifier = Modifier.weight(1f),
        )
        Text("Поддержка", color = Ar.accent, fontSize = 12.sp, textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { Links.external(nav.context, Support.URL) })
    }
}

@Composable
fun UnverifiedEmailBanner() {
    val user by LocalAuth.current.user.collectAsStateWithLifecycle()
    val u = user ?: return
    val config by Stores.config.config.collectAsStateWithLifecycle()
    LaunchedEffect(u.id) { Stores.config.ensure() }
    val mailEnabled = config?.email_verification ?: false
    if (u.email_verified || u.is_banned || !mailEnabled) return
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().background(Ar.amber.copy(alpha = 0.1f)).statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.MailWarning, null, tint = Ar.amber, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text("Почта не подтверждена — вы не сможете восстановить пароль в случае его потери.", color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
        Text("Подтвердить", color = Ar.amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { nav.go(Routes.settings("security")) })
    }
}

/** components/CookiesBanner */
@Composable
fun CookiesBanner(modifier: Modifier = Modifier) {
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val loading by auth.loading.collectAsStateWithLifecycle()
    var localAccepted by remember { mutableStateOf(Stores.prefs.cookiesAccepted) }
    val scope = rememberCoroutineScope()
    if (loading) return
    val accepted = user?.accepted_cookies ?: localAccepted
    if (accepted) return
    val nav = LocalNav.current
    GlassPanel(modifier.padding(horizontal = 12.dp).widthIn(max = 520.dp), background = Ar.surfaceStrong, borderColor = Ar.borderStrong) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Cookie, null, tint = Ar.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Мы используем cookie-файлы, чтобы приложение работало корректно. Продолжая пользоваться AudioRanobe, вы соглашаетесь с политикой конфиденциальности.", color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                Text("Политика конфиденциальности", color = Ar.accent, fontSize = 12.sp, modifier = Modifier.clickable { nav.go(Routes.legal("privacy")) }.padding(top = 2.dp))
            }
            Spacer(Modifier.width(10.dp))
            ArButton("Понятно", {
                if (user != null) scope.launch { runCatching { Api.post<Me>("/me/cookies"); auth.refresh() } }
                else { Stores.prefs.cookiesAccepted = true; localAccepted = true }
            }, kind = ButtonKind.Primary, small = true)
        }
    }
}

@Serializable
private data class RecapAlertRes(val recap: Recap? = null)

/** components/ModAlert + RecapAlert (the corner alert stack). */
@Composable
fun CornerAlerts(modifier: Modifier = Modifier) {
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val loading by auth.loading.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var recap by remember { mutableStateOf<Recap?>(null) }
    var modItems by remember { mutableStateOf<List<Triple<String, String, Int>>?>(null) }

    LaunchedEffect(user?.id, loading) {
        if (loading || user == null) { recap = null; modItems = null; return@LaunchedEffect }
        recap = runCatching { Api.get<RecapAlertRes>("/me/recap/alert").recap }.getOrNull()
        if (auth.can("mod.panel") && (System.currentTimeMillis() / 1000 - Stores.prefs.modAlertHiddenAt) >= 2 * 3600) {
            val counts = runCatching { Api.get<JsonObject>("/mod/dashboard") }.getOrNull()
            if (counts != null) {
                val list = ModNav.countable().filter { t -> t.perm == null || auth.can(t.perm) }
                    .map { t -> Triple(t.page, t.label, counts[t.countKey!!]?.jsonPrimitive?.intOrNull ?: 0) }
                    .filter { it.third > 0 }
                if (list.isNotEmpty()) modItems = list
            }
        }
    }

    Column(modifier.widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        modItems?.let { items ->
            GlassPanel(background = Ar.surfaceStrong, borderColor = Ar.borderStrong) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Bell, null, tint = Ar.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ждёт вашего внимания", color = Ar.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconBtn(Lucide.X, "Скрыть", { Stores.prefs.modAlertHiddenAt = System.currentTimeMillis() / 1000; modItems = null }, size = 28.dp, iconSize = 14.dp)
                }
                for ((page, label, count) in items) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { modItems = null; nav.go(Routes.mod(page)) }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Ar.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(if (count > 99) "99+" else count.toString(), color = Ar.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        recap?.let {
            GlassPanel(background = Ar.surfaceStrong, borderColor = Ar.borderStrong) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Sparkles, null, tint = Ar.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Итоги месяца готовы", color = Ar.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconBtn(Lucide.X, "Скрыть", { recap = null }, size = 28.dp, iconSize = 14.dp)
                }
                Spacer(Modifier.height(6.dp))
                ArButton("Смотреть итоги", { recap = null; nav.go(Routes.ME_RECAP) }, kind = ButtonKind.Primary, small = true, fullWidth = true)
            }
        }
    }
}
