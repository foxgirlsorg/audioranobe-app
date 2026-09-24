package org.foxgirls.audioranobe.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar

@Composable
fun NotFoundScreen(text: String = "Такой страницы нет — или модераторы её так и не одобрили.") {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Eyebrow("потерялись в помехах")
        Row {
            Text("4", color = Ar.white, fontSize = 72.sp, fontWeight = FontWeight.Light)
            Text("0", color = Ar.accent, fontSize = 72.sp, fontWeight = FontWeight.Light)
            Text("4", color = Ar.white, fontSize = 72.sp, fontWeight = FontWeight.Light)
        }
        Text(text, color = Ar.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        ArButton("На главную", { nav.tab(Routes.HOME) }, kind = ButtonKind.Primary, icon = Lucide.House)
    }
}

/** Simple screen header with a back link and title. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    val nav = LocalNav.current
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            org.foxgirls.audioranobe.ui.components.BackLink(onClick = { nav.back() })
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = Ar.white, fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp, lineHeight = 30.sp)
        if (subtitle != null) Text(subtitle, color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
