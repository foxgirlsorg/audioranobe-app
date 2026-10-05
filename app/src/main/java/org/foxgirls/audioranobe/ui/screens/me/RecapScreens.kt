package org.foxgirls.audioranobe.ui.screens.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.foxgirls.audioranobe.ui.components.SiteWebView

private fun isRecap(path: String) = path == "/me/recap" || path.startsWith("/me/recap/")

/** The site's own recap pages, so the cards and their exports match the web exactly. */
@Composable
fun RecapScreen() {
    if (RequireAuth()) return
    Column(Modifier.fillMaxSize()) {
        MeHeader("Личное", "Итоги", "месяца")
        SiteWebView("/me/recap", ::isRecap, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
fun RecapYearScreen(year: Int) {
    if (RequireAuth()) return
    Column(Modifier.fillMaxSize()) {
        MeHeader("Личное", "Итоги", year.toString())
        SiteWebView("/me/recap/$year", ::isRecap, Modifier.fillMaxWidth().weight(1f))
    }
}
