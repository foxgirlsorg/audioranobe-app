package org.foxgirls.audioranobe.ui.screens.auth

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.ApiError
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.core.msg
import org.foxgirls.audioranobe.data.Identity
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Me
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val USERNAME_RE = Regex("^[A-Za-z0-9_]{3,30}$")
private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@Composable
fun LoginScreen() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val config by Stores.config.config.collectAsStateWithLifecycle()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaToken by remember { mutableStateOf("") }
    var captchaNonce by remember { mutableIntStateOf(0) }
    var loginErr by remember { mutableStateOf<String?>(null) }
    var passErr by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var needsTotp by remember { mutableStateOf(false) }
    var totp by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { Stores.config.ensure(); if (auth.loading.value) auth.refresh() }
    LaunchedEffect(user) { if (user != null) nav.back() }

    fun submit() {
        if (submitting) return
        if (needsTotp) {
            if (totp.isBlank()) { formError = "Введите код из приложения"; return }
            formError = ""; submitting = true
            scope.launch {
                try { auth.login(login.trim(), password, captchaToken, totp.trim()) } catch (e: Exception) { formError = e.msg(); totp = "" } finally { submitting = false }
            }
            return
        }
        loginErr = if (login.isBlank()) "Введите имя пользователя или email" else null
        passErr = if (password.isEmpty()) "Введите пароль" else null
        formError = ""
        if (loginErr != null || passErr != null) return
        if (config?.captcha?.enabled == true && captchaToken.isEmpty()) { formError = "Подтвердите, что вы не робот"; return }
        submitting = true
        scope.launch {
            try { auth.login(login.trim(), password, captchaToken) } catch (e: Exception) {
                if (e is ApiError && e.code == "totp_required") { needsTotp = true; formError = "" }
                else { formError = e.msg(); captchaToken = ""; captchaNonce++ }
            } finally { submitting = false }
        }
    }

    AuthCard("Вход", "в аккаунт", formError = formError, showBack = false) {
        if (needsTotp) {
            ArTextField(totp, { totp = it.take(9) }, label = "Код из приложения-аутентификатора", placeholder = "000000", keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, onImeAction = { submit() }, hint = "Нет доступа к приложению? Введите один из запасных кодов вместо этого.")
        } else {
            ArTextField(login, { login = it; loginErr = null }, label = "Имя пользователя или email", placeholder = "listener_01", maxLength = Limits.email, error = loginErr, imeAction = ImeAction.Next)
            Spacer(Modifier.height(12.dp))
            ArTextField(password, { password = it; passErr = null }, label = "Пароль", placeholder = "••••••••", password = true, maxLength = Limits.password, error = passErr, imeAction = ImeAction.Done, onImeAction = { submit() })
            Spacer(Modifier.height(12.dp))
            CaptchaWidget(captchaNonce) { captchaToken = it }
        }
        Spacer(Modifier.height(6.dp))
        ArButton(if (submitting) "Входим…" else "Войти", { submit() }, kind = ButtonKind.Primary, icon = Lucide.LogIn, fullWidth = true, busy = submitting)
        if (needsTotp) AltLink("Назад", { needsTotp = false; totp = ""; formError = "" }, modifier = Modifier.fillMaxWidth())
        else {
            ProviderButtons("login")
            AltLink("Забыли пароль?", { nav.go(Routes.FORGOT) }, modifier = Modifier.fillMaxWidth())
            AltLink("Создать аккаунт", { nav.replace(Routes.REGISTER) }, prefix = "Впервые у нас?", modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun RegisterScreen() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val config by Stores.config.config.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(false) }
    var captchaToken by remember { mutableStateOf("") }
    var captchaNonce by remember { mutableIntStateOf(0) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var formError by remember { mutableStateOf("") }
    var emailTaken by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { Stores.config.ensure(); if (auth.loading.value) auth.refresh() }
    LaunchedEffect(user) { if (user != null) nav.back() }

    fun submit() {
        if (submitting) return
        val errs = mutableMapOf<String, String>()
        val u = username.trim(); val dn = displayName.trim().replace(Regex("\\s+"), " "); val em = email.trim()
        if (!USERNAME_RE.matches(u)) errs["username"] = "3–30 символов: только латинские буквы, цифры и подчёркивания"
        if (dn.length > Limits.displayName) errs["displayName"] = "Не длиннее ${Limits.displayName} символов"
        if (!EMAIL_RE.matches(em)) errs["email"] = "Введите корректный email"
        if (password.length < 8) errs["password"] = "Минимум 8 символов"
        if (confirm != password) errs["confirm"] = "Пароли не совпадают"
        if (!terms) errs["terms"] = "Примите условия использования и политику конфиденциальности"
        errors = errs; formError = ""
        if (errs.isNotEmpty()) return
        if (config?.captcha?.enabled == true && captchaToken.isEmpty()) { formError = "Подтвердите, что вы не робот"; return }
        submitting = true
        scope.launch {
            try { auth.register(u, em, password, terms, dn, captchaToken) } catch (e: Exception) {
                val m = e.msg(); formError = m; emailTaken = Regex("уже зарегистрирован|уже заняты").containsMatchIn(m); captchaToken = ""; captchaNonce++
            } finally { submitting = false }
        }
    }

    AuthCard("Создать", "аккаунт", formError = formError, showBack = false) {
        if (emailTaken) AltLink("Войти", { nav.replace(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        ArTextField(username, { username = it; errors = errors - "username" }, label = "Имя пользователя", placeholder = "iloveranobe228", maxLength = Limits.username, error = errors["username"], hint = "Латинские буквы, цифры и подчёркивания, 3–30 символов", imeAction = ImeAction.Next)
        Spacer(Modifier.height(12.dp))
        ArTextField(displayName, { displayName = it; errors = errors - "displayName" }, label = "Никнейм", placeholder = "Как вас называть", maxLength = Limits.displayName, error = errors["displayName"], hint = "Отображается вместо имени пользователя. Можно оставить пустым и задать позже", imeAction = ImeAction.Next)
        Spacer(Modifier.height(12.dp))
        ArTextField(email, { email = it; errors = errors - "email" }, label = "Email", placeholder = "you@example.com", maxLength = Limits.email, error = errors["email"], keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        Spacer(Modifier.height(12.dp))
        ArTextField(password, { password = it; errors = errors - "password" }, label = "Пароль", placeholder = "Минимум 8 символов", password = true, maxLength = Limits.password, error = errors["password"], imeAction = ImeAction.Next)
        Spacer(Modifier.height(12.dp))
        ArTextField(confirm, { confirm = it; errors = errors - "confirm" }, label = "Повторите пароль", placeholder = "••••••••", password = true, maxLength = Limits.password, error = errors["confirm"], imeAction = ImeAction.Done, onImeAction = { submit() })
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { terms = !terms; errors = errors - "terms" }) {
            Checkbox(terms, { terms = it; errors = errors - "terms" }, colors = CheckboxDefaults.colors(checkedColor = Ar.accent, uncheckedColor = Ar.borderStrong, checkmarkColor = Ar.white))
            Column {
                Row { Text("Я принимаю ", color = Ar.textSecondary, fontSize = 13.sp); Text("условия использования", color = Ar.accent, fontSize = 13.sp, modifier = Modifier.clickable { nav.go(Routes.legal("terms")) }) }
                Row { Text("и ", color = Ar.textSecondary, fontSize = 13.sp); Text("политику конфиденциальности", color = Ar.accent, fontSize = 13.sp, modifier = Modifier.clickable { nav.go(Routes.legal("privacy")) }) }
            }
        }
        errors["terms"]?.let { Text(it, color = Ar.danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        CaptchaWidget(captchaNonce) { captchaToken = it }
        ArButton(if (submitting) "Создаём аккаунт…" else "Создать аккаунт", { submit() }, kind = ButtonKind.Primary, icon = Lucide.UserPlus, fullWidth = true, busy = submitting)
        ProviderButtons("login")
        AltLink("Войти", { nav.replace(Routes.LOGIN) }, prefix = "Уже есть аккаунт?", modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ForgotScreen() {
    val nav = LocalNav.current
    val config by Stores.config.config.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var captchaToken by remember { mutableStateOf("") }
    var captchaNonce by remember { mutableIntStateOf(0) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Stores.config.ensure() }

    if (sent) {
        AuthCard("Письмо", "отправлено", eyebrow = "Проверьте почту") {
            Text("Письмо с инструкциями отправлено на указанный адрес. Если письмо не приходит, проверьте папку «Спам».", color = Ar.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
            AltLink("Вернуться ко входу", { nav.replace(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    AuthCard("Забыли", "пароль?", eyebrow = "Восстановление доступа", formError = error) {
        ArTextField(email, { email = it; error = "" }, label = "Email", placeholder = "you@example.com", keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
        Spacer(Modifier.height(12.dp))
        CaptchaWidget(captchaNonce) { captchaToken = it }
        ArButton(if (submitting) "Отправляем…" else "Отправить инструкции", {
            val em = email.trim()
            if (em.isEmpty()) { error = "Введите email"; return@ArButton }
            if (config?.captcha?.enabled == true && captchaToken.isEmpty()) { error = "Подтвердите, что вы не робот"; return@ArButton }
            error = ""; submitting = true
            scope.launch {
                try { Api.post<Unit>("/auth/forgot", buildJsonObject { put("email", em); if (captchaToken.isNotEmpty()) put("captcha_token", captchaToken) }); sent = true }
                catch (e: Exception) { toastError(e); captchaToken = ""; captchaNonce++ } finally { submitting = false }
            }
        }, kind = ButtonKind.Primary, icon = Lucide.Mail, fullWidth = true, busy = submitting)
        AltLink("Вернуться ко входу", { nav.replace(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ResetScreen(token: String) {
    val nav = LocalNav.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var pErr by remember { mutableStateOf<String?>(null) }
    var cErr by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (token.isBlank()) {
        AuthCard("Неверная", "ссылка") {
            Text("Ссылка для сброса пароля недействительна или устарела. Запросите новую ссылку для восстановления.", color = Ar.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
            AltLink("Получить новую ссылку", { nav.replace(Routes.FORGOT) }, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    if (success) {
        AuthCard("Пароль", "обновлён") {
            Text("Пароль успешно изменён. Теперь вы можете войти с новым паролем.", color = Ar.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
            AltLink("Войти в аккаунт", { nav.replace(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    AuthCard("Сбросить", "пароль", formError = error) {
        ArTextField(password, { password = it; pErr = null }, label = "Новый пароль", placeholder = "Минимум 8 символов", password = true, error = pErr, imeAction = ImeAction.Next)
        Spacer(Modifier.height(12.dp))
        ArTextField(confirm, { confirm = it; cErr = null }, label = "Повторите пароль", placeholder = "••••••••", password = true, error = cErr, imeAction = ImeAction.Done)
        Spacer(Modifier.height(14.dp))
        ArButton(if (submitting) "Сохраняем…" else "Установить пароль", {
            pErr = if (password.length < 8) "Минимум 8 символов" else null
            cErr = if (confirm != password) "Пароли не совпадают" else null
            error = ""
            if (pErr != null || cErr != null) return@ArButton
            submitting = true
            scope.launch { try { Api.post<Unit>("/auth/reset", buildJsonObject { put("token", token); put("password", password) }); success = true } catch (e: Exception) { error = e.msg() } finally { submitting = false } }
        }, kind = ButtonKind.Primary, icon = Lucide.KeyRound, fullWidth = true, busy = submitting)
        AltLink("Вернуться ко входу", { nav.replace(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun VerifyScreen(token: String) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    var state by remember { mutableStateOf("working") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(token) {
        if (token.isBlank()) { state = "failed"; error = "В ссылке нет кода подтверждения."; return@LaunchedEffect }
        try { Api.post<Unit>("/auth/verify", buildJsonObject { put("token", token) }); state = "done"; runCatching { auth.refresh() } } catch (e: Exception) { state = "failed"; error = e.msg() }
    }
    when (state) {
        "working" -> LoadingCard("Подтверждаем почту…")
        "done" -> Box(Modifier.fillMaxSize().statusBarsPadding()) { EmptyState("Почта подтверждена", "Спасибо! Адрес привязан к вашему аккаунту.", Lucide.CircleCheck) { ArButton("На главную", { nav.tab(nav.home) }, kind = ButtonKind.Primary) } }
        else -> Box(Modifier.fillMaxSize().statusBarsPadding()) { EmptyState("Не удалось подтвердить", error, Lucide.CircleAlert) { ArButton("Отправить ссылку заново", { nav.replace(Routes.settings("security")) }, kind = ButtonKind.Ghost) } }
    }
}

@Composable
fun SetupScreen() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val loading by auth.loading.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var displayName by remember(user?.id) { mutableStateOf(user?.display_name ?: "") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (auth.loading.value) auth.refresh() }
    LaunchedEffect(loading, user) {
        if (loading) return@LaunchedEffect
        if (user == null) nav.replace(Routes.LOGIN) else if (!user!!.needs_setup) nav.replace(Routes.HOME)
    }
    if (loading || user == null || !user!!.needs_setup) { LoadingCard("Загрузка…"); return }
    val ok = USERNAME_RE.matches(username)
    AuthCard("Настройте", "профиль", eyebrow = "Почти готово") {
        Text("Осталось выбрать логин для вашей страницы.", color = Ar.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        ArTextField(username, { username = it }, label = "Логин", placeholder = "my_login", maxLength = Limits.username, hint = "3-30 символов: латиница, цифры и подчёркивание. Уникален, его видно в адресе /user/${username.ifBlank { "my_login" }} и в упоминаниях @${username.ifBlank { "my_login" }}.")
        Spacer(Modifier.height(12.dp))
        ArTextField(displayName, { displayName = it }, label = "Отображаемое имя", placeholder = "Как вас показывать", maxLength = Limits.displayName, hint = "Любые символы, до 40 знаков. Можно оставить пустым — тогда будет показан логин.")
        Spacer(Modifier.height(14.dp))
        ArButton(if (saving) "Сохраняем…" else "Продолжить", {
            if (saving) return@ArButton
            if (!ok) { toastError("Логин: 3-30 символов, только латиница, цифры и подчёркивание"); return@ArButton }
            saving = true
            scope.launch {
                try { Api.post<Me>("/me/setup", buildJsonObject { put("username", username); put("display_name", displayName) }); auth.refresh(); toast("Профиль настроен"); nav.replace(Routes.HOME) } catch (e: Exception) { toastError(e) } finally { saving = false }
            }
        }, kind = ButtonKind.Primary, fullWidth = true, busy = saving, enabled = ok)
    }
}

@Serializable
private data class OAuthUrlRes(val url: String, val state: String)

@Serializable
private data class OAuthCallbackRes(val token: String? = null, val user: Me? = null, val ok: Boolean? = null, val identities: List<Identity>? = null)

/**
 * The site hands the result back as audioranobe://oauth/callback/{provider}?code&state (provider sign-in)
 * or audioranobe://oauth/session?handoff= (the browser's existing account); MainActivity drops it here.
 */
object OAuthReturn {
    val result = MutableStateFlow<Uri?>(null)
    var waiting = false
}

/**
 * OAuth sign-in / account linking in a Custom Tab, so the provider sees the real browser with its
 * saved sessions. The `app` param makes the site's callback page forward code+state to this app
 * instead of exchanging them; the exchange then happens here, like app/auth/callback/[provider]/page.tsx.
 */
@Composable
fun OAuthScreen(provider: String, mode: String) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val context = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val returned by OAuthReturn.result.collectAsStateWithLifecycle()

    fun openTab(u: String) {
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(Ar.bg.toArgb()).build())
                .build()
                .launchUrl(context, Uri.parse(u))
        } catch (_: ActivityNotFoundException) { error = "Не найден браузер для входа." }
    }

    DisposableEffect(Unit) {
        OAuthReturn.waiting = true
        onDispose { OAuthReturn.waiting = false }
    }

    LaunchedEffect(provider, mode) {
        if (OAuthReturn.result.value != null) return@LaunchedEffect
        try {
            val res = Api.get<OAuthUrlRes>("/auth/oauth/${Routes.enc(provider)}/url", mapOf("mode" to mode, "app" to context.packageName))
            val u = "${Api.siteUrl}/auth/app?state=${Uri.encode(res.state)}"
            url = u
            openTab(u)
        } catch (e: Exception) { error = e.msg() }
    }

    fun finish(path: String, body: JsonObject) {
        if (finishing) return
        finishing = true
        scope.launch {
            try {
                val res = Api.post<OAuthCallbackRes>(path, body)
                if (res.token != null && res.user != null) {
                    auth.adoptSession(res.user)
                    nav.replace(if (res.user.needs_setup) Routes.SETUP else Routes.HOME)
                } else {
                    auth.refresh()
                    toast("Аккаунт привязан")
                    nav.replace(Routes.settings("security"))
                }
            } catch (e: Exception) { error = e.msg(); finishing = false }
        }
    }

    LaunchedEffect(returned) {
        val u = returned ?: return@LaunchedEffect
        OAuthReturn.result.value = null
        val code = u.getQueryParameter("code") ?: ""
        val state = u.getQueryParameter("state") ?: ""
        val handoff = u.getQueryParameter("handoff") ?: ""
        when {
            u.getQueryParameter("error") != null -> error = "Вход отменён."
            u.pathSegments.firstOrNull() == "session" && handoff.isNotEmpty() ->
                finish("/auth/oauth/handoff/redeem", buildJsonObject { put("handoff", handoff) })
            code.isEmpty() || state.isEmpty() -> error = "Ссылка неполная — попробуйте войти заново."
            else -> finish("/auth/oauth/${Routes.enc(u.pathSegments.getOrNull(1) ?: provider)}/callback", buildJsonObject { put("code", code); put("state", state) })
        }
    }

    val err = error
    val u = url
    when {
        err != null -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
            EmptyState("Не удалось войти", err, Lucide.ShieldAlert) { ArButton("Вернуться ко входу", { nav.replace(Routes.LOGIN) }, kind = ButtonKind.Primary) }
        }
        u == null || finishing -> LoadingCard(if (finishing) "Завершаем вход…" else "Открываем $provider…")
        else -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
            EmptyState("Вход через $provider", "Завершите вход в открывшемся браузере.", Lucide.Lock) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArButton("Открыть снова", { openTab(u) }, kind = ButtonKind.Primary, fullWidth = true)
                    ArButton("Отмена", { nav.back() }, fullWidth = true)
                }
            }
        }
    }
}
