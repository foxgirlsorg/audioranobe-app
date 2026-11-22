package org.foxgirls.audioranobe.ui.screens.editing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.TitleFull
import org.foxgirls.audioranobe.tts.TtsText
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArModal
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.components.LocalFlatUi
import androidx.compose.runtime.CompositionLocalProvider
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.arFieldColors
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.Chevron
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.Pill
import org.foxgirls.audioranobe.ui.components.Spinner
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
private data class TextChapter(
    val id: Int,
    val volume_id: Int,
    val volume_number: Double = 0.0,
    val volume_name: String = "",
    val number: Double = 0.0,
    val number_end: Double? = null,
    val name: String = "",
    val chars: Int = 0,
    val edited: Boolean = false,
    val enabled: Boolean = true,
    val removed: Boolean = false,
    val updated_at: Long = 0,
)

@Serializable
private data class TextBranchData(val enabled: Boolean = true, val chapters: List<TextChapter> = emptyList())

@Serializable
private data class ChapterTextBody(val text: String = "")

@Serializable
private data class StressJob(
    val chapter_id: Int,
    val status: String = "",
    val fixes: Int = 0,
    val error: String? = null,
    val checked_at: Long? = null,
)

@Serializable
private data class StressState(val configured: Boolean = false, val jobs: List<StressJob> = emptyList())

@Serializable
private data class StressCount(val queued: Int = 0, val cancelled: Int = 0)

private fun length(chars: Int): String = "≈ ${Fmt.duration(chars / TtsText.CHARS_PER_SECOND)}"
private fun grouped(n: Int): String = "%,d".format(n).replace(',', ' ')
private val DAY = SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru"))
private fun chapterLabel(c: TextChapter) = c.name.ifBlank { "Глава ${Fmt.chapterNumber(c.number, c.number_end)}" }
private val checkColors @Composable get() = CheckboxDefaults.colors(checkedColor = Ar.accent, uncheckedColor = Ar.textMuted)

private fun aiBusy(j: StressJob?): Boolean = j?.status == "queued" || j?.status == "running"
private fun aiChecked(c: TextChapter, j: StressJob?): Boolean {
    val at = j?.checked_at ?: return false
    return c.updated_at <= at
}
private fun aiNote(c: TextChapter, j: StressJob?): String = when {
    j == null -> ""
    j.status == "queued" -> "в очереди на проверку ИИ"
    j.status == "running" -> "ИИ проверяет…"
    j.status == "error" -> "ошибка проверки ИИ"
    j.checked_at == null -> ""
    aiChecked(c, j) -> "проверено ИИ, исправлений: ${j.fixes}"
    else -> "текст изменён после проверки ИИ"
}
private fun aiAction(c: TextChapter, j: StressJob?): String = when {
    j?.status == "queued" -> "В очереди на проверку ИИ"
    j?.status == "running" -> "ИИ проверяет главу. Нажмите, чтобы начать заново"
    aiChecked(c, j) -> "Проверить ударения ИИ ещё раз"
    else -> "Проверить ударения ИИ"
}

