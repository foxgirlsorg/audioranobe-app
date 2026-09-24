package com.audioranobe.app.ui.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.theme.Ar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ToastKind { OK, INFO, ERROR }

class ToastItem(val id: Long, val message: String, val kind: ToastKind)

/** Global toast queue, like lib/toast.tsx. Call toast() from anywhere. */
object Toaster {
    val items = mutableStateListOf<ToastItem>()
    private var nextId = 1L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private const val MAX_VISIBLE = 4

    fun show(message: String, kind: ToastKind = ToastKind.OK) {
        val item = ToastItem(nextId++, message, kind)
        items.add(item)
        while (items.size > MAX_VISIBLE) items.removeAt(0)
        val ms = when (kind) { ToastKind.OK -> 4500L; ToastKind.INFO -> 5500L; ToastKind.ERROR -> 8000L }
        scope.launch { delay(ms); items.remove(item) }
    }

    fun dismiss(item: ToastItem) { items.remove(item) }
}

fun toast(message: String, kind: ToastKind = ToastKind.OK) = Toaster.show(message, kind)
fun toastError(message: String) = Toaster.show(message, ToastKind.ERROR)
fun toastError(e: Throwable) = Toaster.show(e.message?.takeIf { it.isNotBlank() } ?: "Что-то пошло не так", ToastKind.ERROR)

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 14.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (t in Toaster.items) {
            AnimatedVisibility(visible = true, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                val accent = when (t.kind) { ToastKind.OK -> Ar.ok; ToastKind.INFO -> Ar.blue; ToastKind.ERROR -> Ar.danger }
                val icon = when (t.kind) { ToastKind.OK -> Lucide.CircleCheck; ToastKind.INFO -> Lucide.Info; ToastKind.ERROR -> Lucide.CircleAlert }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ar.surfaceStrong)
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable { Toaster.dismiss(t) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(3.dp).size(3.dp, 18.dp).background(accent, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.message, color = Ar.text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
