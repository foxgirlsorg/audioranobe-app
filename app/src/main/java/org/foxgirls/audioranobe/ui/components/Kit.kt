package org.foxgirls.audioranobe.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar

// ---------- typography helpers (globals.css: .eyebrow, .section-title) ----------

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Ar.textMuted, bar: Boolean = false, icon: ImageVector? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (bar) {
            Box(Modifier.width(18.dp).height(2.dp).background(Ar.accent, RoundedCornerShape(1.dp)))
            Spacer(Modifier.width(8.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp, lineHeight = 14.sp)
    }
}

@Composable
fun SectionTitle(title: String, accent: String? = null, modifier: Modifier = Modifier, size: Int = 24) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(title.uppercase(), color = Ar.white, fontSize = size.sp, fontWeight = FontWeight.Light, letterSpacing = (size * 0.08).sp, lineHeight = (size * 1.2).sp)
        if (accent != null) {
            Spacer(Modifier.width(8.dp))
            Text(accent.uppercase(), color = Ar.accent, fontSize = size.sp, fontWeight = FontWeight.Normal, letterSpacing = (size * 0.08).sp, lineHeight = (size * 1.2).sp)
        }
    }
}

/** components/Section: eyebrow + title, then content. */
@Composable
fun Section(title: String, accent: String? = null, eyebrow: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (eyebrow != null) Eyebrow(eyebrow, bar = true)
        SectionTitle(title, accent, Modifier.padding(top = 6.dp, bottom = 14.dp), size = 22)
        content()
    }
}

@Composable
fun PageHeader(eyebrow: String, title: String, accent: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(4.dp))
        SectionTitle(title, accent, size = 26)
    }
}

// ---------- surfaces ----------

/** .glass-panel */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(14.dp),
    shape: Shape = RoundedCornerShape(Ar.radius),
    background: Color = Ar.surfaceSolid.copy(alpha = 0.7f),
    borderColor: Color = Ar.border,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.clip(shape).background(background, shape).border(1.dp, borderColor, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

@Composable
fun AccentDivider(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(1.dp).alpha(0.7f)
            .background(Brush.horizontalGradient(listOf(Ar.accent, Ar.accent.copy(alpha = 0.2f), Color.Transparent))),
    )
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ar.border))
}

// ---------- buttons (.btn, .btn-primary, .btn-ghost, .btn-danger) ----------

enum class ButtonKind { Default, Primary, Ghost, Danger }

@Composable
fun ArButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Default,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    fullWidth: Boolean = false,
    small: Boolean = false,
) {
    val bg = when (kind) {
        ButtonKind.Default -> Ar.fill04
        ButtonKind.Primary -> Ar.accent.copy(alpha = 0.12f)
        ButtonKind.Ghost -> Color.Transparent
        ButtonKind.Danger -> Ar.danger.copy(alpha = 0.12f)
    }
    val border = when (kind) {
        ButtonKind.Default -> Ar.border
        ButtonKind.Primary -> Ar.accent.copy(alpha = 0.5f)
        ButtonKind.Ghost -> Color.Transparent
        ButtonKind.Danger -> Ar.danger.copy(alpha = 0.55f)
    }
    val fg = when (kind) {
        ButtonKind.Ghost -> Ar.textSecondary
        else -> Ar.text
    }
    val shape = RoundedCornerShape(Ar.radiusSm)
    val active = enabled && !busy
    Row(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .alpha(if (active) 1f else 0.4f)
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(enabled = active, onClick = onClick)
            .defaultMinSize(minHeight = if (small) 34.dp else 44.dp)
            .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 6.dp else 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Ar.accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round icon-only button (.iconBtn). */
@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ar.textSecondary,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    background: Color = Color.Transparent,
    border: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(background, CircleShape)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** .pill / .pill-active */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, active: Boolean = false, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val border by animateColorAsState(if (active) Ar.borderHover else Ar.border, label = "pillBorder")
    val fg by animateColorAsState(if (active) Ar.accentHover else Ar.textSecondary, label = "pillFg")
    val bg = if (active) Ar.accentSoft else Color.White.copy(alpha = 0.03f)
    var m = modifier.clip(CircleShape).background(bg, CircleShape).border(1.dp, border, CircleShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.heightIn(min = 32.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text.uppercase(), color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp, maxLines = 1)
    }
}

/** .badge — small accent chip */
@Composable
fun AccentBadge(text: String, modifier: Modifier = Modifier, color: Color = Ar.accent, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.12f), CircleShape).border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    }
}

