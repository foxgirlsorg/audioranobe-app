package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.theme.InterFamily
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding

private val MENTION = Regex("(?<![\\wА-Яа-яЁё])@([A-Za-zА-Яа-яЁё0-9_]{3,30})")
private val SPOILER = Regex("\\|\\|([\\s\\S]+?)\\|\\|")
private val YOUTUBE = Regex("@\\[youtube]\\(\\s*([A-Za-z0-9_-]{6,})\\s*\\)")
private val SIZED_IMG = Regex("!\\[([^\\]]*)]\\(\\s*([^\\s\")]+)\\s+\"(\\d+)\\s*[x×]\\s*(?:(\\d+)|auto)\"\\s*\\)")

/**
 * Site markdown (components/Markdown): GFM plus @mentions, ||spoilers|| and @[youtube](id).
 * Spoilers render hidden; tapping one reveals it.
 */
@Composable
fun ArMarkdown(source: String, modifier: Modifier = Modifier, compact: Boolean = false, media: String? = null) {
    val nav = LocalNav.current
    var revealed by remember(source) { mutableStateOf(setOf<Int>()) }

    val prepared = remember(source, revealed, media) {
        var s = source
        var idx = 0
        s = SPOILER.replace(s) { m ->
            val i = idx++
            if (i in revealed) m.groupValues[1] else "[▒▒ спойлер ▒▒](spoiler:$i)"
        }
        s = MENTION.replace(s) { m -> "[@${m.groupValues[1]}](${Links.site}/user/${m.groupValues[1]})" }
        if (media == "image" || media == "both") s = SIZED_IMG.replace(s) { m -> "![${m.groupValues[1]}](${m.groupValues[2]})" }
        if (media == "video" || media == "both") s = YOUTUBE.replace(s) { m -> "\n\n[▶ Видео на YouTube](https://www.youtube.com/watch?v=${m.groupValues[1]})\n\n" }
        s
    }

    val handler = remember(nav) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (uri.startsWith("spoiler:")) {
                    uri.removePrefix("spoiler:").toIntOrNull()?.let { revealed = revealed + it }
                } else {
                    Links.open(nav, uri)
                }
            }
        }
    }

    val base = TextStyle(fontFamily = InterFamily, fontSize = if (compact) 14.sp else 15.sp, lineHeight = if (compact) 20.sp else 23.sp, color = Ar.textSecondary)
    CompositionLocalProvider(LocalUriHandler provides handler) {
        Markdown(
            content = prepared,
            modifier = modifier.fillMaxWidth().wrapContentHeight(),
            colors = markdownColor(
                text = Ar.textSecondary,
                codeBackground = Ar.fill06,
                inlineCodeBackground = Ar.fill08,
                dividerColor = Ar.border,
                tableBackground = Ar.fill04,
            ),
            typography = markdownTypography(
                h1 = base.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = Ar.white),
                h2 = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = Ar.white),
                h3 = base.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = Ar.white),
                h4 = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ar.white),
                h5 = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ar.white),
                h6 = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ar.textMuted),
                text = base,
                paragraph = base,
                ordered = base,
                bullet = base,
                list = base,
                code = base.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                inlineCode = base.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                quote = base.copy(fontStyle = FontStyle.Italic, color = Ar.textMuted),
                textLink = TextLinkStyles(style = SpanStyle(color = Ar.accent, fontFamily = InterFamily)),
                table = base,
            ),
            padding = markdownPadding(block = if (compact) 4.dp else 8.dp),
            imageTransformer = Coil2ImageTransformerImpl,
        )
    }
}

// ---------- editor (components/MarkdownEditor) ----------

private data class Tool(val key: String, val icon: ImageVector, val title: String, val before: String = "", val after: String = "", val prefix: String? = null, val ordered: Boolean = false)

private val TOOLS = listOf(
    Tool("bold", Lucide.Bold, "Полужирный", "**", "**"),
    Tool("italic", Lucide.Italic, "Курсив", "*", "*"),
    Tool("strike", Lucide.Strikethrough, "Зачёркнутый", "~~", "~~"),
    Tool("spoiler", Lucide.EyeOff, "Спойлер", "||", "||"),
    Tool("heading", Lucide.Heading, "Заголовок", prefix = "## "),
    Tool("quote", Lucide.Quote, "Цитата", prefix = "> "),
    Tool("code", Lucide.Code, "Код", "`", "`"),
    Tool("ul", Lucide.List, "Список", prefix = "- "),
    Tool("ol", Lucide.ListOrdered, "Нумерованный список", ordered = true),
)
private val SLIM_KEYS = setOf("bold", "italic", "strike", "spoiler", "quote")

