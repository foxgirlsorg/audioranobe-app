package org.foxgirls.audioranobe.push

import android.content.Context

/** No Firebase in this build; push goes through UnifiedPush only. */
object Fcm {
    fun usable(ctx: Context): Boolean = false
    suspend fun token(): String? = null
}
