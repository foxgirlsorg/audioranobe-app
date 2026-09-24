package com.audioranobe.app.ui.screens

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.screens.auth.ForgotScreen
import com.audioranobe.app.ui.screens.auth.LoginScreen
import com.audioranobe.app.ui.screens.auth.OAuthScreen
import com.audioranobe.app.ui.screens.auth.RegisterScreen
import com.audioranobe.app.ui.screens.auth.ResetScreen
import com.audioranobe.app.ui.screens.auth.SetupScreen
import com.audioranobe.app.ui.screens.auth.VerifyScreen
import com.audioranobe.app.ui.screens.content.AuthorScreen
import com.audioranobe.app.ui.screens.content.CollectionScreen
import com.audioranobe.app.ui.screens.content.CollectionsScreen
import com.audioranobe.app.ui.screens.content.DmcaScreen
import com.audioranobe.app.ui.screens.content.DonateScreen
import com.audioranobe.app.ui.screens.content.LegalScreen
import com.audioranobe.app.ui.screens.content.NarratorScreen
import com.audioranobe.app.ui.screens.content.NewsItemScreen
import com.audioranobe.app.ui.screens.content.NewsScreen
import com.audioranobe.app.ui.screens.content.PostScreen
import com.audioranobe.app.ui.screens.editing.AuthorEditScreen
import com.audioranobe.app.ui.screens.editing.NarratorEditScreen
import com.audioranobe.app.ui.screens.editing.TitleEditScreen
import com.audioranobe.app.ui.screens.me.ChatScreen
import com.audioranobe.app.ui.screens.me.FriendsScreen
import com.audioranobe.app.ui.screens.me.HistoryScreen
import com.audioranobe.app.ui.screens.me.NotificationsScreen
import com.audioranobe.app.ui.screens.me.RecapScreen
import com.audioranobe.app.ui.screens.me.RecapYearScreen
import com.audioranobe.app.ui.screens.me.RequestsScreen
import com.audioranobe.app.ui.screens.me.SettingsScreen
import com.audioranobe.app.ui.screens.me.UserScreen
import com.audioranobe.app.ui.screens.mod.ModScreen

/** Registers every route beyond the four core screens (see AppShell). */
object ScreenRegistry {
    private fun str(name: String) = navArgument(name) { type = NavType.StringType; nullable = true }

    fun register(b: NavGraphBuilder) = with(b) {
        composable(Routes.COLLECTIONS) { CollectionsScreen() }
        composable(Routes.NEWS) { NewsScreen() }
        composable(Routes.DONATE) { DonateScreen() }
        composable(Routes.DMCA) { DmcaScreen() }
        composable(Routes.TITLE_EDIT, listOf(navArgument("slug") { type = NavType.StringType }, str("tab"))) { e -> TitleEditScreen(e.arguments?.getString("slug") ?: "", e.arguments?.getString("tab")) }
        composable(Routes.NARRATOR, listOf(navArgument("slug") { type = NavType.StringType }, str("tab"))) { e -> NarratorScreen(e.arguments?.getString("slug") ?: "", e.arguments?.getString("tab")) }
        composable(Routes.NARRATOR_EDIT, listOf(navArgument("slug") { type = NavType.StringType })) { e -> NarratorEditScreen(e.arguments?.getString("slug") ?: "") }
        composable(Routes.AUTHOR, listOf(navArgument("id") { type = NavType.StringType })) { e -> AuthorScreen(e.arguments?.getString("id") ?: "") }
        composable(Routes.AUTHOR_EDIT, listOf(navArgument("id") { type = NavType.IntType })) { e -> AuthorEditScreen(e.arguments?.getInt("id") ?: 0) }
        composable(Routes.USER, listOf(navArgument("id") { type = NavType.StringType }, str("tab"))) { e -> UserScreen(e.arguments?.getString("id") ?: "", e.arguments?.getString("tab")) }
        composable(Routes.COLLECTION, listOf(navArgument("id") { type = NavType.IntType })) { e -> CollectionScreen(e.arguments?.getInt("id") ?: 0) }
        composable(Routes.NEWS_ITEM, listOf(navArgument("slug") { type = NavType.StringType })) { e -> NewsItemScreen(e.arguments?.getString("slug") ?: "") }
        composable(Routes.POST, listOf(navArgument("id") { type = NavType.IntType })) { e -> PostScreen(e.arguments?.getInt("id") ?: 0) }

        composable(Routes.LOGIN) { LoginScreen() }
        composable(Routes.REGISTER) { RegisterScreen() }
        composable(Routes.FORGOT) { ForgotScreen() }
        composable(Routes.RESET, listOf(str("token"))) { e -> ResetScreen(e.arguments?.getString("token") ?: "") }
        composable(Routes.VERIFY, listOf(str("token"))) { e -> VerifyScreen(e.arguments?.getString("token") ?: "") }
        composable(Routes.SETUP) { SetupScreen() }
        composable(Routes.OAUTH, listOf(navArgument("provider") { type = NavType.StringType }, str("mode"))) { e -> OAuthScreen(e.arguments?.getString("provider") ?: "", e.arguments?.getString("mode") ?: "login") }

        composable(Routes.ME_CHAT, listOf(str("user"))) { e -> ChatScreen(e.arguments?.getString("user")?.toIntOrNull()) }
        composable(Routes.ME_FRIENDS) { FriendsScreen() }
        composable(Routes.ME_HISTORY) { HistoryScreen() }
        composable(Routes.ME_NOTIFICATIONS) { NotificationsScreen() }
        composable(Routes.ME_REQUESTS) { RequestsScreen() }
        composable(Routes.ME_SETTINGS, listOf(str("tab"))) { e -> SettingsScreen(e.arguments?.getString("tab")) }
        composable(Routes.ME_RECAP) { RecapScreen() }
        composable(Routes.ME_RECAP_YEAR, listOf(navArgument("year") { type = NavType.IntType })) { e -> RecapYearScreen(e.arguments?.getInt("year") ?: 0) }

        composable(Routes.LEGAL, listOf(navArgument("doc") { type = NavType.StringType })) { e -> LegalScreen(e.arguments?.getString("doc") ?: "rules") }
        composable(Routes.MOD, listOf(navArgument("page") { type = NavType.StringType }, str("arg"))) { e -> ModScreen(e.arguments?.getString("page") ?: "dashboard", e.arguments?.getString("arg")) }
    }
}
