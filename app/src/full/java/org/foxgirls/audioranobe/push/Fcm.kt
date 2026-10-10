package org.foxgirls.audioranobe.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.foxgirls.audioranobe.BuildConfig
import org.foxgirls.audioranobe.data.Stores
import kotlin.coroutines.resume

object Fcm {
    fun usable(ctx: Context): Boolean {
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

    // ponytail: getToken() is deprecated in firebase-messaging 25; move to register() + onRegistered once that flow is documented.
    @Suppress("DEPRECATION")
    suspend fun token(): String? = suspendCancellableCoroutine { c ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { t -> c.resume(if (t.isSuccessful) t.result else null) }
    }
}

class FcmService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        Stores.scope.launch { Push.registerFcm(token) }
    }

    override fun onRegistered(token: String) {
        Stores.scope.launch { Push.registerFcm(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        runBlocking { Push.show(this@FcmService, message.data) }
    }
}