/** Count bubble on dock tabs / menu rows. */
@Composable
fun CountBubble(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier.defaultMinSize(minWidth = 16.dp, minHeight = 16.dp).background(Ar.accent, CircleShape).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else count.toString(), color = Color(0xFF4A1B0C), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, lineHeight = 10.sp)
    }
}

// ---------- status badges (components/StatusBadge, AiBadge, VerifiedBadge) ----------

private val statusTone = mapOf(
    "pending" to Ar.amber, "approved" to Ar.ok, "rejected" to Ar.danger, "queued" to Ar.blue, "processing" to Ar.blue,
    "ready" to Ar.ok, "error" to Ar.danger, "none" to Ar.textMuted, "done" to Ar.ok, "open" to Ar.amber, "resolved" to Ar.ok,
    "dismissed" to Ar.textMuted, "ongoing" to Ar.blue, "completed" to Ar.ok, "abandoned" to Ar.danger, "frozen" to Ar.textMuted,
    "running" to Ar.blue, "success" to Ar.ok, "failed" to Ar.danger, "online" to Ar.ok, "offline" to Ar.textMuted, "idle" to Ar.blue, "working" to Ar.ok,
)
private val statusLabel = mapOf(
    "pending" to "на проверке", "approved" to "одобрено", "rejected" to "отклонено", "queued" to "в очереди", "processing" to "обработка",
    "ready" to "готово", "error" to "ошибка", "none" to "нет", "done" to "готово", "open" to "открыто", "resolved" to "решено",
    "dismissed" to "отклонено", "ongoing" to "продолжается", "completed" to "завершён", "abandoned" to "заброшен", "frozen" to "заморожен",
    "running" to "выполняется", "success" to "успешно", "failed" to "ошибка", "online" to "в сети", "offline" to "не в сети", "idle" to "ожидает", "working" to "работает",
)

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    if (status == "restricted") return
    val tone = statusTone[status] ?: Ar.textMuted
    Box(
        modifier.clip(CircleShape).background(tone.copy(alpha = 0.14f)).border(1.dp, tone.copy(alpha = 0.4f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text((statusLabel[status] ?: status.replace('_', ' ')).uppercase(), color = tone, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 12.sp)
    }
}

@Composable
fun AiBadge(modifier: Modifier = Modifier, label: String = "AI") {
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xE6202024)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(label, color = Ar.white, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, lineHeight = 15.sp)
    }
}

@Composable
fun NsfwBadge(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xE6B42828)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text("18+", color = Ar.white, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, lineHeight = 15.sp)
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 15.dp) {
    Icon(Lucide.CheckBold, "Личность подтверждена администрацией", tint = Ar.accent, modifier = modifier.size(size))
}

// ---------- form controls ----------

