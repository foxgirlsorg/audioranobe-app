package org.foxgirls.audioranobe.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.foxgirls.audioranobe.BuildConfig
import org.foxgirls.audioranobe.MainActivity
import org.foxgirls.audioranobe.R
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.data.Stores
import org.unifiedpush.android.connector.UnifiedPush
import kotlin.coroutines.resume

@Serializable
private data class PushKeyRes(val key: String = "", val fcm: Boolean = false)

@Serializable
private data class FcmTokenRes(val ok: Boolean = false, val enabled: Boolean = false)

/**
 * System push for the signed-in user: FCM when this build has a Firebase config, Play services are
 * deliver a token and the server has FCM enabled; otherwise UnifiedPush (Web Push through a distributor app
 * such as ntfy) when one is installed. Payload: {title, body, url, icon, type, tag}.
 */
object Push {
    enum class Method { NONE, FCM, UNIFIED }

    private const val UP_INSTANCE = "default"
    private const val KEY_FCM_TOKEN = "push_fcm_token"
    private const val KEY_UP_ENDPOINT = "push_up_endpoint"

    private val _method = MutableStateFlow(Method.NONE)
    val method: StateFlow<Method> = _method.asStateFlow()

    fun permitted(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** App start: Firebase must exist before FcmService can receive in a cold process. */
    fun init(ctx: Context) {
        runCatching { fcmUsable(ctx) }
    }

    fun hasDistributor(ctx: Context): Boolean = UnifiedPush.getDistributors(ctx).isNotEmpty()

    private fun fcmUsable(ctx: Context): Boolean {
        if (BuildConfig.FCM_APP_ID.isBlank() || BuildConfig.FCM_API_KEY.isBlank() || BuildConfig.FCM_PROJECT_ID.isBlank()) return false
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FCM_APP_ID)
                    .setApiKey(BuildConfig.FCM_API_KEY)
                    .setProjectId(BuildConfig.FCM_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                    .build(),
            )
        }
        return true
    }

    /** Registers this device for the signed-in user. Safe to call on every start; no-op without permission. */
    suspend fun sync(ctx: Context) {
        if (!permitted(ctx)) { _method.value = Method.NONE; return }
        ensureChannels(ctx)
        val server = runCatching { Api.get<PushKeyRes>("/push/public-key") }.getOrNull() ?: return
        if (server.fcm && fcmUsable(ctx)) {
            val token = fcmToken()
            if (token != null && registerFcm(token)) {
                if (Stores.prefs.getString(KEY_UP_ENDPOINT) != null) unregisterUnified(ctx)
                _method.value = Method.FCM
                return
            }
        }
        if (server.key.isNotBlank() && hasDistributor(ctx)) {
            UnifiedPush.tryUseCurrentOrDefaultDistributor(ctx) { ok ->
                if (ok) UnifiedPush.register(ctx, UP_INSTANCE, "AudioRanobe", server.key)
            }
            if (UnifiedPush.getAckDistributor(ctx) != null) _method.value = Method.UNIFIED
            return
        }
        _method.value = Method.NONE
    }

    /** Called before logout while the session cookie is still valid. */
    suspend fun unregister(ctx: Context) {
        Stores.prefs.getString(KEY_FCM_TOKEN)?.let { t ->
            runCatching { Api.delete<Unit>("/me/fcm-tokens", buildJsonObject { put("token", t) }) }
            Stores.prefs.putString(KEY_FCM_TOKEN, null)
        }
        unregisterUnified(ctx)
        _method.value = Method.NONE
    }

    private suspend fun unregisterUnified(ctx: Context) {
        Stores.prefs.getString(KEY_UP_ENDPOINT)?.let { e ->
            runCatching { Api.delete<Unit>("/me/push-subscriptions", buildJsonObject { put("endpoint", e) }) }
            Stores.prefs.putString(KEY_UP_ENDPOINT, null)
        }
        runCatching { UnifiedPush.unregister(ctx, UP_INSTANCE) }
    }

    private suspend fun fcmToken(): String? = suspendCancellableCoroutine { c ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { t -> c.resume(if (t.isSuccessful) t.result else null) }
    }

    suspend fun registerFcm(token: String): Boolean {
        val res = runCatching { Api.put<FcmTokenRes>("/me/fcm-tokens", buildJsonObject { put("token", token) }) }.getOrNull() ?: return false
        if (res.enabled) Stores.prefs.putString(KEY_FCM_TOKEN, token)
        return res.enabled
    }

    suspend fun registerUnified(endpoint: String, p256dh: String, auth: String) {
        runCatching {
            Api.put<Unit>("/me/push-subscriptions", buildJsonObject {
                put("endpoint", endpoint)
                putJsonObject("keys") { put("p256dh", p256dh); put("auth", auth) }
            })
            Stores.prefs.putString(KEY_UP_ENDPOINT, endpoint)
            _method.value = Method.UNIFIED
        }
    }

    fun forgetUnified() {
        Stores.prefs.putString(KEY_UP_ENDPOINT, null)
        if (_method.value == Method.UNIFIED) _method.value = Method.NONE
    }

    private fun channelFor(type: String) = when (type) {
        "dm" -> "dm"
        "new_chapter", "narrator_release", "narrator_post", "narration_ready" -> "releases"
        "comment_reply", "narrator_comment", "mention", "friend_request", "friend_accept" -> "social"
        else -> "system"
    }

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel("dm", "Личные сообщения", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("social", "Ответы, упоминания и друзья", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("releases", "Новые главы и релизы", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("system", "Модерация и системные", NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    /** Shows one pushed notification. Suspends briefly to fetch the large icon. */
    suspend fun show(ctx: Context, data: Map<String, String>) {
        if (!permitted(ctx)) return
        ensureChannels(ctx)
        val type = data["type"].orEmpty()
        val tag = data["tag"]?.takeIf { it.isNotBlank() }
        val body = data["body"].orEmpty()
        val url = data["url"].orEmpty()
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(Api.siteUrl + url.ifBlank { "/me/notifications" }), ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val id = tag?.hashCode() ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channelFor(type))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFDE6161.toInt())
            .setContentTitle(data["title"]?.ifBlank { null } ?: "AudioRanobe")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setLargeIcon(largeIcon(ctx, data["icon"]))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(if (type == "dm") NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(tag, id, n)
        Stores.badges.refresh()
    }

    private suspend fun largeIcon(ctx: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank() || url.endsWith(".svg")) return null
        return withTimeoutOrNull(4000) {
            val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).size(192).allowHardware(false).build())
            (r.drawable as? BitmapDrawable)?.bitmap
        }
    }
}
