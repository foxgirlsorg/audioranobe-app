package org.foxgirls.audioranobe.ui.screens.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.AppVersion
import org.foxgirls.audioranobe.core.Support
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.components.SectionTitle
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.pagePadding
import org.foxgirls.audioranobe.ui.theme.Ar

/** The site's footer links, gathered on one screen behind the user menu's «Другое». */
@Composable
fun OtherScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) { IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text) }
        Eyebrow("AudioRanobe")
        SectionTitle("Другое", null, Modifier.padding(top = 4.dp, bottom = 14.dp), size = 24)

        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
            Eyebrow("Проект", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
            MenuRow(Lucide.Heart, "Поддержать проект", { nav.go(Routes.DONATE) }, tint = Ar.accent)
            MenuRow(Lucide.Library, "Коллекции", { nav.go(Routes.COLLECTIONS) })
            MenuRow(Lucide.Newspaper, "Новости", { nav.go(Routes.NEWS) })
        }
        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
            Eyebrow("Документы", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
            MenuRow(Lucide.ScrollText, "Правила", { nav.go(Routes.legal("rules")) })
            MenuRow(Lucide.FileText, "Условия использования", { nav.go(Routes.legal("terms")) })
            MenuRow(Lucide.Lock, "Политика конфиденциальности", { nav.go(Routes.legal("privacy")) })
            MenuRow(Lucide.Shield, "DMCA", { nav.go(Routes.DMCA) })
        }
        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
            Eyebrow("Связь", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
            MenuRow(Lucide.Mail, Support.EMAIL, { Links.external(context, "mailto:${Support.EMAIL}") })
            MenuRow(Lucide.MessageCircle, "Бот поддержки в Telegram", { Links.external(context, Support.BOT) })
            MenuRow(Lucide.Radio, "Канал в Telegram", { Links.external(context, Support.CHANNEL) })
            MenuRow(Lucide.Globe, "foxgirls.org", { Links.external(context, "https://foxgirls.org") })
        }
        Spacer(Modifier.height(8.dp))
        Text("AudioRanobe для Android · ${AppVersion.VERSION} «${AppVersion.NAME}»", color = Ar.textMuted, fontSize = 12.sp)
        Text("© 2026 foxgirls.org", color = Ar.textMuted, fontSize = 12.sp)
    }
}
