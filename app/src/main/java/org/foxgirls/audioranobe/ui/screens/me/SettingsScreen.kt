package org.foxgirls.audioranobe.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.data.Genre
import org.foxgirls.audioranobe.data.Identity
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Me
import org.foxgirls.audioranobe.data.Paginated
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArModal
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.MarkdownEditor
import org.foxgirls.audioranobe.ui.components.UserAvatar
import org.foxgirls.audioranobe.ui.components.pickers.SocialsEditor
import org.foxgirls.audioranobe.ui.components.rememberImageCropper
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.auth.ProviderButtons
import org.foxgirls.audioranobe.ui.screens.auth.TotpSetupModal
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val USERNAME_RE = Regex("^[A-Za-z0-9_]{3,30}$")
private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

private data class PrefDef(val key: String, val label: String, val hint: String)

private val PREF_DEFS = listOf(
    PrefDef("new_chapter", "Новые главы", "Выходит новая глава тайтла из вашей библиотеки"),
    PrefDef("narration_ready", "Озвучка готова", "Заказанная вами озвучка готова к прослушиванию"),
    PrefDef("narrator_release", "Релизы чтецов", "Чтец, на которого вы подписаны, публикует что-то новое"),
    PrefDef("comment_reply", "Ответы на комментарии", "Кто-то отвечает на ваш комментарий или ответ"),
    PrefDef("friend_request", "Заявки в друзья", "Кто-то отправил вам заявку в друзья"),
    PrefDef("request_reviewed", "Решения по заявкам", "Модератор рассмотрел вашу заявку — одобрил или отклонил с причиной"),
    PrefDef("entity_modified", "Изменения объектов", "Модератор изменил чтеца или тайтл, к которому вы имеете отношение"),
    PrefDef("entity_deleted", "Удаление объектов", "Модератор удалил объект, к которому вы имеете отношение"),
)

@Serializable
private data class CommentSubTitle(val id: Int, val slug: String = "", val name: String = "", val comment_subscribed: Boolean = false)

@Serializable
private data class CommentSubs(val items: List<CommentSubTitle> = emptyList())

@Serializable
private data class IdentitiesRes(val identities: List<Identity> = emptyList())

@Composable
fun SettingsScreen(tab: String?) {
    if (RequireAuth()) return
    val bottom = LocalBottomInset.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { MeHeader("Аккаунт", "Мои", "настройки") }
        item { Column(Modifier.padding(horizontal = 16.dp)) { SettingsBody(scopeUserId = null, onSaved = {}) } }
    }
}

