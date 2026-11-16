package org.foxgirls.audioranobe.offline

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One running transfer; [fraction] null = indeterminate, [cancel] stops it. */
data class Transfer(val title: String, val text: String, val fraction: Float? = null, val cancel: (() -> Unit)? = null)

/** Every long transfer of the app, shown as one foreground notification by [DownloadService] while any of them runs. */
object Downloads {
    const val CHAPTERS = "chapters"
    const val VOICE = "tts"
    const val UPDATE = "update"

    private lateinit var appContext: Context
    private val _transfers = MutableStateFlow<Map<String, Transfer>>(emptyMap())
    val transfers: StateFlow<Map<String, Transfer>> = _transfers.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Registers a transfer and starts the service that keeps the app alive while it runs. Call from a user action. */
    fun begin(key: String, title: String, text: String = "", cancel: (() -> Unit)? = null) {
        _transfers.update { it + (key to Transfer(title, text, null, cancel)) }
        DownloadService.start(appContext)
    }

    fun update(key: String, text: String? = null, fraction: Float? = null) {
        _transfers.update { m -> m[key]?.let { t -> m + (key to t.copy(text = text ?: t.text, fraction = fraction ?: t.fraction)) } ?: m }
    }

    fun end(key: String) {
        _transfers.update { it - key }
    }

    /** Stops every transfer; each owner's cancel lambda does the work, and the entries are dropped here. */
    fun cancelAll() {
        val all = _transfers.value
        _transfers.value = emptyMap()
        all.values.forEach { it.cancel?.invoke() }
    }
}
