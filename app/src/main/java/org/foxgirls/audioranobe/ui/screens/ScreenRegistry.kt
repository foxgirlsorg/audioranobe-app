package org.foxgirls.audioranobe.ui.screens

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.auth.ForgotScreen
import org.foxgirls.audioranobe.ui.screens.auth.LoginScreen
import org.foxgirls.audioranobe.ui.screens.auth.OAuthScreen
import org.foxgirls.audioranobe.ui.screens.auth.RegisterScreen
import org.foxgirls.audioranobe.ui.screens.auth.ResetScreen
import org.foxgirls.audioranobe.ui.screens.auth.SetupScreen
import org.foxgirls.audioranobe.ui.screens.auth.VerifyScreen
import org.foxgirls.audioranobe.ui.screens.content.AuthorScreen
import org.foxgirls.audioranobe.ui.screens.content.CollectionScreen
import org.foxgirls.audioranobe.ui.screens.content.CollectionsScreen
import org.foxgirls.audioranobe.ui.screens.content.DmcaScreen
import org.foxgirls.audioranobe.ui.screens.content.DonateScreen
import org.foxgirls.audioranobe.ui.screens.content.LegalScreen
import org.foxgirls.audioranobe.ui.screens.content.NarratorScreen
import org.foxgirls.audioranobe.ui.screens.content.NewsItemScreen
import org.foxgirls.audioranobe.ui.screens.content.NewsScreen
import org.foxgirls.audioranobe.ui.screens.content.PostScreen
import org.foxgirls.audioranobe.ui.screens.editing.AuthorEditScreen
import org.foxgirls.audioranobe.ui.screens.editing.NarratorEditScreen
import org.foxgirls.audioranobe.ui.screens.editing.TitleEditScreen
import org.foxgirls.audioranobe.ui.screens.me.ChatScreen
import org.foxgirls.audioranobe.ui.screens.me.FriendsScreen
import org.foxgirls.audioranobe.ui.screens.me.HistoryScreen
import org.foxgirls.audioranobe.ui.screens.me.NotificationsScreen
import org.foxgirls.audioranobe.ui.screens.me.RecapScreen
import org.foxgirls.audioranobe.ui.screens.me.RecapYearScreen
import org.foxgirls.audioranobe.ui.screens.me.RequestsScreen
import org.foxgirls.audioranobe.ui.screens.me.SettingsScreen
import org.foxgirls.audioranobe.ui.screens.me.UserScreen
import org.foxgirls.audioranobe.ui.screens.mod.ModScreen

/** Registers every route beyond the four core screens (see AppShell). */
object ScreenRegistry {
    private fun str(name: String) = navArgument(name) { type = NavType.StringType; nullable = true }

    fun register(b: NavGraphBuilder) = with(b) {
        composable(Routes.COLLECTIONS) { CollectionsScreen() }
        composable(Routes.OFFLINE) { org.foxgirls.audioranobe.ui.screens.offline.OfflineScreen() }
        composable(Routes.OTHER) { org.foxgirls.audioranobe.ui.screens.content.OtherScreen() }
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
        composable(Routes.MOD, listOf(str("path"))) { e -> ModScreen(e.arguments?.getString("path") ?: "/mod") }
    }
}
