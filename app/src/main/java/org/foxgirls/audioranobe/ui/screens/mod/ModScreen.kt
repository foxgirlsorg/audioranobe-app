package org.foxgirls.audioranobe.ui.screens.mod

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.foxgirls.audioranobe.ui.components.SiteWebView
import org.foxgirls.audioranobe.ui.screens.me.RequireAuth

/** The site's /mod panel in a WebView. */
@Composable
fun ModScreen(path: String) {
    if (RequireAuth()) return
    SiteWebView(path, { it == "/mod" || it.startsWith("/mod/") }, Modifier.fillMaxSize().statusBarsPadding())
}