@Composable
fun ArTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    hint: String? = null,
    error: String? = null,
    maxLength: Int? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    password: Boolean = false,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onImeAction: (() -> Unit)? = null,
    showCounter: Boolean = false,
    compact: Boolean = false,
) {
    Column(modifier) {
        if (label != null) FieldLabel(label)
        if (compact) {
            val colors = arFieldColors()
            val interaction = remember { MutableInteractionSource() }
            val transformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
            BasicTextField(
                value = value,
                onValueChange = { v -> if (maxLength == null || v.length <= maxLength) onValueChange(v) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Ar.text),
                cursorBrush = SolidColor(colors.cursorColor),
                interactionSource = interaction,
                decorationBox = { inner ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value, innerTextField = inner, enabled = enabled, singleLine = singleLine,
                        visualTransformation = transformation, interactionSource = interaction, isError = error != null,
                        placeholder = placeholder?.let { { Text(it, color = Ar.textMuted, fontSize = 14.sp) } },
                        leadingIcon = leading?.let { { Icon(it, null, tint = Ar.textMuted, modifier = Modifier.size(16.dp)) } },
                        trailingIcon = trailing, colors = colors,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        container = { OutlinedTextFieldDefaults.Container(enabled, error != null, interaction, colors = colors, shape = RoundedCornerShape(9.dp)) },
                    )
                },
            )
        } else OutlinedTextField(
            value = value,
            onValueChange = { v -> if (maxLength == null || v.length <= maxLength) onValueChange(v) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = placeholder?.let { { Text(it, color = Ar.textMuted, fontSize = 14.sp) } },
            leadingIcon = leading?.let { { Icon(it, null, tint = Ar.textMuted, modifier = Modifier.size(16.dp)) } },
            trailingIcon = trailing,
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Ar.text),
            shape = RoundedCornerShape(9.dp),
            colors = arFieldColors(),
        )
        if (error != null) {
            Text(error, color = Ar.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        } else if (hint != null || (showCounter && maxLength != null)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (hint != null) Text(hint, color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                if (showCounter && maxLength != null) Text("${value.length}/$maxLength", color = Ar.textMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun arFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ar.text,
    unfocusedTextColor = Ar.text,
    disabledTextColor = Ar.textMuted,
    cursorColor = Ar.accent,
    focusedBorderColor = Ar.accent.copy(alpha = 0.45f),
    unfocusedBorderColor = Ar.border,
    disabledBorderColor = Ar.border,
    errorBorderColor = Ar.danger.copy(alpha = 0.6f),
    focusedContainerColor = Ar.fill06,
    unfocusedContainerColor = Ar.fill04,
    disabledContainerColor = Ar.fill04,
    errorContainerColor = Ar.fill04,
    focusedPlaceholderColor = Ar.textMuted,
    unfocusedPlaceholderColor = Ar.textMuted,
)

@Composable
fun FieldLabel(text: String, optional: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = Ar.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        if (optional) Text("  необязательно", color = Ar.textMuted, fontSize = 11.sp)
    }
}

/** components/Toggle */
@Composable
fun ArToggle(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    label: String? = null,
    hint: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = checked, onCheckedChange = { if (enabled) onChange(it) }, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ar.white, checkedTrackColor = Ar.accent, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Ar.textSecondary, uncheckedTrackColor = Ar.fill08, uncheckedBorderColor = Ar.borderStrong,
            ),
        )
        if (label != null) {
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Ar.text, fontSize = 14.sp, lineHeight = 18.sp)
                if (hint != null) Text(hint, color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

// ---------- tabs (components/Tabs) ----------

data class TabItem(val key: String, val label: String, val count: Int? = null, val accent: Boolean = false)

enum class TabsVariant { Pill, Underline, Square }

@Composable
fun ArTabs(
    tabs: List<TabItem>,
    active: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: TabsVariant = TabsVariant.Pill,
    scrollable: Boolean = true,
) {
    when (variant) {
        TabsVariant.Underline -> Column(modifier.fillMaxWidth()) {
            val scroll = rememberScrollState()
            val bounds = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
            val inset = with(LocalDensity.current) { 12.dp.roundToPx() }
            val indicatorX = remember { Animatable(0f) }
            val indicatorW = remember { Animatable(0f) }
            val target = bounds[active]
            LaunchedEffect(target) {
                val (x, w) = target ?: return@LaunchedEffect
                val nx = (x + inset).toFloat()
                val nw = (w - inset * 2).toFloat()
                if (indicatorW.value == 0f) { indicatorX.snapTo(nx); indicatorW.snapTo(nw) }
                else { launch { indicatorX.animateTo(nx, tween(180)) }; indicatorW.animateTo(nw, tween(180)) }
            }
            Box(Modifier.fillMaxWidth().then(if (scrollable) Modifier.edgeFade(scroll).horizontalScroll(scroll) else Modifier)) {
                Row(if (scrollable) Modifier else Modifier.fillMaxWidth()) {
                    for (t in tabs) {
                        val on = t.key == active
                        val fg = if (on) Ar.white else if (t.accent) Ar.accent else Ar.textMuted
                        Row(
                            Modifier.then(if (!scrollable) Modifier.weight(1f) else Modifier)
                                .onPlaced { bounds[t.key] = it.positionInParent().x.roundToInt() to it.size.width }
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(t.key) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (t.count != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(t.count.toString(), color = if (on || t.accent) Ar.accent else Ar.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (target != null) Box(
                    Modifier.align(Alignment.BottomStart).offset { IntOffset(indicatorX.value.roundToInt(), 0) }
                        .width(with(LocalDensity.current) { indicatorW.value.toDp() }).height(2.dp).background(Ar.accent, RoundedCornerShape(1.dp)),
                )
            }
            HairlineDivider()
        }
        TabsVariant.Square -> Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ar.fill04).border(1.dp, Ar.border, RoundedCornerShape(10.dp)).padding(3.dp)) {
            for (t in tabs) {
                val on = t.key == active
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) Ar.accentSoft else Color.Transparent)
                        .clickable { onChange(t.key) }.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.label, color = if (on) Ar.accentHover else Ar.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (t.count != null) Text("  ${t.count}", color = Ar.textMuted, fontSize = 11.sp)
                }
            }
        }
        TabsVariant.Pill -> Row(
            modifier.then(if (scrollable) rememberScrollState().let { Modifier.edgeFade(it).horizontalScroll(it) } else Modifier)
                .clip(CircleShape).background(Color.White.copy(alpha = 0.035f)).border(1.dp, Ar.border, CircleShape).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (t in tabs) {
                val on = t.key == active
                Row(
                    Modifier.clip(CircleShape).background(if (on) Ar.accentSoft else Color.Transparent).clickable { onChange(t.key) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.label, color = if (on) Ar.accentHover else if (t.accent) Ar.accent else Ar.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (t.count != null) Text("  ${t.count}", color = if (on) Ar.accentHover.copy(alpha = 0.8f) else Ar.textMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------- states ----------

@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 26.dp) {
    CircularProgressIndicator(modifier.size(size), color = Ar.accent, trackColor = Color.White.copy(alpha = 0.12f), strokeWidth = 2.dp)
}

@Composable
fun CenterSpinner(modifier: Modifier = Modifier, minHeight: Dp = 160.dp) {
    Box(modifier.fillMaxWidth().heightIn(min = minHeight), contentAlignment = Alignment.Center) { Spinner(size = 34.dp) }
}

/** components/EmptyState */
@Composable
fun EmptyState(title: String, body: String? = null, icon: ImageVector? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Box(Modifier.size(56.dp).background(Ar.fill04, CircleShape).border(1.dp, Ar.border, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Ar.textMuted, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(title, color = Ar.white, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, color = Ar.textMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, title: String = "Не удалось загрузить", modifier: Modifier = Modifier) {
    val online by org.foxgirls.audioranobe.offline.OfflineStore.online.collectAsState()
    if (onRetry != null) {
        var wasOffline by remember { mutableStateOf(!online) }
        LaunchedEffect(online) { if (!online) wasOffline = true else if (wasOffline) { wasOffline = false; onRetry() } }
    }
    if (!online) {
        val nav = org.foxgirls.audioranobe.ui.nav.LocalNav.current
        EmptyState("Нет подключения", "Без интернета доступны только скачанные книги.", Lucide.WifiOff, modifier) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArButton("Загрузки", { nav.tab(org.foxgirls.audioranobe.ui.nav.Routes.OFFLINE) }, kind = ButtonKind.Primary, icon = Lucide.Download)
                if (onRetry != null) ArButton("Повторить", onRetry, icon = Lucide.RefreshCw)
            }
        }
        return
    }
    EmptyState(title, message, Lucide.TriangleAlert, modifier) {
        if (onRetry != null) ArButton("Попробовать ещё раз", onRetry, icon = Lucide.RefreshCw)
    }
}

/** .back-link */
@Composable
fun BackLink(text: String = "Назад", onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.03f)).border(1.dp, Ar.border, CircleShape).clickable(onClick = onClick)
            .height(32.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.ArrowLeft, null, tint = Ar.textMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(text.uppercase(), color = Ar.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
    }
}

/** Skeleton placeholder block. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.background(Ar.surfaceRaised, shape))
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Ar.text, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(key, color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, color = if (onClick != null) Ar.accentHover else valueColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/** Menu-style row: icon + label + optional trailing. */
@Composable
fun MenuRow(icon: ImageVector?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int = 0, tint: Color = Ar.textSecondary, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, color = Ar.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (count > 0) CountBubble(count)
        trailing?.invoke(this)
    }
}

@Composable
fun BoxScope.TopRightBadges(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.align(Alignment.TopEnd).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

@Composable
fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, color: Color = Ar.accent, track: Color = Ar.fill08) {
    val f by animateDpAsState(0.dp, label = "noop")
    @Suppress("UNUSED_VARIABLE") val unused = f
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(color, CircleShape))
    }
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier, size: Int = 13, color: Color = Ar.textMuted, style: TextStyle = LocalTextStyle.current) {
    Text(text, modifier = modifier, color = color, fontSize = size.sp, lineHeight = (size * 1.45).sp, style = style)
}

@Composable
fun WithContentColor(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides color, content = content)
}

@Composable
fun OutlineChip(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = Ar.textSecondary) {
    var m = modifier.clip(RoundedCornerShape(6.dp)).border(BorderStroke(1.dp, Ar.border), RoundedCornerShape(6.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Text(text, color = color, fontSize = 12.sp, modifier = m.padding(horizontal = 8.dp, vertical = 4.dp))
}

/**
 * ScrollRail / Tabs .scrollFade: fades a horizontal scroller's edge on whichever side still has content
 * to scroll to; each fade grows in over 0.2 s as that side becomes scrollable.
 */
@Composable
fun Modifier.edgeFade(canStart: Boolean, canEnd: Boolean, fade: Dp): Modifier {
    val start by animateDpAsState(if (canStart) fade else 0.dp, tween(200), label = "edgeFadeStart")
    val end by animateDpAsState(if (canEnd) fade else 0.dp, tween(200), label = "edgeFadeEnd")
    return this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val w = size.width
        val s = start.toPx().coerceAtMost(w / 2)
        val e = end.toPx().coerceAtMost(w / 2)
        if (s > 0f) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = s), size = Size(s, size.height), blendMode = BlendMode.DstIn)
        if (e > 0f) drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = w - e, endX = w), topLeft = Offset(w - e, 0f), size = Size(e, size.height), blendMode = BlendMode.DstIn)
    }
}

@Composable
fun Modifier.edgeFade(state: LazyListState, fade: Dp = 88.dp): Modifier = edgeFade(state.canScrollBackward, state.canScrollForward, fade)

@Composable
fun Modifier.edgeFade(state: ScrollState, fade: Dp = 32.dp): Modifier = edgeFade(state.canScrollBackward, state.canScrollForward, fade)

/** One-shot entrance like the site's `…In` keyframes: fades in while rising [rise] (and growing from [fromScale]). */
@Composable
fun Modifier.enterRise(rise: Dp = 8.dp, durationMs: Int = 220, fromScale: Float = 1f): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(durationMs)) }
    return graphicsLayer {
        alpha = p.value
        translationY = (1 - p.value) * rise.toPx()
        val sc = fromScale + (1 - fromScale) * p.value
        scaleX = sc; scaleY = sc
    }
}

