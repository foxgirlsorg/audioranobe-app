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
import androidx.compose.ui.Modifier
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
fun TtsReader(paragraphs: List<List<String>>, position: Double, modifier: Modifier = Modifier) {
    // Characters before each paragraph / sentence, on the same scale the player counts its position in.
    val starts = remember(paragraphs) {
        var chars = 0
        paragraphs.map { p -> p.map { s -> chars.also { chars += s.length } } }
    }
    val chars = position * TtsText.CHARS_PER_SECOND
    val current = remember(starts, chars.toInt() / 20) {
        var para = 0
        var sent = 0
        for (pi in starts.indices) for (si in starts[pi].indices) if (starts[pi][si] <= chars) { para = pi; sent = si }
        para to sent
    }
    val list = rememberLazyListState()
    LaunchedEffect(current.first, paragraphs) { list.animateScrollToItem(current.first + 1, scrollOffset = -40) }

    LazyColumn(modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