private fun applyTool(t: Tool, v: TextFieldValue): TextFieldValue {
    val value = v.text
    val start = v.selection.min
    val end = v.selection.max
    val selected = value.substring(start, end)
    if (t.prefix == null && !t.ordered) {
        val b = t.before; val a = t.after
        if (start - b.length >= 0 && value.substring(start - b.length, start) == b && value.substring(end, minOf(value.length, end + a.length)) == a) {
            val nv = value.substring(0, start - b.length) + selected + value.substring(end + a.length)
            return TextFieldValue(nv, TextRange(start - b.length, end - b.length))
        }
        val nv = value.substring(0, start) + b + selected + a + value.substring(end)
        return TextFieldValue(nv, TextRange(start + b.length, start + b.length + selected.length))
    }
    val lineStart = value.lastIndexOf('\n', start - 1) + 1
    val lineEndIdx = value.indexOf('\n', end)
    val lineEnd = if (lineEndIdx == -1) value.length else lineEndIdx
    val lines = value.substring(lineStart, lineEnd).split("\n")
    val block = if (t.ordered) {
        val numbered = Regex("^\\d+\\.\\s")
        val all = lines.all { numbered.containsMatchIn(it) }
        (if (all) lines.map { it.replace(numbered, "") } else lines.mapIndexed { i, l -> "${i + 1}. ${l.replace(numbered, "")}" }).joinToString("\n")
    } else {
        val p = t.prefix!!
        val all = lines.all { it.startsWith(p) }
        (if (all) lines.map { it.removePrefix(p) } else lines.map { p + it }).joinToString("\n")
    }
    val nv = value.substring(0, lineStart) + block + value.substring(lineEnd)
    return TextFieldValue(nv, TextRange(lineStart, lineStart + block.length))
}

@Composable
fun MarkdownEditor(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLength: Int? = null,
    slim: Boolean = false,
    minLines: Int = if (slim) 3 else 8,
    media: String? = null,
    label: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    if (tfv.text != value) tfv = TextFieldValue(value, TextRange(value.length.coerceAtMost(tfv.selection.end)))
    var preview by remember { mutableStateOf(false) }
    var urlPrompt by remember { mutableStateOf<String?>(null) }

    fun set(v: TextFieldValue) {
        if (maxLength != null && v.text.length > maxLength) return
        tfv = v; onChange(v.text)
    }

    fun insert(text: String) {
        val s = tfv.selection.min; val e = tfv.selection.max
        set(TextFieldValue(tfv.text.substring(0, s) + text + tfv.text.substring(e), TextRange(s + text.length)))
    }

    val tools = if (slim) TOOLS.filter { it.key in SLIM_KEYS } else TOOLS
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth()) {
        if (label != null) FieldLabel(label)
        Column(Modifier.fillMaxWidth().clip(shape).background(Ar.fill04).border(1.dp, Ar.border, shape)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                for (t in tools) IconBtn(t.icon, t.title, { if (!preview) set(applyTool(t, tfv)) }, size = 32.dp, iconSize = 14.dp, enabled = !preview)
                if (!slim && (media == "image" || media == "both")) IconBtn(Lucide.Image, "Вставить изображение", { urlPrompt = "image" }, size = 32.dp, iconSize = 14.dp, enabled = !preview)
                if (!slim && (media == "video" || media == "both")) IconBtn(Lucide.Youtube, "Вставить видео", { urlPrompt = "video" }, size = 32.dp, iconSize = 14.dp, enabled = !preview)
                Spacer(Modifier.weight(1f))
                IconBtn(if (preview) Lucide.Pencil else Lucide.Eye, if (preview) "Правка" else "Предпросмотр", { preview = !preview }, size = 32.dp, iconSize = 14.dp, tint = if (preview) Ar.accent else Ar.textSecondary)
            }
            HairlineDivider()
            if (preview) {
                Box(Modifier.fillMaxWidth().padding(12.dp)) {
                    if (value.isBlank()) Text("Нечего показать — текст пуст.", color = Ar.textMuted, fontSize = 13.sp)
                    else ArMarkdown(value, compact = true, media = media)
                }
            } else {
                OutlinedTextField(
                    value = tfv,
                    onValueChange = { set(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = placeholder?.let { { Text(it, color = Ar.textMuted, fontSize = 14.sp) } },
                    minLines = minLines,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Ar.text, lineHeight = 20.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = arFieldColors().let { it },
                    shape = RoundedCornerShape(0.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Markdown · ||спойлер|| · @упоминание", color = Ar.textMuted, fontSize = 11.sp)
                if (maxLength != null) Text("${value.length}/$maxLength", color = if (value.length > maxLength) Ar.danger else Ar.textMuted, fontSize = 11.sp)
            }
        }
        if (footer != null) footer()
    }

    TextPromptDialog(
        open = urlPrompt != null,
        onClose = { urlPrompt = null },
        title = if (urlPrompt == "video") "Вставить видео с YouTube" else "Вставить изображение",
        placeholder = if (urlPrompt == "video") "https://www.youtube.com/watch?v=…" else "https://…/image.png",
        multiline = false,
        submitLabel = "Вставить",
    ) { url ->
        val u = url.trim()
        if (urlPrompt == "video") {
            val m = Regex("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/)|youtu\\.be/)([\\w-]{11})").find(u)
                ?: throw IllegalArgumentException("Не удалось распознать ссылку на YouTube")
            insert("\n\n@[youtube](${m.groupValues[1]})\n\n")
        } else {
            if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u)) throw IllegalArgumentException("Ссылка должна начинаться с http:// или https://")
            insert("![Описание]($u \"640xauto\")")
        }
    }
}

@Composable
fun MarkdownHint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.Info, null, tint = Ar.textMuted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Ar.textMuted, fontSize = 12.sp)
    }
}

@Composable
fun SpacerH(h: Int) = Spacer(Modifier.height(h.dp))
