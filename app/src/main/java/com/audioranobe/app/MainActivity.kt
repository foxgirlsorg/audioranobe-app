package com.audioranobe.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.audioranobe.app.player.PlayerController
import com.audioranobe.app.ui.AppShell
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.theme.AudioRanobeTheme

class MainActivity : ComponentActivity() {
    /** Route requested by a deep link / notification tap, consumed by the shell. */
    private var pendingRoute by mutableStateOf<String?>(null)
    private var pendingSeq by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            AudioRanobeTheme {
                AppShell(pendingRoute = pendingRoute, pendingSeq = pendingSeq, onRouteConsumed = { pendingRoute = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("open_player", false)) {
            PlayerController.setFull(true)
            intent.removeExtra("open_player")
        }
        val data = intent.data ?: return
        val path = (data.path ?: "/") + (data.query?.let { "?$it" } ?: "") + (data.fragment?.let { "#$it" } ?: "")
        val route = Routes.fromPath(path) ?: return
        pendingRoute = route
        pendingSeq++
    }
}
