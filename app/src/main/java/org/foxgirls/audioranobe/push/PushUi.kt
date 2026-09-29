package org.foxgirls.audioranobe.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.foxgirls.audioranobe.data.Stores

private const val KEY_ASKED = "push_permission_asked"

class PushPermission(val granted: Boolean, private val ask: () -> Unit) {
    fun request() = ask()
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Notification permission + push registration. [request] shows the system dialog while Android still
 * allows it; after a permanent denial (or on Android < 13 with notifications off) it opens the app's
 * notification settings instead. The state is re-read on every resume, so returning from settings updates it.
 */
@Composable
fun rememberPushPermission(): PushPermission {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(Push.permitted(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Stores.prefs.putString(KEY_ASKED, "1")
        granted = Push.permitted(ctx)
        if (ok) scope.launch { Push.sync(ctx) }
    }
    LifecycleResumeEffect(Unit) {
        val now = Push.permitted(ctx)
        if (now != granted) {
            granted = now
            scope.launch { if (now) Push.sync(ctx) else Push.unregister(ctx) }
        }
        onPauseOrDispose {}
    }
    return remember(granted) {
        PushPermission(granted) {
            val act = ctx.activity()
            val canAsk = Build.VERSION.SDK_INT >= 33 && act != null && (
                Stores.prefs.getString(KEY_ASKED) == null ||
                    ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.POST_NOTIFICATIONS)
                )
            if (canAsk) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

/** Mounted in the signed-in shell: registers push, or asks for the permission once per install. */
@Composable
fun PushBootstrap(userId: Int) {
    val ctx = LocalContext.current
    val perm = rememberPushPermission()
    LaunchedEffect(userId) {
        if (perm.granted) Push.sync(ctx)
        else if (Build.VERSION.SDK_INT >= 33 && Stores.prefs.getString(KEY_ASKED) == null) perm.request()
    }
}
