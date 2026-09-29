package org.foxgirls.audioranobe.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.foxgirls.audioranobe.data.Stores
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Stores.scope.launch { Push.registerFcm(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        runBlocking { Push.show(this@FcmService, message.data) }
    }
}

class UnifiedPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet ?: return
        Stores.scope.launch { Push.registerUnified(endpoint.url, keys.pubKey, keys.auth) }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) return
        val json = runCatching { Json.parseToJsonElement(message.content.decodeToString()) as JsonObject }.getOrNull() ?: return
        val data = json.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }
        Stores.scope.launch { Push.show(applicationContext, data) }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Push.forgetUnified()
    }

    override fun onUnregistered(instance: String) {
        Push.forgetUnified()
    }
}
