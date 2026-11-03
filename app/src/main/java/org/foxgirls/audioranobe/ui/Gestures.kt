package org.foxgirls.audioranobe.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.CoroutineStart
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
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

/**
 * Horizontal offset that slides a screen's tab content in from the side the
 * new tab sits on. Apply it with [tabSlide] to the content, or wrap lazy tab
 * items in [slideItems].
 */
@Composable
fun rememberTabSlide(keys: List<String>, active: String): Animatable<Float, AnimationVector1D> {
    val x = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val index = keys.indexOf(active)
    var last by remember { mutableIntStateOf(index) }
    if (index != last) {
        val dir = if (index > last) 1f else -1f
        last = index
        scope.launch(start = CoroutineStart.UNDISPATCHED) { x.snapTo(width * dir); x.animateTo(0f, tween(280)) }
    }
    return x
}

fun Modifier.tabSlide(x: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer { translationX = x.value }

/** Emits the items of [block] with each one sliding by [x], so only the tab content moves and the page header stays still. */
fun LazyListScope.slideItems(x: Animatable<Float, AnimationVector1D>, block: LazyListScope.() -> Unit) = SlideListScope(this, x).block()


private class SlideListScope(private val base: LazyListScope, x: Animatable<Float, AnimationVector1D>) : LazyListScope by base {
    private val slide = Modifier.tabSlide(x)

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        val m = slide
        base.item(key, contentType) { val s = this; Box(m) { s.content() } }
    }

    override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?, itemContent: @Composable LazyItemScope.(Int) -> Unit) {
        val m = slide
        base.items(count, key, contentType) { i -> val s = this; Box(m) { s.itemContent(i) } }
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
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (consumed.y < -2f && !shell.dockCompact) shell.dockCompact = true
                    if (consumed.y > 2f && shell.dockCompact) shell.dockCompact = false
                }
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
