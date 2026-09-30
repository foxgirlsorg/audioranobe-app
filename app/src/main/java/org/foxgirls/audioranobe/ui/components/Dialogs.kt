package org.foxgirls.audioranobe.ui.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar

/** components/Modal: a centered card with a title row and close button. */
@Composable
fun ArModal(
    open: Boolean,
    onClose: () -> Unit,
    title: String? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = open
    if (!shown.currentState && !shown.targetState) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pop = rememberTransition(shown, label = "modal")
        val alpha by pop.animateFloat({ if (targetState) tween(260) else tween(160) }, label = "modalAlpha") { if (it) 1f else 0f }
        val lift by pop.animateDp({ if (targetState) tween(260, easing = Overshoot) else tween(160) }, label = "modalLift") { if (it) 0.dp else if (pop.targetState) 20.dp else 12.dp }
        val scale by pop.animateFloat({ if (targetState) tween(260, easing = Overshoot) else tween(160) }, label = "modalScale") { if (it) 1f else 0.97f }
        Box(Modifier.fillMaxWidth().padding(14.dp).imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha; translationY = lift.toPx(); scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(16.dp)).background(Ar.surfaceSolid).border(1.dp, Ar.borderStrong, RoundedCornerShape(16.dp)),
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(Ar.accent))
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title ?: "", color = Ar.white, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    IconBtn(Lucide.X, "Закрыть", onClose, size = 36.dp, iconSize = 18.dp)
                }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 620.dp).then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    content = content,
                )
            }
        }
    }
}

/** --ease-overshoot in globals.css. */
val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** components/ConfirmDialog */
@Composable
fun ConfirmDialog(
    open: Boolean,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    body: String,
    danger: Boolean = false,
    confirmLabel: String = "Подтвердить",
) {
    ArModal(open, onClose, title) {
        Text(body, color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onClose, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton(confirmLabel, { onConfirm(); onClose() }, kind = if (danger) ButtonKind.Danger else ButtonKind.Primary)
        }
    }
}

/** Bottom sheet wrapper with the site's dark surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArSheet(open: Boolean, onClose: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open) shown = true else if (shown) { state.hide(); shown = false } }
    if (!open && !shown) return
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        containerColor = Ar.surfaceSolid,
        contentColor = Ar.text,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).background(Ar.borderStrong, RoundedCornerShape(2.dp))) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (title != null) Text(title, color = Ar.white, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

data class SelectOption<T>(val value: T, val label: String, val hint: String? = null, val disabled: Boolean = false)

/** components/Select: a button with a dropdown menu anchored under it (above when there is no room). */
@Composable
fun <T> SelectMenu(
    value: T,
    options: List<SelectOption<T>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Не выбрано",
    label: String? = null,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == value }
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "selectChevron")
    var anchorWidth by remember { mutableIntStateOf(0) }
    val menu = remember { MutableTransitionState(false) }
    menu.targetState = open
    Column(modifier) {
        if (label != null) FieldLabel(label)
        Box {
            Row(
                Modifier.fillMaxWidth().onSizeChanged { anchorWidth = it.width }
                    .clip(RoundedCornerShape(if (small) 8.dp else 9.dp)).background(if (open) Ar.fill06 else Ar.fill04)
                    .border(1.dp, if (open) Ar.accent.copy(alpha = 0.45f) else Ar.border, RoundedCornerShape(if (small) 8.dp else 9.dp))
                    .clickable(enabled = enabled) { open = !open }
                    .padding(start = if (small) 11.dp else 14.dp, end = if (small) 8.dp else 12.dp, top = if (small) 6.dp else 10.dp, bottom = if (small) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.label ?: placeholder, color = if (selected != null) Ar.text else Ar.textMuted, fontSize = if (small) 12.sp else 14.sp, fontWeight = if (small) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Lucide.ChevronDown, null, tint = if (open) Ar.accent else Ar.textMuted, modifier = Modifier.size(14.dp).rotate(chevron))
            }
            if (menu.currentState || menu.targetState) SelectDropdown(menu, anchorWidth, options, value, { open = false; if (it != value) onChange(it) }, { open = false })
        }
    }
}

@Composable
private fun <T> SelectDropdown(menu: MutableTransitionState<Boolean>, anchorWidth: Int, options: List<SelectOption<T>>, value: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { 4.dp.roundToPx() }
    Popup(popupPositionProvider = remember(gap) { AnchorBelow(gap) }, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            menu,
            enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { -gap },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -gap },
        ) {
            Column(
                Modifier.widthIn(min = with(density) { anchorWidth.toDp() }).width(IntrinsicSize.Max).heightIn(max = 280.dp)
                    .shadow(18.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                    .background(Color(0xF7161618)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState()).padding(4.dp),
            ) {
                for (o in options) {
                    val on = o.value == value
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(if (on) Ar.accentSoft else Color.Transparent)
                            .clickable(enabled = !o.disabled) { onPick(o.value) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(o.label, color = if (o.disabled) Ar.textMuted else if (on) Ar.accent else Ar.textSecondary, fontSize = 13.sp, maxLines = 1, softWrap = false)
                            if (o.hint != null) Text(o.hint, color = Ar.textMuted, fontSize = 11.sp)
                        }
                        if (on) Icon(Lucide.Check, null, tint = Ar.accent, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/** Places a popup under its anchor, or above it when it would run off the bottom of the window. */
internal class AnchorBelow(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = if (below + popupContentSize.height > windowSize.height && above >= 0) above else below
        return IntOffset(anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0), y)
    }
}

/** Prompt for one text value (used for notes, reasons, URLs). */
@Composable
fun TextPromptDialog(
    open: Boolean,
    onClose: () -> Unit,
    title: String,
    initial: String = "",
    placeholder: String = "",
    hint: String? = null,
    maxLength: Int? = null,
    multiline: Boolean = true,
    submitLabel: String = "Сохранить",
    danger: Boolean = false,
    onSubmit: suspend (String) -> Unit,
) {
    if (!open) return
    var value by remember(initial) { mutableStateOf(initial) }
    val busy = rememberBusy()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ArModal(true, onClose, title) {
        if (hint != null) { Text(hint, color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp); Spacer(Modifier.height(10.dp)) }
        ArTextField(value, { value = it }, placeholder = placeholder, singleLine = !multiline, minLines = if (multiline) 4 else 1, maxLength = maxLength, showCounter = maxLength != null)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onClose, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton(submitLabel, {
                scope.launch {
                    busy.value = true
                    try { onSubmit(value); onClose() } catch (e: Exception) { org.foxgirls.audioranobe.ui.toast.toastError(e) } finally { busy.value = false }
                }
            }, kind = if (danger) ButtonKind.Danger else ButtonKind.Primary, busy = busy.value)
        }
    }
}

