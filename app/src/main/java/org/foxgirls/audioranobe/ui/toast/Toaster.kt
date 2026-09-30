package org.foxgirls.audioranobe.ui.toast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ToastKind { OK, INFO, ERROR }

class ToastItem(val id: Long, val message: String, val kind: ToastKind, val durationMs: Long) {
    val visible = MutableTransitionState(false).apply { targetState = true }
}

/** Global toast queue, like lib/toast.tsx. Call toast() from anywhere. */
object Toaster {
    val items = mutableStateListOf<ToastItem>()
    private var nextId = 1L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private const val MAX_VISIBLE = 4

    fun show(message: String, kind: ToastKind = ToastKind.OK) {
        val ms = when (kind) { ToastKind.OK -> 4500L; ToastKind.INFO -> 5500L; ToastKind.ERROR -> 8000L }
        val item = ToastItem(nextId++, message, kind, ms)
        items.add(item)
        while (items.size > MAX_VISIBLE) items.removeAt(0)
        scope.launch { delay(ms); dismiss(item) }
    }

    /** Starts the leave animation; the host drops the item once it has played. */
    fun dismiss(item: ToastItem) { item.visible.targetState = false }
}

private val ToastEase = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

fun toast(message: String, kind: ToastKind = ToastKind.OK) = Toaster.show(message, kind)
fun toastError(message: String) = Toaster.show(message, ToastKind.ERROR)
fun toastError(e: Throwable) = Toaster.show(e.message?.takeIf { it.isNotBlank() } ?: "Что-то пошло не так", ToastKind.ERROR)

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 14.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val slide = with(LocalDensity.current) { 18.dp.roundToPx() }
        for (t in Toaster.items) key(t.id) {
            LaunchedEffect(t.visible.isIdle, t.visible.currentState) { if (t.visible.isIdle && !t.visible.currentState) Toaster.items.remove(t) }
            AnimatedVisibility(
                t.visible,
                enter = fadeIn(tween(280, easing = ToastEase)) + slideInHorizontally(tween(280, easing = ToastEase)) { slide } + scaleIn(tween(280, easing = ToastEase), 0.97f),
                exit = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { slide } + scaleOut(tween(180), 0.97f),
            ) {
                val accent = when (t.kind) { ToastKind.OK -> Ar.ok; ToastKind.INFO -> Ar.blue; ToastKind.ERROR -> Ar.danger }
                val icon = when (t.kind) { ToastKind.OK -> Lucide.CircleCheck; ToastKind.INFO -> Lucide.Info; ToastKind.ERROR -> Lucide.CircleAlert }
                val remaining = remember { Animatable(1f) }
                LaunchedEffect(Unit) { remaining.animateTo(0f, tween(t.durationMs.toInt(), easing = LinearEasing)) }
                val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                val scope = rememberCoroutineScope()
                val threshold = with(LocalDensity.current) { 72.dp.toPx() }
                Box(
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            translationX = drag.value.x; translationY = drag.value.y
                            alpha = 1f - (drag.value.getDistance() / (threshold * 3)).coerceIn(0f, 0.8f)
                        }
                        .pointerInput(t.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val v = drag.value
                                        val d = v.getDistance()
                                        if (d > threshold) { drag.animateTo(v * (size.width * 1.2f / d), tween(160)); Toaster.items.remove(t) }
                                        else drag.animateTo(Offset.Zero, spring())
                                    }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, spring()) } },
                            ) { change, amount -> change.consume(); scope.launch { drag.snapTo(drag.value + amount) } }
                        }
                        .clip(RoundedCornerShape(10.dp)).background(Ar.surfaceStrong)
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).clickable { Toaster.dismiss(t) },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).size(3.dp, 18.dp).background(accent, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t.message, color = Ar.text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(remaining.value).height(2.dp).background(accent.copy(alpha = 0.6f)))
                }
            }
        }
    }
}
