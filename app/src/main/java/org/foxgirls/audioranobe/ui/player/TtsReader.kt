package org.foxgirls.audioranobe.ui.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import android.os.Build
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.tts.TtsText
import org.foxgirls.audioranobe.ui.theme.Ar

/**
 * The chapter text of a read-aloud chapter, scrolled along with the narration: the sentence being spoken is
 * highlighted, earlier text is dimmed, and tapping a sentence jumps there. [position] is in stored-length seconds.
 */
@Composable
fun TtsReader(paragraphs: List<List<String>>, position: Double, prompter: Boolean, modifier: Modifier = Modifier) {
    // Characters before each paragraph / sentence, on the same scale the player counts its position in.
    val starts = remember(paragraphs) {
        var chars = 0
        paragraphs.map { p -> p.map { s -> chars.also { chars += s.length } } }
    }
    val chars = position * TtsText.CHARS_PER_SECOND
    var para = 0
    var sent = 0
    for (pi in starts.indices) for (si in starts[pi].indices) if (starts[pi][si] <= chars) { para = pi; sent = si }
    val current = para to sent
    if (prompter) return PrompterReader(paragraphs, starts, current, modifier)
    val list = rememberLazyListState()
    LaunchedEffect(current.first, paragraphs) { list.animateScrollToItem(current.first + 1, scrollOffset = -40) }

    LazyColumn(modifier.fillMaxSize().verticalFade(32.dp), state = list, contentPadding = PaddingValues(vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(24.dp)) }
        itemsIndexed(paragraphs) { pi, sentences ->
            val active = if (pi == current.first) current.second else -1
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val text = remember(sentences, current.first, active) {
                buildAnnotatedString {
                    sentences.forEachIndexed { si, s ->
                        val style = when {
                            pi == current.first && si == active -> SpanStyle(color = Ar.white, fontWeight = FontWeight.SemiBold)
                            pi < current.first || (pi == current.first && si < active) -> SpanStyle(color = Ar.textMuted)
                            else -> SpanStyle(color = Ar.textSecondary)
                        }
                        withStyle(style) { append(s) }
                        if (si < sentences.lastIndex) append(" ")
                    }
                }
            }
            Text(
                text, fontSize = 17.sp, lineHeight = 26.sp,
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).pointerInput(sentences, starts) {
                    detectTapGestures { pos ->
                        val offset = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                        var at = 0
                        var hit = 0
                        sentences.forEachIndexed { si, s -> if (offset >= at) hit = si; at += s.length + 1 }
                        PlayerController.seekText(starts[pi][hit] / TtsText.CHARS_PER_SECOND)
                    }
                },
            )
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

/** "Суфлёр" (the site's prompter style): one centered sentence per line, the spoken one held in the middle of the screen. */
@Composable
private fun PrompterReader(paragraphs: List<List<String>>, starts: List<List<Int>>, current: Pair<Int, Int>, modifier: Modifier) {
    data class Line(val text: String, val start: Int, val opensParagraph: Boolean)
    val lines = remember(paragraphs) {
        paragraphs.flatMapIndexed { pi, p -> p.mapIndexed { si, s -> Line(s, starts[pi][si], si == 0 && pi > 0) } }
    }
    val cur = starts.take(current.first).sumOf { it.size } + current.second
    val list = rememberLazyListState()
    LaunchedEffect(cur, paragraphs) {
        val viewport = list.layoutInfo.viewportSize.height
        list.animateScrollToItem(cur + 1, scrollOffset = -(viewport * 0.38f).toInt())
    }
    LazyColumn(modifier.fillMaxSize().verticalFade(64.dp), state = list) {
        item { Spacer(Modifier.height(24.dp)) }
        itemsIndexed(lines) { i, line ->
            val focus by animateFloatAsState(if (i == cur) 1f else 0f, tween(450), label = "prompterFocus")
            val dim = if (i < cur) 0.2f else 0.14f
            // Modifier.blur works from Android 12; below it a shadow-only glyph stands in for the blur.
            val style = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) TextStyle(color = Ar.white.copy(alpha = dim + (1 - dim) * focus))
                else TextStyle(color = Ar.white.copy(alpha = focus), shadow = Shadow(Ar.white.copy(alpha = (1 - focus) * dim * 1.6f), blurRadius = 14f))
            Text(
                line.text, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.2).sp,
                textAlign = TextAlign.Center, style = style,
                modifier = Modifier.fillMaxWidth().padding(top = if (line.opensParagraph) 22.dp else 12.dp, start = 8.dp, end = 8.dp)
                    .blur(((1 - focus) * 1.5f).dp, BlurredEdgeTreatment.Unbounded)
                    .clickable(interactionSource = null, indication = null) { PlayerController.seekText(line.start / TtsText.CHARS_PER_SECOND) },
            )
        }
        item { Spacer(Modifier.height(400.dp)) }
    }
}

private fun Modifier.verticalFade(fade: Dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val f = fade.toPx().coerceAtMost(size.height / 2)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = f), size = Size(size.width, f), blendMode = BlendMode.DstIn)
    drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - f, endY = size.height), topLeft = Offset(0f, size.height - f), size = Size(size.width, f), blendMode = BlendMode.DstIn)
}