/**
 * components/TextBranch/TextBranchManager: the AudioRanobe AI branch of a title, its chapter texts read aloud on the device.
 * Branch switch, search and filters, selection with bulk actions, text editing, adding, importing, moving and volumes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextBranchManager(title: TitleFull, reload: suspend () -> Unit, header: @Composable ColumnScope.() -> Unit) {
    val loader = rememberLoader("texts", title.id, keepOnReload = true) { Api.get<TextBranchData>("/panel/titles/${title.id}/texts") }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var open by remember { mutableStateOf<Set<Int>?>(null) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var editing by remember { mutableStateOf<TextChapter?>(null) }
    var confirm by remember { mutableStateOf<Pair<List<Int>, String>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var editVolume by remember { mutableStateOf<VolumeOption?>(null) }
    var moving by remember { mutableStateOf<List<Int>?>(null) }
    var actions by remember { mutableStateOf(false) }
    var stressAsk by remember { mutableStateOf<List<Int>?>(null) }

    val canStress = LocalAuth.current.can("stress.run")
    val stressLoader = rememberLoader("stress", title.id, keepOnReload = true) {
        if (canStress) Api.get<StressState>("/panel/titles/${title.id}/stress") else StressState()
    }
    var stressPrev by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val stressState = stressLoader.data
    val stressConfigured = stressState?.configured == true
    val stressJobs = stressState?.jobs.orEmpty().associateBy { it.chapter_id }
    val stressActive = stressJobs.values.filter { it.status == "queued" || it.status == "running" }.map { it.chapter_id }.toSet()
    LaunchedEffect(stressActive) {
        val finished = stressPrev - stressActive
        stressPrev = stressActive
        if (finished.isNotEmpty()) loader.reload()
    }
    LaunchedEffect(stressActive.isNotEmpty()) {
        if (!canStress || stressActive.isEmpty()) return@LaunchedEffect
        while (true) { delay(10_000); stressLoader.reload() }
    }

    val data = when (val s = loader.state) {
        is Load.Err -> { EditScaffold(false, header = header) { Text(s.message, color = Ar.danger, fontSize = 13.sp) }; return }
        is Load.Loading -> { EditScaffold(false, header = header) { Spinner(Modifier.align(Alignment.CenterHorizontally).padding(24.dp)) }; return }
        is Load.Ok -> s.data
    }
    val refresh: suspend () -> Unit = { loader.reload(); reload() }
    val volumes = title.volumes.sortedBy { it.number }.map { VolumeOption(it.id, it.number, it.name) }
    fun nextNumber(volumeId: Int) = (data.chapters.filter { it.volume_id == volumeId }.maxOfOrNull { it.number } ?: 0.0).toInt() + 1

    fun run(ids: List<Int>, action: String) {
        if (ids.isEmpty()) return
        busy = true
        scope.launch {
            try {
                Api.post<Unit>("/panel/texts", buildJsonObject { put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }); put("action", action) })
                toast(when (action) { "remove" -> "Тексты удалены"; "resync" -> "Тексты обновлены из источника"; "enable" -> "Озвучка включена"; else -> "Озвучка отключена" })
                selected = emptySet()
                refresh()
            } catch (e: Exception) { toastError(e) }
            busy = false
        }
    }

    fun startStress(ids: List<Int>) {
        if (ids.isEmpty()) return
        scope.launch {
            try {
                val res = Api.post<StressCount>("/panel/stress", buildJsonObject { put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) })
                toast("В очередь поставлено глав: ${res.queued}")
                stressLoader.reload()
            } catch (e: Exception) { toastError(e) }
        }
    }

    fun cancelStress(ids: List<Int>) {
        if (ids.isEmpty()) return
        scope.launch {
            try {
                val res = Api.post<StressCount>("/panel/stress/cancel", buildJsonObject { put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) })
                toast("Отменено глав: ${res.cancelled}")
                selected = emptySet()
                stressLoader.reload()
            } catch (e: Exception) { toastError(e) }
        }
    }

    val live = data.chapters.filter { !it.removed }
    val checkedIds = live.filter { aiChecked(it, stressJobs[it.id]) }.map { it.id }.toSet()
    val q = query.trim().lowercase()
    val groups = data.chapters
        .filter { c -> filter != "edited" || c.edited }
        .filter { c -> filter != "off" || !c.enabled || c.removed }
        .filter { c -> q.isEmpty() || "${c.name} ${Fmt.chapterNumber(c.number, c.number_end)}".lowercase().contains(q) }
        .groupBy { it.volume_id }.values.sortedBy { it.first().volume_number }
    fun isOpen(volumeId: Int) = open?.contains(volumeId) ?: (groups.size <= 3 || groups.lastOrNull()?.first()?.volume_id == volumeId)
    val allIds = groups.flatten().map { it.id }
    val allOn = allIds.isNotEmpty() && allIds.all { it in selected }
    fun flip(ids: List<Int>, on: Boolean) { selected = if (on) selected + ids else selected - ids.toSet() }

    EditScaffold(selected.isNotEmpty(), { SelectionBar(selected.size, { selected = emptySet() }, { actions = true }) }, header) {
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        ArToggle(data.enabled, { next ->
            busy = true
            scope.launch {
                try {
                    Api.put<Unit>("/panel/titles/${title.id}/texts", buildJsonObject { put("enabled", next) })
                    toast(if (next) "ИИ-озвучка включена" else "ИИ-озвучка отключена")
                    refresh()
                } catch (e: Exception) { toastError(e) }
                busy = false
            }
        }, "ИИ-озвучка", "Главы с текстом читаются вслух прямо на устройстве слушателя.", enabled = !busy)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Stat("глав", live.size.toString())
            Stat("знаков", grouped(live.sumOf { it.chars }))
            Stat("на 1×", length(live.sumOf { it.chars }))
        }
    }

    FlowRow(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArButton("Добавить главу", { adding = true }, icon = Lucide.Plus, small = true, enabled = volumes.isNotEmpty())
        if (live.isEmpty()) ArButton("Импорт из файла", { importing = true }, icon = Lucide.Upload, small = true)
        ArButton("Обновить из источника", { confirm = data.chapters.map { it.id } to "resync" }, small = true, enabled = !busy && data.chapters.isNotEmpty())
        if (canStress && live.size > 1) IconBtn(
            Lucide.Sparkles, if (stressConfigured) "Проверить ударения ИИ: вся книга" else "ИИ не настроен",
            { stressAsk = live.map { it.id } }, size = 36.dp, iconSize = 17.dp,
            background = Ar.fill04, border = Ar.border, enabled = !busy && stressConfigured,
        )
    }

    ArTextField(query, { query = it }, placeholder = "Поиск по названию или номеру")
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Checkbox(allOn, { flip(allIds, it) }, enabled = allIds.isNotEmpty(), colors = checkColors)
        for ((key, label) in listOf("all" to "Все", "edited" to "Изменённые", "off" to "Отключённые")) Pill(label, active = filter == key, onClick = { filter = key })
    }

    if (groups.isEmpty()) Text(if (data.chapters.isEmpty()) "У тайтла пока нет текстов глав." else "Ничего не найдено.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
    for (g in groups) {
        val first = g.first()
        val ids = g.map { it.id }
        val on = ids.count { it in selected }
        val volumeLive = g.filter { !it.removed }
        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp), padding = PaddingValues(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(on == ids.size, { flip(ids, on != ids.size) }, colors = checkColors)
                Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable {
                    open = (open ?: groups.map { it.first().volume_id }.filter(::isOpen).toSet()).let { if (first.volume_id in it) it - first.volume_id else it + first.volume_id }
                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${title.volume_label} ${Fmt.trimNum(first.volume_number)}" + if (first.volume_name.isNotBlank()) " · ${first.volume_name}" else "", color = Ar.white, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        val checkedCount = volumeLive.count { aiChecked(it, stressJobs[it.id]) }
                        Text(
                            "${volumeLive.size} гл. · ${length(volumeLive.sumOf { it.chars })}" +
                                (if (checkedCount > 0) " · проверено ИИ: $checkedCount из ${volumeLive.size}" else "") +
                                (if (on in 1 until ids.size) " · выбрано $on" else ""),
                            color = Ar.textMuted, fontSize = 12.sp,
                        )
                    }
                    Chevron(isOpen(first.volume_id))
                }
                IconBtn(Lucide.Pencil, "Править ${title.volume_label.lowercase()}", { editVolume = VolumeOption(first.volume_id, first.volume_number, first.volume_name) }, size = 36.dp, iconSize = 15.dp)
                if (canStress && volumeLive.size > 1) IconBtn(
                    Lucide.Sparkles,
                    if (stressConfigured) "Проверить ударения ИИ: ${title.volume_label.lowercase()} ${Fmt.trimNum(first.volume_number)}" else "ИИ не настроен",
                    { stressAsk = volumeLive.map { it.id } }, size = 36.dp, iconSize = 15.dp,
                    enabled = !busy && stressConfigured,
                )
            }
            AnimatedVisibility(isOpen(first.volume_id), enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(top = 4.dp)) {
                    for (c in g) TextChapterRow(
                        c = c, checked = c.id in selected, busy = busy, onCheck = { flip(listOf(c.id), it) }, onEdit = { editing = c },
                        onRestore = { confirm = listOf(c.id) to "resync" }, stress = stressJobs[c.id],
                        canStress = canStress, stressReady = stressConfigured, onStress = { startStress(listOf(c.id)) },
                    )
                }
            }
        }
    }

    GlassPanel(Modifier.fillMaxWidth().padding(top = 4.dp), borderColor = Ar.danger.copy(alpha = 0.4f)) {
        Text("Удалить все тексты ветки", color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text("Ветка AudioRanobe AI исчезнет, аудио других веток не затрагивается.", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        ArButton("Удалить все тексты", { confirm = live.map { it.id } to "remove" }, kind = ButtonKind.Danger, small = true, enabled = !busy && live.isNotEmpty())
    }
    }

    CompositionLocalProvider(LocalFlatUi provides true) {
    ArSheet(actions, { actions = false }, "Выбрано: ${selected.size}") {
        val picked = selected.toList()
        fun act(fn: () -> Unit) { actions = false; fn() }
        MenuRow(Lucide.RefreshCw, "Обновить из источника", { act { confirm = picked to "resync" } })
        MenuRow(Lucide.Mic, "Включить озвучку", { act { run(picked, "enable") } })
        MenuRow(Lucide.MicOff, "Отключить озвучку", { act { run(picked, "disable") } })
        if (canStress) {
            MenuRow(Lucide.Sparkles, if (stressConfigured) "Проверить ударения ИИ" else "ИИ не настроен", { if (stressConfigured) act { stressAsk = picked.filter { id -> live.any { it.id == id } } } }, tint = if (stressConfigured) Ar.textSecondary else Ar.textMuted)
            val queued = picked.filter { aiBusy(stressJobs[it]) }
            if (queued.isNotEmpty()) MenuRow(Lucide.Ban, "Отменить проверку ИИ", { act { cancelStress(queued) } })
        }
        if (volumes.isNotEmpty()) MenuRow(Lucide.Move, "Переместить в ${title.volume_label.lowercase()}…", { act { moving = picked } })
        MenuRow(Lucide.Trash2, "Удалить тексты", { act { confirm = picked to "remove" } }, tint = Ar.danger)
    }

    ConfirmDialog(
        confirm != null, { confirm = null },
        onConfirm = { confirm?.let { (ids, action) -> run(ids, action) }; confirm = null },
        title = when (confirm?.second) {
            "remove" -> "Удалить тексты: ${confirm?.first?.size}?"
            else -> "Обновить из источника: ${confirm?.first?.size ?: 0}?"
        },
        body = when (confirm?.second) {
            "remove" -> "Тексты будут удалены, источник их заново не подтянет."
            else -> "Сохранённые тексты, включая правки, заменятся оригиналами из источника."
        },
        danger = true,
    )
    stressAsk?.let { ids ->
        StressConfirm(ids, checkedIds, onClose = { stressAsk = null }, onConfirm = { target -> stressAsk = null; startStress(target) })
    }
    if (adding) AddTextChapterDialog(title.id, volumes, title.volume_label, groups.lastOrNull()?.first()?.volume_id ?: volumes.last().id, ::nextNumber, { adding = false }, refresh)
    editVolume?.let { v -> EditVolumeDialog(v, title.volume_label, { editVolume = null }, refresh) }
    moving?.let { ids -> MoveChaptersDialog(title.id, ids, volumes, title.volume_label, { moving = null }) { selected = emptySet(); refresh() } }
    if (importing) BookImportDialog(title.id, title.volume_label, (title.volumes.maxOfOrNull { it.number } ?: 0.0) + 1, { importing = false }, refresh)

    val editable = data.chapters.filter { !it.removed }
    editing?.let { c ->
        val i = editable.indexOfFirst { it.id == c.id }
        TextEditor(
            c, title,
            canPrev = i > 0, canNext = i in 0 until editable.lastIndex,
            onNavigate = { d -> editing = editable.getOrNull(i + d) ?: c },
            onClose = { editing = null }, onSaved = refresh,
            onRemove = { editing = null; confirm = listOf(c.id) to "remove" },
            onResync = { editing = null; confirm = listOf(c.id) to "resync" },
        )
    }
    }
}

/** The sticky bar while chapters are selected: how many, clear, and the actions sheet. */
@Composable
private fun SelectionBar(count: Int, onClear: () -> Unit, onActions: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).clip(RoundedCornerShape(14.dp)).background(Ar.surfaceRaised)
            .border(1.dp, Ar.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Выбрано: $count", color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        ArButton("Снять", onClear, kind = ButtonKind.Ghost, small = true)
        ArButton("Действия", onActions, kind = ButtonKind.Primary, small = true)
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), color = Ar.textMuted, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.sp)
        Text(value, color = Ar.text, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun TextChapterRow(
    c: TextChapter, checked: Boolean, busy: Boolean, onCheck: (Boolean) -> Unit, onEdit: () -> Unit, onRestore: () -> Unit,
    stress: StressJob?, canStress: Boolean, stressReady: Boolean, onStress: () -> Unit,
) {
    val off = !c.enabled || c.removed
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (checked) Ar.accentSoft.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheck, colors = checkColors)
        Text(Fmt.chapterNumber(c.number, c.number_end), color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.width(30.dp))
        Column(Modifier.weight(1f)) {
            Text(chapterLabel(c), color = if (off) Ar.textMuted else Ar.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    c.removed -> "текст удалён"
                    else -> listOfNotNull(
                        if (!c.enabled) "отключена" else null,
                        if (c.edited && !aiChecked(c, stress)) "изменён вручную" else null,
                        if (c.updated_at > 0) DAY.format(Date(c.updated_at * 1000)) else null,
                        length(c.chars),
                    ).joinToString(" · ")
                },
                color = Ar.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!c.removed) aiNote(c, stress).takeIf { it.isNotEmpty() }?.let {
                Text(it, color = if (stress?.status == "error") Ar.danger else Ar.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (stress != null && stress.status == "error") stress.error?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Ar.danger, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (c.removed) ArButton("Вернуть", onRestore, kind = ButtonKind.Ghost, small = true, enabled = !busy)
        else {
            IconBtn(Lucide.Pencil, "Править текст", onEdit, size = 34.dp, iconSize = 15.dp)
            if (canStress) {
                if (aiBusy(stress)) Box(Modifier.size(34.dp).clickable(enabled = !busy && stressReady && stress?.status == "running", onClickLabel = aiAction(c, stress), onClick = onStress), contentAlignment = Alignment.Center) { Spinner(size = 15.dp) }
                else IconBtn(
                    Lucide.Sparkles, aiAction(c, stress), onStress, size = 34.dp, iconSize = 15.dp,
                    tint = if (aiChecked(c, stress)) Ar.accent else Ar.textSecondary, enabled = !busy && stressReady,
                )
            }
        }
    }
}

/** The AI stress check for chapters: skips the checked ones when asked, and says so when all of them are checked. */
@Composable
private fun StressConfirm(ids: List<Int>, checked: Set<Int>, onClose: () -> Unit, onConfirm: (List<Int>) -> Unit) {
    var skipChecked by remember(ids) { mutableStateOf(true) }
    val unchecked = ids.filter { it !in checked }
    val mixed = unchecked.isNotEmpty() && unchecked.size < ids.size
    val target = if (mixed && skipChecked) unchecked else ids
    ArModal(true, onClose, "Проверить ударения ИИ: глав ${target.size}") {
        Text(
            "ИИ проверит ударения и сохранит результат в тексте глав. Главы обрабатываются по одной, статус виден у каждой.",
            color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp,
        )
        if (mixed) Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { skipChecked = !skipChecked },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(skipChecked, { skipChecked = it }, colors = checkColors)
            Text("Пропустить уже проверенные: ${ids.size - unchecked.size}", color = Ar.text, fontSize = 14.sp)
        }
        else if (unchecked.isEmpty() && ids.isNotEmpty()) Text(
            "Все эти главы уже проверены и будут проверены заново.",
            color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onClose, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton("Проверить", { onConfirm(target) }, kind = ButtonKind.Primary, enabled = target.isNotEmpty())
        }
    }
}

