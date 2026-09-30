package org.foxgirls.audioranobe.ui

import androidx.activity.compose.BackHandler
import org.foxgirls.audioranobe.push.PushBootstrap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.UpdateDialog
import org.foxgirls.audioranobe.ui.nav.AppNav
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.nav.chrome.AccountMenuSheet
import org.foxgirls.audioranobe.ui.nav.chrome.BannedBanner
import org.foxgirls.audioranobe.ui.nav.chrome.CookiesBanner
import org.foxgirls.audioranobe.ui.nav.chrome.CornerAlerts
import org.foxgirls.audioranobe.ui.nav.chrome.Dock
import org.foxgirls.audioranobe.ui.nav.chrome.SearchSheet
import org.foxgirls.audioranobe.ui.nav.chrome.UnverifiedEmailBanner
import org.foxgirls.audioranobe.ui.player.FullPlayer
import org.foxgirls.audioranobe.ui.player.MiniPlayer
import org.foxgirls.audioranobe.ui.player.MiniProgressLine
import org.foxgirls.audioranobe.ui.screens.ChapterScreen
import org.foxgirls.audioranobe.ui.screens.HomeScreen
import org.foxgirls.audioranobe.ui.screens.NotFoundScreen
import org.foxgirls.audioranobe.ui.screens.ScreenRegistry
import org.foxgirls.audioranobe.ui.screens.catalog.CatalogScreen
import org.foxgirls.audioranobe.ui.screens.title.TitleScreen
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.ToastHost

/** Bottom inset every scrolling screen must leave for the dock (+ mini player). */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

/** Shell-level UI toggles that screens can trigger (search / account menu / compact dock). */
class ShellState {
    var searchOpen by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    /** True while the current list scrolls down: the dock shrinks and the mini player folds. */
    var dockCompact by mutableStateOf(false)
}

val LocalShell = staticCompositionLocalOf<ShellState> { error("no shell") }

/**
 * Root of the UI. The app works only with an account: while the session is
 * unknown a splash spinner shows, while nobody is signed in (or the profile is
 * not set up) only the auth screens exist, and the real shell appears after.
 */
@Composable
fun AppShell(pendingRoute: String?, pendingSeq: Int, onRouteConsumed: () -> Unit) {
    val user by Stores.auth.user.collectAsStateWithLifecycle()
    val loading by Stores.auth.loading.collectAsStateWithLifecycle()
    val state = when {
        user == null && loading -> 0
        user == null || user!!.needs_setup -> 1
        else -> 2
    }
    LaunchedEffect(state) { if (state == 1) PlayerController.stop() }
    AnimatedContent(state, transitionSpec = { (fadeIn(tween(380)) + scaleIn(tween(380), initialScale = 0.98f)) togetherWith fadeOut(tween(220)) }, label = "shell") { s ->
        when (s) {
            0 -> Box(Modifier.fillMaxSize().background(Ar.bg)) { CenterSpinner(Modifier.fillMaxSize(), 0.dp) }
            1 -> AuthShell(pendingRoute, pendingSeq, onRouteConsumed)
            else -> MainShell(pendingRoute, pendingSeq, onRouteConsumed)
        }
    }
}

