package org.foxgirls.audioranobe.ui

import org.foxgirls.audioranobe.ui.components.UpdateDialog
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.ui.nav.AppNav
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.NotFoundScreen
import org.foxgirls.audioranobe.ui.screens.auth.ForgotScreen
import org.foxgirls.audioranobe.ui.screens.auth.LoginScreen
import org.foxgirls.audioranobe.ui.screens.auth.OAuthScreen
import org.foxgirls.audioranobe.ui.screens.auth.RegisterScreen
import org.foxgirls.audioranobe.ui.screens.auth.ResetScreen
import org.foxgirls.audioranobe.ui.screens.auth.SetupScreen
import org.foxgirls.audioranobe.ui.screens.auth.VerifyScreen
import org.foxgirls.audioranobe.ui.screens.content.LegalScreen
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.ToastHost

/** Routes the signed-out shell can show; everything else waits for the sign-in. */
fun isAuthRoute(route: String): Boolean = route.startsWith("auth/") || route.startsWith("legal/")

/**
 * The app is gated behind an account: while nobody is signed in only the auth
 * screens (and the legal documents they link to) exist. Once [AuthStore.user]
 * is set — and the profile setup is done — [AppShell] swaps this shell for the
 * real one, so the auth screens never need to navigate "home" themselves.
 */
@Composable
fun AuthShell(pendingRoute: String?, pendingSeq: Int, onRouteConsumed: () -> Unit) {
    val controller = rememberNavController()
    val context = LocalContext.current
    val nav = remember(controller) { AppNav(controller, context, home = Routes.LOGIN) }

    LaunchedEffect(pendingSeq) {
        if (pendingRoute != null && isAuthRoute(pendingRoute)) { nav.go(pendingRoute); onRouteConsumed() }
    }

    CompositionLocalProvider(LocalNav provides nav, LocalAuth provides Stores.auth, LocalBottomInset provides 0.dp) {
        UpdateDialog()
        Box(Modifier.fillMaxSize().background(Ar.bg)) {
            NavHost(
                controller, startDestination = Routes.LOGIN,
                enterTransition = { slideInHorizontally { it / 6 } + fadeIn() }, exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() }, popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() },
            ) {
                val str = { name: String -> navArgument(name) { type = NavType.StringType; nullable = true } }
                composable(Routes.LOGIN) { LoginScreen() }
                composable(Routes.REGISTER) { RegisterScreen() }
                composable(Routes.FORGOT) { ForgotScreen() }
                composable(Routes.RESET, listOf(str("token"))) { e -> ResetScreen(e.arguments?.getString("token") ?: "") }
                composable(Routes.VERIFY, listOf(str("token"))) { e -> VerifyScreen(e.arguments?.getString("token") ?: "") }
                composable(Routes.SETUP) { SetupScreen() }
                composable(Routes.OAUTH, listOf(navArgument("provider") { type = NavType.StringType }, str("mode"))) { e -> OAuthScreen(e.arguments?.getString("provider") ?: "", e.arguments?.getString("mode") ?: "login") }
                composable(Routes.LEGAL, listOf(navArgument("doc") { type = NavType.StringType })) { e -> LegalScreen(e.arguments?.getString("doc") ?: "rules") }
                composable(Routes.NOT_FOUND) { NotFoundScreen() }
            }
            ToastHost(Modifier.align(Alignment.TopCenter))
        }
    }
}