/** Collapsible / clamped text mask: the last [fraction] of the height fades out. */
fun Modifier.bottomFade(fraction: Float): Modifier = this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    val h = size.height * fraction
    drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - h, endY = size.height), topLeft = Offset(0f, size.height - h), size = Size(size.width, h), blendMode = BlendMode.DstIn)
}

/** Accordion chevron: turns over (and takes [openTint]) as its section opens, 0.3 s like the site's .chev. */
@Composable
fun Chevron(open: Boolean, size: Dp = 16.dp, tint: Color = Ar.textMuted, openTint: Color = Ar.accent) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(300, easing = FastOutSlowInEasing), label = "chevron")
    val color by animateColorAsState(if (open) openTint else tint, tween(300), label = "chevronTint")
    Icon(Lucide.ChevronDown, null, tint = color, modifier = Modifier.size(size).rotate(turn))
}

/** components/PlayPauseIcon: the two glyphs cross-fade and scale over 120 ms; play is nudged right to look centered. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp, nudge: Dp = size / 10) {
    AnimatedContent(playing, transitionSpec = { (scaleIn(tween(120), 0.6f) + fadeIn(tween(120))) togetherWith (scaleOut(tween(120), 0.6f) + fadeOut(tween(120))) }, label = "playPause") { p ->
        Icon(if (p) Lucide.Pause else Lucide.Play, if (p) "Пауза" else "Воспроизвести", tint = tint, modifier = Modifier.size(size).padding(start = if (p) 0.dp else nudge))
    }
}
