package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar

private val RU = Locale("ru")

private fun label(epochSeconds: Long): String = SimpleDateFormat("d MMMM yyyy 'в' HH:mm", RU).format(Date(epochSeconds * 1000))

/** A field showing the chosen moment (Unix seconds) that opens a themed date + time picker; null means "not set". */
@Composable
fun DateTimeField(value: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Не ограничено") {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 46.dp).clip(shape).background(Ar.fill04).border(BorderStroke(1.dp, Ar.border), shape)
            .clickable { open = true }.padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Clock, null, tint = Ar.textMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(value?.let(::label) ?: placeholder, color = if (value != null) Ar.white else Ar.textMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value != null) IconBtn(Lucide.X, "Сбросить", { onChange(null) }, size = 38.dp, iconSize = 16.dp)
    }
    if (open) DateTimeDialog(value, { open = false }) { onChange(it); open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeDialog(initial: Long?, onClose: () -> Unit, onPick: (Long) -> Unit) {
    val start = remember { Calendar.getInstance().apply { if (initial != null) timeInMillis = initial * 1000 else { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0) } } }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = utcMidnight(start),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= utcMidnight(Calendar.getInstance())
        },
    )
    val timeState = rememberTimePickerState(start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), is24Hour = true)

    val dateColors = DatePickerDefaults.colors(
        containerColor = Color.Transparent,
        titleContentColor = Ar.textMuted,
        headlineContentColor = Ar.white,
        weekdayContentColor = Ar.textMuted,
        subheadContentColor = Ar.textSecondary,
        navigationContentColor = Ar.text,
        yearContentColor = Ar.text,
        currentYearContentColor = Ar.accent,
        selectedYearContentColor = Ar.accentOn,
        selectedYearContainerColor = Ar.accent,
        dayContentColor = Ar.text,
        disabledDayContentColor = Ar.textMuted.copy(alpha = 0.35f),
        selectedDayContentColor = Ar.accentOn,
        selectedDayContainerColor = Ar.accent,
        todayContentColor = Ar.accent,
        todayDateBorderColor = Ar.accent,
        dividerColor = Ar.border,
    )
    val timeColors = TimePickerDefaults.colors(
        clockDialColor = Ar.fill06,
        selectorColor = Ar.accent,
        containerColor = Color.Transparent,
        periodSelectorBorderColor = Ar.border,
        timeSelectorSelectedContainerColor = Ar.accentSoft,
        timeSelectorUnselectedContainerColor = Ar.fill06,
        timeSelectorSelectedContentColor = Ar.white,
        timeSelectorUnselectedContentColor = Ar.text,
    )

    ArModal(true, onClose, "Окончание опроса") {
        DatePicker(dateState, title = null, headline = null, showModeToggle = false, colors = dateColors)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ВРЕМЯ", color = Ar.textMuted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            TimeInput(timeState, colors = timeColors)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onClose, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton("Готово", kind = ButtonKind.Primary, enabled = dateState.selectedDateMillis != null, onClick = {
                val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateState.selectedDateMillis ?: return@ArButton }
                val local = Calendar.getInstance().apply {
                    set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (local.timeInMillis <= System.currentTimeMillis()) { org.foxgirls.audioranobe.ui.toast.toastError("Время окончания должно быть в будущем"); return@ArButton }
                onPick(local.timeInMillis / 1000)
            })
        }
    }
}

private fun utcMidnight(c: Calendar): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
    clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
}.timeInMillis