/** components/TextBranch/TextEditorModal: the chapter text as the narrator will read it, with its stats and per-chapter switches. */
@Composable
private fun TextEditor(
    c: TextChapter, title: TitleFull, canPrev: Boolean, canNext: Boolean, onNavigate: (Int) -> Unit,
    onClose: () -> Unit, onSaved: suspend () -> Unit, onRemove: () -> Unit, onResync: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember(c.id) { mutableStateOf<TextFieldValue?>(null) }
    var original by remember(c.id) { mutableStateOf("") }
    var enabled by remember(c.id) { mutableStateOf(c.enabled) }
    var saving by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(c.id) {
        runCatching { Api.get<ChapterTextBody>("/panel/chapters/${c.id}/text").text }
            .onSuccess { text = TextFieldValue(it); original = it }.onFailure { toastError(it); onClose() }
    }
    val dirty = text != null && text?.text != original
    fun guard(fn: () -> Unit) { if (dirty) discard = fn else fn() }
    fun stressAtCaret() {
        val f = text ?: return
        val r = TtsText.toggleStress(f.text, f.selection.min, f.selection.max)
        if (r == null) toastError("Поставьте курсор после гласной") else text = TextFieldValue(r.first, TextRange(r.second, r.third))
    }

    ArModal(true, { if (!saving) guard(onClose) }, chapterLabel(c)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${title.volume_label} ${Fmt.trimNum(c.volume_number)} · глава ${Fmt.chapterNumber(c.number, c.number_end)} · AudioRanobe AI",
                color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            IconBtn(Lucide.ChevronLeft, "Предыдущая глава", { guard { onNavigate(-1) } }, size = 34.dp, iconSize = 16.dp, enabled = canPrev)
            IconBtn(Lucide.ChevronRight, "Следующая глава", { guard { onNavigate(1) } }, size = 34.dp, iconSize = 16.dp, enabled = canNext)
        }
        val field = text
        if (field == null) { Spinner(Modifier.align(Alignment.CenterHorizontally).padding(24.dp)); return@ArModal }
        val t = field.text
        val words = t.split(Regex("\\s+")).count { it.isNotBlank() }
        val paras = t.split('\n').count { it.isNotBlank() }
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("знаков", grouped(TtsText.length(t))); Stat("слов", grouped(words)); Stat("абзацев", paras.toString()); Stat("на 1×", length(TtsText.length(t)))
        }
        Text("Прозвучит первой строкой:", color = Ar.textMuted, fontSize = 12.sp)
        Text(
            TtsText.announcement(title.volumes.size, title.volume_label, c.volume_number, c.number, c.number_end, c.name),
            color = Ar.accent, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).clip(RoundedCornerShape(50)).background(Ar.accentSoft).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.End) {
            ArButton("Ударение", { stressAtCaret() }, kind = ButtonKind.Ghost, small = true, enabled = !saving)
        }
        OutlinedTextField(
            value = field,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 420.dp),
            singleLine = false,
            minLines = 10,
            textStyle = TextStyle(fontSize = 14.sp, color = Ar.text, fontFamily = FontFamily.Default),
            shape = RoundedCornerShape(9.dp),
            colors = arFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        ArToggle(enabled, { next ->
            enabled = next
            scope.launch {
                try {
                    Api.post<Unit>("/panel/texts", buildJsonObject { put("ids", buildJsonArray { add(JsonPrimitive(c.id)) }); put("action", if (next) "enable" else "disable") })
                    onSaved()
                } catch (e: Exception) { enabled = !next; toastError(e) }
            }
        }, "Озвучка включена", "Выключенная глава не звучит")
        if (c.edited) Text("Изменён вручную — источник его не перезапишет.", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArButton("Вернуть текст источника", { guard(onResync) }, small = true, enabled = !saving)
            ArButton("Удалить текст", { guard(onRemove) }, kind = ButtonKind.Danger, small = true, enabled = !saving)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (dirty) "Есть несохранённые изменения" else "", color = Ar.amber, fontSize = 12.sp, modifier = Modifier.weight(1f))
            ArButton("Отмена", { guard(onClose) }, kind = ButtonKind.Ghost, small = true, enabled = !saving)
            ArButton(if (saving) "Сохраняем…" else "Сохранить", {
                saving = true
                scope.launch {
                    try {
                        Api.put<Unit>("/chapters/${c.id}/text", buildJsonObject { put("text", t) })
                        toast("Текст сохранён")
                        original = t
                        onSaved()
                    } catch (e: Exception) { toastError(e) }
                    saving = false
                }
            }, kind = ButtonKind.Primary, small = true, busy = saving, enabled = dirty && t.isNotBlank())
        }
    }
    ConfirmDialog(discard != null, { discard = null }, onConfirm = { discard?.invoke(); discard = null }, title = "Закрыть без сохранения?", body = "Несохранённые правки текста пропадут.", danger = true)
}