@Composable
private fun MainShell(pendingRoute: String?, pendingSeq: Int, onRouteConsumed: () -> Unit) {
    val controller = rememberNavController()
    val context = LocalContext.current
    val nav = remember(controller) { AppNav(controller, context) }
    val shell = remember { ShellState() }
    val current by PlayerController.current.collectAsStateWithLifecycle()
    val full by PlayerController.full.collectAsStateWithLifecycle()
    val backStack by controller.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val hideChrome = route.startsWith("me/chat") && backStack?.arguments?.getString("user") != null
    val dockH = if (hideChrome) 0.dp else if (shell.dockCompact) Ar.dockHeightCompact else Ar.dockHeight
    val miniH = if (current == null || hideChrome) 0.dp else if (shell.dockCompact) Ar.miniPlayerHeightCompact else Ar.miniPlayerHeight
    val bottomInset = dockH + miniH

    LaunchedEffect(pendingSeq) {
        if (pendingRoute != null) { nav.go(pendingRoute); onRouteConsumed() }
    }
    // A new screen always starts with the full dock.
    LaunchedEffect(route) { shell.dockCompact = false }

    BackHandler(enabled = full) { PlayerController.setFull(false) }

    val me by Stores.auth.user.collectAsStateWithLifecycle()
    me?.let { PushBootstrap(it.id) }

    CompositionLocalProvider(LocalNav provides nav, LocalAuth provides Stores.auth, LocalShell provides shell, LocalBottomInset provides miniH) {
        UpdateDialog()
        Box(Modifier.fillMaxSize().background(Ar.bg)) {
            Column(Modifier.fillMaxSize()) {
                BannedBanner()
                UnverifiedEmailBanner()
                Box(Modifier.weight(1f)) {
                    NavHost(
                        controller, startDestination = Routes.HOME,
                        enterTransition = { slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260)) },
                        exitTransition = { fadeOut(tween(200)) },
                        popEnterTransition = { fadeIn(tween(220)) },
                        popExitTransition = { slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(220)) },
                    ) {
                        composable(Routes.HOME, enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(200)) }) { HomeScreen() }
                        composable(
                            Routes.CATALOG,
                            arguments = listOf("q", "tab", "genre", "sort", "order", "finished", "author").map { navArgument(it) { type = NavType.StringType; nullable = true } },
                            enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(200)) },
                        ) { e -> CatalogScreen(e.arguments) }
                        composable(Routes.TITLE, arguments = listOf(navArgument("slug") { type = NavType.StringType }, navArgument("tab") { type = NavType.StringType; nullable = true })) { e ->
                            TitleScreen(e.arguments?.getString("slug") ?: "", e.arguments?.getString("tab"))
                        }
                        composable(Routes.CHAPTER, arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("t") { type = NavType.StringType; nullable = true })) { e ->
                            ChapterScreen(e.arguments?.getInt("id") ?: 0, e.arguments?.getString("t")?.toDoubleOrNull())
                        }
                        composable(Routes.NOT_FOUND) { NotFoundScreen() }
                        ScreenRegistry.register(this)
                    }
                    if (current != null && !hideChrome) FloatingMiniPlayer(shell.dockCompact, Modifier.align(Alignment.BottomCenter))
                }
                AnimatedVisibility(visible = !hideChrome, enter = slideInVertically(tween(280)) { it } + fadeIn(tween(280)), exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220))) {
                    Dock(route)
                }
            }
            CornerAlerts(Modifier.align(Alignment.BottomEnd).padding(bottom = bottomInset + 12.dp, end = 12.dp))
            CookiesBanner(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 8.dp))
            AnimatedVisibility(visible = full && current != null, enter = slideInVertically(tween(320)) { it } + fadeIn(tween(320)), exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(260))) {
                FullPlayer()
            }
            ToastHost(Modifier.align(Alignment.TopCenter))
            SearchSheet(shell.searchOpen) { shell.searchOpen = false }
            AccountMenuSheet(shell.menuOpen) { shell.menuOpen = false }
        }
    }
}

@Composable
private fun FloatingMiniPlayer(compact: Boolean, modifier: Modifier) {
    Box(modifier) {
        AnimatedVisibility(visible = !compact, enter = slideInVertically(tween(260)) { it }, exit = slideOutVertically(tween(220)) { it }) {
            MiniPlayer()
        }
        AnimatedVisibility(visible = compact, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
            MiniProgressLine()
        }
    }
}

/** Standard page padding for scrolling content: gutter + dock clearance. */
@Composable
fun pagePadding(top: Int = 12, extraBottom: Int = 24): PaddingValues =
    PaddingValues(start = 16.dp, end = 16.dp, top = top.dp, bottom = LocalBottomInset.current + extraBottom.dp)

@Composable
fun Modifier.pageBottomPadding(extra: Int = 24): Modifier = this.padding(bottom = LocalBottomInset.current + extra.dp)

@Composable
fun FullWidth(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth()) { content() }
