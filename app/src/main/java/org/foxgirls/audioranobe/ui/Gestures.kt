package org.foxgirls.audioranobe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Horizontal swipe between the tabs of a screen: a swipe left goes to the next
 * tab, a swipe right to the previous one. Vertical scrolling stays untouched
 * because only the horizontal drag is consumed.
 */
fun Modifier.swipeTabs(keys: List<String>, active: String, onChange: (String) -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    pointerInput(keys, active) {
        var total = 0f
        var vertical = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f; vertical = 0f },
            onDragEnd = {
                val i = keys.indexOf(active)
                if (i < 0 || abs(total) < threshold || abs(vertical) > abs(total)) return@detectHorizontalDragGestures
                if (total < 0 && i < keys.lastIndex) onChange(keys[i + 1])
                if (total > 0 && i > 0) onChange(keys[i - 1])
            },
        ) { change, dragAmount ->
            total += dragAmount
            vertical += change.positionChange().y
        }
    }
}

/** Swipe from the left edge to go back (the chat thread has no dock, so this is its way out). */
fun Modifier.swipeBack(onBack: () -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val edge = with(density) { 28.dp.toPx() }
    val threshold = with(density) { 90.dp.toPx() }
    pointerInput(Unit) {
        var fromEdge = false
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { pos: Offset -> fromEdge = pos.x <= edge; total = 0f },
            onDragEnd = { if (fromEdge && total > threshold) onBack() },
        ) { _, dragAmount -> total += dragAmount }
    }
}

/**
 * Drag down to close (the full-screen player). The content follows the finger
 * and springs back when the drag is too short.
 */
fun Modifier.swipeDownToDismiss(onDismiss: () -> Unit): Modifier = composed {
    val density = LocalDensity.current
    val threshold = with(density) { 140.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    this
        .graphicsLayer { translationY = offset.value; alpha = 1f - (offset.value / (threshold * 3)).coerceIn(0f, 0.6f) }
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (offset.value > threshold) { onDismiss(); scope.launch { offset.snapTo(0f) } }
                    else scope.launch { offset.animateTo(0f) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, dragAmount ->
                val next = (offset.value + dragAmount).coerceAtLeast(0f)
                if (next > 0f || dragAmount > 0f) { change.consume(); scope.launch { offset.snapTo(next) } }
            }
        }
}

/**
 * Collapses the dock while the list scrolls down and brings it back on the
 * first scroll up, like the site's dock hiding behind the scroll.
 */
@Composable
fun Modifier.dockScrollAware(): Modifier {
    val shell = LocalShell.current
    val connection = remember(shell) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -6f && !shell.dockCompact) shell.dockCompact = true
                    if (available.y > 6f && shell.dockCompact) shell.dockCompact = false
                }
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