@Composable
private fun Panel(icon: ImageVector, title: String, hint: String?, danger: Boolean = false, content: @Composable () -> Unit) {
    GlassPanel(Modifier.padding(bottom = 12.dp), borderColor = if (danger) Ar.danger.copy(alpha = 0.35f) else Ar.border) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = if (danger) Ar.danger else Ar.accent, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (hint != null) Text(hint, color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * app/me/settings. With [scopeUserId] set (mod with users.full_edit) every /me call carries ?as=<id>
 * and the subject is that user instead of the signed-in moderator.
 */
@Composable
fun SettingsBody(scopeUserId: Int?, onSaved: (Me) -> Unit) {
    val auth = LocalAuth.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val scoped = scopeUserId != null
    val asParam: Map<String, Any?>? = if (scoped) mapOf("as" to scopeUserId) else null
    val viewer by auth.user.collectAsStateWithLifecycle()
    var me by remember { mutableStateOf<Me?>(null) }
    var seeded by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var socials by remember { mutableStateOf<List<String>>(emptyList()) }
    var savingProfile by remember { mutableStateOf(false) }
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var newPw2 by remember { mutableStateOf("") }
    var savingPw by remember { mutableStateOf(false) }
    var prefs by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var prefBusy by remember { mutableStateOf(false) }
    var commentTitles by remember { mutableStateOf<List<CommentSubTitle>>(emptyList()) }
    var sensitiveGenres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var identities by remember { mutableStateOf<List<Identity>>(emptyList()) }
    var toUnlink by remember { mutableStateOf<Identity?>(null) }
    var emailOpen by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var emailPw by remember { mutableStateOf("") }
    var savingEmail by remember { mutableStateOf(false) }
    var totpModal by remember { mutableStateOf(false) }
    var totpDisableOpen by remember { mutableStateOf(false) }
    var totpDisablePw by remember { mutableStateOf("") }
    var delOpen by remember { mutableStateOf(false) }
    var delPw by remember { mutableStateOf("") }
    var resending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val m = Api.get<Me>("/me", asParam)
        me = m
        if (scoped) onSaved(m) else auth.setUser(m)
    }

    LaunchedEffect(scopeUserId, viewer?.id) {
        try {
            val m = Api.get<Me>("/me", asParam)
            me = m
            if (!seeded) {
                seeded = true
                username = m.username; displayName = m.display_name; bio = m.bio; socials = m.socials
                identities = m.identities
                val np = m.notification_prefs
                if (np != null) prefs = mapOf(
                    "new_chapter" to np.new_chapter, "narration_ready" to np.narration_ready, "narrator_release" to np.narrator_release, "comment_reply" to np.comment_reply,
                    "friend_request" to np.friend_request, "request_reviewed" to np.request_reviewed, "entity_modified" to np.entity_modified, "entity_deleted" to np.entity_deleted,
                )
            }
            if (!scoped) commentTitles = runCatching { Api.get<CommentSubs>("/me/comment-subscriptions").items }.getOrDefault(emptyList())
            sensitiveGenres = runCatching { Api.get<Paginated<Genre>>("/genres", mapOf("per_page" to 200)).items.filter { it.is_sensitive } }.getOrDefault(emptyList())
        } catch (e: Exception) { toastError(e) }
    }

    val m = me
    if (m == null) { CenterSpinner(); return }
    val hasPassword = m.has_password
    val providerLabel = { id: String -> m.auth_providers?.firstOrNull { it.id == id }?.name ?: mapOf("google" to "Google", "discord" to "Discord", "telegram" to "Telegram")[id] ?: id }
    fun run(block: suspend () -> Unit) { scope.launch { try { block() } catch (e: Exception) { toastError(e) } } }

    val avatarPick = rememberImageCropper(1, 1, 1024, 1024, circle = true) { img -> uploading = "avatar"; run { try { Api.upload<Me>("/me/avatar" + (if (scoped) "?as=$scopeUserId" else "")) { addPart(img.part(filename = "avatar.webp")) }; refresh(); toast("Аватар обновлён") } finally { uploading = null } } }
    val coverPick = rememberImageCropper(3, 1, 2048, 2048) { img -> uploading = "cover"; run { try { Api.upload<Me>("/me/cover" + (if (scoped) "?as=$scopeUserId" else "")) { addPart(img.part(filename = "cover.webp")) }; refresh(); toast("Обложка обновлена") } finally { uploading = null } } }

    Column(Modifier.fillMaxWidth()) {
        Panel(Lucide.User, "Профиль", "Вы вошли как ${m.email ?: m.username}${if (m.email == null) " — почта не указана" else ""}") {
            ArTextField(displayName, { displayName = it }, label = "Отображаемое имя", placeholder = username.ifBlank { "Как вас показывать" }, maxLength = Limits.displayName, hint = "Любые символы, до 40 знаков. Если оставить пустым, будет показан логин.")
            Spacer(Modifier.height(10.dp))
            ArTextField(username, { username = it }, label = "Логин", maxLength = Limits.username, hint = "audioranobe.com/user/${username.ifBlank { "…" }}")
            Spacer(Modifier.height(10.dp))
            MarkdownEditor(bio, { bio = it }, label = "О себе", maxLength = Limits.bio, placeholder = "Расскажите что-нибудь…", media = "image")
            Spacer(Modifier.height(10.dp))
            SocialsEditor(socials, { socials = it }, label = "Ссылки на соцсети")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ArButton(if (savingProfile) "Сохраняем…" else "Сохранить профиль", {
                    if (savingProfile) return@ArButton
                    val name = username.trim()
                    if (!USERNAME_RE.matches(name)) { toastError("Имя пользователя: 3–30 символов, только латинские буквы, цифры и подчёркивание"); return@ArButton }
                    val cleaned = socials.map { it.trim() }.filter { it.isNotEmpty() }
                    if (cleaned.size > Limits.socialsCount) { toastError("Можно добавить не больше 10 ссылок"); return@ArButton }
                    for (u in cleaned) if (!Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(u) || u.length > Limits.socialUrl) { toastError("Неверная ссылка: ${u.take(60)} — только http(s), не длиннее ${Limits.socialUrl} символов"); return@ArButton }
                    savingProfile = true
                    run { try { Api.patch<Me>("/me", buildJsonObject { put("username", name); put("display_name", displayName.trim()); put("bio", bio.trim()); put("socials", JsonArray(cleaned.map { JsonPrimitive(it) })) }, asParam); socials = cleaned; refresh(); toast("Профиль сохранён") } finally { savingProfile = false } }
                }, kind = ButtonKind.Primary, busy = savingProfile)
            }
        }

        Panel(Lucide.ImagePlus, "Аватар и обложка", "Картиночки.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(m.username, m.avatar_url, 64.dp)
                Spacer(Modifier.width(12.dp))
                Column { Text("Аватар · 1:1", color = Ar.textSecondary, fontSize = 12.sp); Spacer(Modifier.height(6.dp)); ArButton(if (uploading == "avatar") "Загружаем…" else "Сменить аватар", { avatarPick.pick() }, icon = Lucide.ImagePlus, small = true, busy = uploading == "avatar", enabled = uploading == null) }
            }
            Spacer(Modifier.height(12.dp))
            ArImage(m.cover_url, Modifier.fillMaxWidth().height(90.dp), fallbackIcon = Lucide.ImagePlus, shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Обложка профиля · 3:1", color = Ar.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f)); ArButton(if (uploading == "cover") "Загружаем…" else "Сменить обложку", { coverPick.pick() }, icon = Lucide.ImagePlus, small = true, busy = uploading == "cover", enabled = uploading == null) }
        }

        Panel(Lucide.KeyRound, if (hasPassword) "Пароль" else "Задать пароль", if (hasPassword) "Минимум 8 символов. Выберите что-нибудь уникальное." else "Вы вошли через сторонний сервис, пароля у аккаунта нет. Задайте его, чтобы входить и по логину.") {
            if (!scoped && hasPassword) { ArTextField(oldPw, { oldPw = it }, label = "Текущий пароль", password = true, maxLength = Limits.password); Spacer(Modifier.height(10.dp)) }
            ArTextField(newPw, { newPw = it }, label = "Новый пароль", password = true, maxLength = Limits.password)
            Spacer(Modifier.height(10.dp))
            ArTextField(newPw2, { newPw2 = it }, label = "Повторите новый пароль", password = true, maxLength = Limits.password)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ArButton(if (savingPw) "Сохраняем…" else if (hasPassword) "Сменить пароль" else "Задать пароль", {
                    if (savingPw) return@ArButton
                    if (!scoped && hasPassword && oldPw.isEmpty()) { toastError("Введите текущий пароль"); return@ArButton }
                    if (newPw.length < 8) { toastError("Новый пароль должен быть не короче 8 символов"); return@ArButton }
                    if (newPw != newPw2) { toastError("Новые пароли не совпадают"); return@ArButton }
                    savingPw = true
                    run { try { Api.post<Unit>("/me/password", buildJsonObject { put("old_password", oldPw); put("new_password", newPw) }, asParam); oldPw = ""; newPw = ""; newPw2 = ""; toast(if (hasPassword) "Пароль изменён" else "Пароль установлен"); refresh() } finally { savingPw = false } }
                }, kind = ButtonKind.Primary, busy = savingPw)
            }
        }

        Panel(Lucide.ShieldCheck, "Двухфакторная аутентификация", if (m.totp_enabled) "Включена — при входе понадобится код из приложения-аутентификатора." else "Необязательно. Приложение вроде Google Authenticator или Aegis добавит код при входе.") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (m.totp_enabled) ArButton("Отключить", { totpDisableOpen = true }, kind = ButtonKind.Ghost)
                else if (scoped) Text("Включить может только сам пользователь.", color = Ar.textMuted, fontSize = 12.sp)
                else ArButton("Включить", { totpModal = true }, kind = ButtonKind.Ghost)
            }
        }
        TotpSetupModal(totpModal, { totpModal = false }) { u -> me = u; if (!scoped) auth.setUser(u) }

        Panel(Lucide.Link, "Способы входа", if (hasPassword) "Привяжите сервисы, чтобы входить в один клик." else "У аккаунта пока нет пароля — вход возможен только через привязанные сервисы. Задайте пароль выше, чтобы отвязать последний из них.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Почта", color = Ar.text, fontSize = 14.sp)
                    Text((m.email ?: "не указана") + (if (m.email != null && !m.email_verified) " — не подтверждена" else ""), color = Ar.textMuted, fontSize = 12.sp)
                }
                if (!scoped && m.email_verification == true && m.email != null && !m.email_verified) ArButton(if (resending) "Отправляем…" else "Письмо повторно", { resending = true; run { try { Api.post<Unit>("/me/verify-email/resend"); toast("Письмо отправлено — проверьте почту") } finally { resending = false } } }, kind = ButtonKind.Ghost, small = true, busy = resending)
                ArButton(if (m.email != null) "Изменить" else "Добавить", { newEmail = m.email ?: ""; emailPw = ""; emailOpen = true }, kind = ButtonKind.Ghost, small = true)
            }
            for (idn in identities) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(providerLabel(idn.provider), color = Ar.text, fontSize = 14.sp); Text(idn.display_name.ifBlank { idn.email ?: "привязан" }, color = Ar.textMuted, fontSize = 12.sp) }
                ArButton("Отвязать", { toUnlink = idn }, kind = ButtonKind.Ghost, small = true)
            }
            if (identities.isEmpty()) Text("Пока ничего не привязано.", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            if (!scoped) ProviderButtons("link", m.auth_providers, hide = identities.map { it.provider })
        }

        Panel(Lucide.Bell, "Уведомления", "Выберите, какие уведомления вы хотите получать.") {
            for (def in PREF_DEFS) ArToggle(prefs[def.key] ?: true, { v ->
                if (prefBusy) return@ArToggle
                prefBusy = true; prefs = prefs + (def.key to v)
                run { try { val u = Api.patch<Me>("/me/notification-prefs", buildJsonObject { put(def.key, v) }, asParam); me = u; toast("Настройки уведомлений сохранены") } catch (e: Exception) { prefs = prefs + (def.key to !v); throw e } finally { prefBusy = false } }
            }, def.label, def.hint, enabled = !prefBusy)
        }

        Panel(Lucide.MessageSquare, "Личные сообщения", "Кто может писать вам напрямую.") {
            ArToggle(m.dm_privacy == "friends", { v -> run { Api.patch<Me>("/me", buildJsonObject { put("dm_privacy", if (v) "friends" else "all") }, asParam); refresh(); toast("Настройки личных сообщений сохранены") } }, "Только друзья могут писать мне", "Писать вам первыми смогут только друзья.")
        }

        if (commentTitles.isNotEmpty()) Panel(Lucide.MessageSquare, "Комментарии к вашим тайтлам", "Уведомления о новых комментариях к тайтлам ваших чтецов.") {
            for (t in commentTitles) ArToggle(t.comment_subscribed, { v ->
                commentTitles = commentTitles.map { if (it.id == t.id) it.copy(comment_subscribed = v) else it }
                run { try { if (v) Api.put<Unit>("/titles/${t.id}/comment-subscription") else Api.delete<Unit>("/titles/${t.id}/comment-subscription"); toast(if (v) "Уведомления о комментариях включены" else "Уведомления о комментариях выключены") } catch (e: Exception) { commentTitles = commentTitles.map { if (it.id == t.id) it.copy(comment_subscribed = !v) else it }; throw e } }
            }, t.name, "Уведомлять о новых комментариях")
        }

        Panel(Lucide.EyeOff, "Контент", "Скрытые тайтлы не появляются в каталоге, поиске, на главной и в рекомендациях.") {
            val cp = m.content_prefs
            ArToggle(cp?.hide_nsfw ?: true, { v -> run { me = Api.patch<Me>("/me/content-prefs", buildJsonObject { put("hide_nsfw", v); put("hidden_genres", JsonArray((cp?.hidden_genres ?: emptyList()).map { JsonPrimitive(it) })) }, asParam); toast("Настройки контента сохранены") } }, "Скрывать 18+", "Тайтлы с меткой 18+ не будут показываться в списках", enabled = cp != null)
            ArToggle(m.blur_unlistened_illustrations, { v -> run { Api.patch<Me>("/me", buildJsonObject { put("blur_unlistened_illustrations", v) }, asParam); me = m.copy(blur_unlistened_illustrations = v); toast("Настройки контента сохранены") } }, "Скрывать иллюстрации непрослушанных глав", "Превью размывается, пока глава не прослушана хотя бы наполовину.")
            ArToggle(m.auto_add_to_library, { v -> run { Api.patch<Me>("/me", buildJsonObject { put("auto_add_to_library", v) }, asParam); me = m.copy(auto_add_to_library = v); toast("Настройки контента сохранены") } }, "Автоматически добавлять в библиотеку", "Книга попадёт в вашу библиотеку со статусом «Слушаю» после того, как вы дослушаете главу до конца.")
            for (g in sensitiveGenres) {
                val on = cp?.hidden_genres?.contains(g.id) ?: false
                ArToggle(on, { v -> run { val hidden = if (v) (cp?.hidden_genres ?: emptyList()) + g.id else (cp?.hidden_genres ?: emptyList()) - g.id; me = Api.patch<Me>("/me/content-prefs", buildJsonObject { put("hide_nsfw", cp?.hide_nsfw ?: true); put("hidden_genres", JsonArray(hidden.map { JsonPrimitive(it) })) }, asParam); toast("Настройки контента сохранены") } }, "Скрывать «${g.name}»", "Тег помечен администрацией — по умолчанию показывается", enabled = cp != null)
            }
        }

        if (!scoped) Panel(Lucide.ShieldAlert, "Опасная зона", "Аккаунт будет удалён навсегда — это нельзя отменить.", danger = true) {
            if (hasPassword) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ArButton("Удалить аккаунт", { delPw = ""; delOpen = true }, kind = ButtonKind.Danger) }
            else Text("Чтобы удалить аккаунт, сначала задайте пароль в разделе выше.", color = Ar.textMuted, fontSize = 12.sp)
        }
    }

    ArModal(emailOpen, { emailOpen = false }, if (m.email != null) "Изменить почту" else "Добавить почту") {
        ArTextField(newEmail, { newEmail = it }, label = "Новый адрес", keyboardType = KeyboardType.Email, maxLength = Limits.email)
        if (!scoped && hasPassword) { Spacer(Modifier.height(10.dp)); ArTextField(emailPw, { emailPw = it }, label = "Текущий пароль", password = true) }
        Text("Новый адрес нужно будет подтвердить — на него придёт письмо со ссылкой.", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton(if (savingEmail) "Сохраняем…" else "Сохранить", {
                val em = newEmail.trim()
                if (!EMAIL_RE.matches(em)) { toastError("Введите корректный email"); return@ArButton }
                savingEmail = true
                run { try { Api.post<Me>("/me/email", buildJsonObject { put("email", em); if (hasPassword && !scoped) put("password", emailPw) }, asParam); refresh(); emailOpen = false; toast("Почта обновлена — подтвердите её по ссылке в письме") } finally { savingEmail = false } }
            }, kind = ButtonKind.Primary, busy = savingEmail)
        }
    }
    ConfirmDialog(toUnlink != null, { toUnlink = null }, {
        val p = toUnlink ?: return@ConfirmDialog
        run { val r = Api.delete<IdentitiesRes>("/me/identities/${Routes.enc(p.provider)}", params = asParam); identities = r.identities; refresh(); toast("${providerLabel(p.provider)} отвязан") }
    }, toUnlink?.let { "Отвязать ${providerLabel(it.provider)}?" } ?: "", toUnlink?.let { "Войти через ${providerLabel(it.provider)} больше не получится${if (it.email != null || it.display_name.isNotBlank()) " — аккаунт ${it.display_name.ifBlank { it.email }} будет отвязан" else ""}. Привязать обратно можно в любой момент." } ?: "", danger = true)
    ArModal(totpDisableOpen, { totpDisableOpen = false; totpDisablePw = "" }, "Отключить двухфакторную аутентификацию?") {
        Text("При входе больше не будет запрашиваться код из приложения.", color = Ar.textSecondary, fontSize = 13.sp)
        if (!scoped) { Spacer(Modifier.height(10.dp)); ArTextField(totpDisablePw, { totpDisablePw = it }, label = "Подтвердите действие паролем", password = true, placeholder = "Ваш пароль") }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", { totpDisableOpen = false }, kind = ButtonKind.Ghost); Spacer(Modifier.width(8.dp))
            ArButton("Отключить", { run { val u = Api.post<Me>("/me/totp/disable", buildJsonObject { put("password", totpDisablePw) }, asParam); me = u; if (!scoped) auth.setUser(u); totpDisableOpen = false; totpDisablePw = ""; toast("Двухфакторная аутентификация отключена") } }, kind = ButtonKind.Danger)
        }
    }
    ArModal(delOpen, { delOpen = false }, "Удалить аккаунт?") {
        Text("Это необратимо. Аккаунт будет удалён навсегда.", color = Ar.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        ArTextField(delPw, { delPw = it }, label = "Подтвердите действие паролем", password = true, placeholder = "Ваш пароль")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", { delOpen = false }, kind = ButtonKind.Ghost); Spacer(Modifier.width(8.dp))
            ArButton("Удалить аккаунт", {
                if (delPw.isEmpty()) { toastError("Введите пароль, чтобы подтвердить удаление аккаунта"); return@ArButton }
                run { Api.delete<Unit>("/me", buildJsonObject { put("password", delPw) }); delOpen = false; auth.logout(); toast("Ваш аккаунт удалён. До встречи."); nav.tab(Routes.HOME) }
            }, kind = ButtonKind.Danger)
        }
    }
}
