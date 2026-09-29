package org.foxgirls.audioranobe.data

import androidx.compose.runtime.staticCompositionLocalOf
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.ApiError
import org.foxgirls.audioranobe.core.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Auth context: mirrors lib/auth.tsx. */
class AuthStore(private val scope: CoroutineScope) {
    private val _user = MutableStateFlow<Me?>(null)
    val user: StateFlow<Me?> = _user.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var lastHeader: String? = null

    init {
        scope.launch {
            Api.viewerHeader.collect { h ->
                if (h == null || h == lastHeader) return@collect
                lastHeader = h
                val obj = Api.decodeViewerHeader(h)
                if (obj == null) {
                    _user.value = null
                } else {
                    val slim = runCatching { AppJson.decodeFromJsonElement(Me.serializer(), obj) }.getOrNull()
                    if (slim != null) {
                        // Keep the heavy fields of a previously fetched Me for the same account.
                        val prev = _user.value
                        _user.value = if (prev != null && prev.id == slim.id) slim.copy(
                            bio = prev.bio, socials = prev.socials, identities = prev.identities,
                            notification_prefs = prev.notification_prefs, content_prefs = prev.content_prefs,
                            narrators_count = prev.narrators_count, totp_enabled = prev.totp_enabled,
                            blur_unlistened_illustrations = prev.blur_unlistened_illustrations,
                            auto_add_to_library = prev.auto_add_to_library,
                        ) else slim
                    }
                }
                _loading.value = false
            }
        }
        scope.launch { refresh(); _loading.value = false }
    }

    suspend fun refresh() {
        try {
            _user.value = Api.get<Me>("/me")
        } catch (e: ApiError) {
            if (e.status == 401) _user.value = null
        } catch (_: Exception) {}
    }

    suspend fun login(login: String, password: String, captchaToken: String = "", totpCode: String = "") {
        val res = Api.post<LoginResponse>("/auth/login", buildJsonObject {
            put("login", login); put("password", password)
            if (captchaToken.isNotEmpty()) put("captcha_token", captchaToken)
            if (totpCode.isNotEmpty()) put("totp_code", totpCode)
        })
        _user.value = res.user
    }

    suspend fun register(username: String, email: String, password: String, acceptTerms: Boolean, displayName: String = "", captchaToken: String = "") {
        val res = Api.post<LoginResponse>("/auth/register", buildJsonObject {
            put("username", username); put("email", email); put("password", password); put("accept_terms", acceptTerms)
            if (displayName.isNotEmpty()) put("display_name", displayName)
            if (captchaToken.isNotEmpty()) put("captcha_token", captchaToken)
        })
        _user.value = res.user
    }

    fun adoptSession(me: Me) {
        _user.value = me
    }

    fun setUser(me: Me?) {
        _user.value = me
    }

    fun logout() {
        _user.value = null
        scope.launch {
            runCatching { org.foxgirls.audioranobe.push.Push.unregister(org.foxgirls.audioranobe.App.instance) }
            runCatching { Api.post<Unit>("/auth/logout") }
            Api.cookies.clear()
        }
    }

    fun can(perm: String) = user.value?.can(perm) ?: false
    val isMod get() = can("mod.panel")
}

@kotlinx.serialization.Serializable
data class LoginResponse(val token: String = "", val user: Me)

/** One poll for every dock badge (messages, notifications, friend requests), see lib/badges.tsx. */
class BadgesStore(private val scope: CoroutineScope, private val auth: AuthStore) {
    private val _badges = MutableStateFlow(Badges())
    val badges: StateFlow<Badges> = _badges.asStateFlow()
    private var pollJob: Job? = null
    @Volatile var foreground = true

    init {
        scope.launch {
            auth.user.collect { u ->
                pollJob?.cancel()
                if (u == null) {
                    _badges.value = Badges()
                } else {
                    pollJob = scope.launch {
                        while (isActive) {
                            if (foreground) refreshNow()
                            delay(30_000)
                        }
                    }
                }
            }
        }
    }

    suspend fun refreshNow() {
        if (auth.user.value == null) return
        runCatching { Api.get<Badges>("/me/summary") }.onSuccess { _badges.value = it }
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    fun patch(f: (Badges) -> Badges) {
        _badges.value = f(_badges.value)
    }
}

/** Lazily fetched public /config, see lib/config.tsx. */
class ConfigStore(private val scope: CoroutineScope) {
    private val _config = MutableStateFlow<AppConfig?>(null)
    val config: StateFlow<AppConfig?> = _config.asStateFlow()
    private var started = false

    fun ensure() {
        if (started) return
        started = true
        scope.launch {
            _config.value = runCatching { Api.get<AppConfig>("/config") }.getOrElse { AppConfig() }
        }
    }

    suspend fun await(): AppConfig {
        ensure()
        var c = _config.value
        var n = 0
        while (c == null && n++ < 200) { delay(50); c = _config.value }
        return c ?: AppConfig()
    }
}

/** The signed-in user's narrator teams, see lib/narrators.tsx. */
class MyNarratorsStore(private val scope: CoroutineScope, private val auth: AuthStore) {
    private val _narrators = MutableStateFlow<List<NarratorCard>>(emptyList())
    val narrators: StateFlow<List<NarratorCard>> = _narrators.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private var loading = false
    private var forUser: Int? = null

    fun ensureLoaded(force: Boolean = false) {
        val u = auth.user.value ?: return
        if (forUser != u.id) { forUser = u.id; _loaded.value = false; _narrators.value = emptyList() }
        if ((_loaded.value && !force) || loading) return
        loading = true
        scope.launch {
            runCatching { Api.get<List<NarratorCard>>("/panel/narrators") }
                .onSuccess { _narrators.value = it; _loaded.value = true }
            loading = false
        }
    }

    fun reset() {
        _narrators.value = emptyList(); _loaded.value = false
    }
}

/** Process-wide singletons. Initialised by App. */
object Stores {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var auth: AuthStore
    lateinit var badges: BadgesStore
    lateinit var config: ConfigStore
    lateinit var myNarrators: MyNarratorsStore
    lateinit var prefs: org.foxgirls.audioranobe.core.Prefs

    fun init(prefs: org.foxgirls.audioranobe.core.Prefs) {
        this.prefs = prefs
        auth = AuthStore(scope)
        badges = BadgesStore(scope, auth)
        config = ConfigStore(scope)
        myNarrators = MyNarratorsStore(scope, auth)
    }
}

val LocalAuth = staticCompositionLocalOf<AuthStore> { error("no auth") }
