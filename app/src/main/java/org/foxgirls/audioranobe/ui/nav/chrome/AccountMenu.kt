package org.foxgirls.audioranobe.ui.nav.chrome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.components.NarratorAvatar
import org.foxgirls.audioranobe.ui.components.UserBadgesRow
import org.foxgirls.audioranobe.ui.components.VerifiedBadge
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.editing.AddContentDialog
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class RandomTitle(val slug: String)

/** The NavBar account/guest menu, as a bottom sheet (opened from the dock's last tab). */
@Composable
fun AccountMenuSheet(open: Boolean, onClose: () -> Unit) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val badges by Stores.badges.badges.collectAsStateWithLifecycle()
    val myNarrators by Stores.myNarrators.narrators.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var addOpen by remember { mutableStateOf(false) }

    fun go(route: String) { onClose(); nav.go(route) }

    fun random() {
        scope.launch {
            try { go(Routes.title(Api.get<RandomTitle>("/titles/random").slug)) } catch (e: Exception) { toastError(e) }
        }
    }

    ArSheet(open, onClose) {
        val u = user
        if (u != null) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Eyebrow("вы вошли как")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.shownName, color = Ar.white, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    UserBadgesRow(u.badges, u.is_banned, 14.dp)
                }
                if (u.display_name.isNotBlank() && u.display_name != u.username) Text("@${u.username}", color = Ar.textMuted, fontSize = 12.sp)
            }
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            MenuRow(Lucide.User, "Профиль", { go(Routes.user(u.id)) })
            MenuRow(Lucide.Users, "Друзья", { go(Routes.ME_FRIENDS) }, count = badges.friend_requests)
            MenuRow(Lucide.History, "История", { go(Routes.ME_HISTORY) })
            MenuRow(Lucide.ClipboardList, "Мои заявки", { go(Routes.ME_REQUESTS) })
            if (u.isMod) MenuRow(Lucide.Shield, "Модерация", { go(Routes.mod()) })
            MenuRow(Lucide.Settings, "Настройки", { go(Routes.settings()) })
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            MenuRow(Lucide.Library, "Коллекции", { go(Routes.COLLECTIONS) })
            MenuRow(Lucide.Newspaper, "Новости", { go(Routes.NEWS) })
            MenuRow(Lucide.Dices, "Случайный тайтл", { random() })
            MenuRow(Lucide.Ellipsis, "Другое", { go(Routes.OTHER) })
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            MenuRow(Lucide.Plus, "Добавить", { addOpen = true })
            if (myNarrators.isNotEmpty()) {
                HairlineDivider(Modifier.padding(vertical = 6.dp))
                for (n in myNarrators) {
                    MenuRow(null, n.name, { go(Routes.narrator(n.slug)) }) {
                        if (n.is_verified) VerifiedBadge(size = 13.dp)
                    }
                }
            }
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            MenuRow(Lucide.LogOut, "Выйти", {
                onClose(); auth.logout(); toast("Вы вышли из аккаунта"); nav.go(Routes.HOME)
            })
        } else {
            MenuRow(Lucide.Library, "Коллекции", { go(Routes.COLLECTIONS) })
            MenuRow(Lucide.Newspaper, "Новости", { go(Routes.NEWS) })
            MenuRow(Lucide.Dices, "Случайный тайтл", { random() })
            MenuRow(Lucide.Ellipsis, "Другое", { go(Routes.OTHER) })
            HairlineDivider(Modifier.padding(vertical = 6.dp))
            MenuRow(Lucide.LogIn, "Войти", { go(Routes.LOGIN) })
            MenuRow(Lucide.UserPlus, "Регистрация", { go(Routes.REGISTER) })
        }
        Spacer(Modifier.height(4.dp))
    }
    AddContentDialog(addOpen) { addOpen = false; onClose() }
}

/** Small leading avatar for narrator rows in menus. */
@Composable
fun NarratorMenuAvatar(name: String, url: String?) = NarratorAvatar(name, url, 22.dp)
