package org.foxgirls.audioranobe.ui.screens.editing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.ChapterRow
import org.foxgirls.audioranobe.data.JobsPage
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.TitleFull
import org.foxgirls.audioranobe.data.TitleVersion
import org.foxgirls.audioranobe.data.Volume
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArModal
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.FieldLabel
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.components.Pill
import org.foxgirls.audioranobe.ui.components.ProgressTrack
import org.foxgirls.audioranobe.ui.components.SelectMenu
import org.foxgirls.audioranobe.ui.components.SelectOption
import org.foxgirls.audioranobe.ui.components.Spinner
import org.foxgirls.audioranobe.ui.components.StatusBadge
import org.foxgirls.audioranobe.ui.components.rememberFilePicker
import org.foxgirls.audioranobe.ui.components.rememberMultiFilePicker
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.screens.catalog.Pagination
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val JOBS_PER_PAGE = 30

private fun liveChapters(v: Volume) = v.chapters.filter { !it.is_deleted }
private fun nextNumberIn(v: Volume): Double = Math.floor(maxOf(0.0, liveChapters(v).maxOfOrNull { it.number_end ?: it.number } ?: 0.0)) + 1
private fun fmtNum(n: Double) = Fmt.chapterNumber(n, null)
private fun naturalKey(s: String) = Regex("\\d+|\\D+").findAll(s.lowercase()).map { m -> m.value.toBigIntegerOrNull()?.let { "0" + it.toString().padStart(12, '0') } ?: m.value }.joinToString("")

@Serializable private data class ChapterCreated(val id: Int = 0, val mod_status: String = "")
@Serializable private data class CountResult(val count: Int = 0)
@Serializable private data class UpdatedResult(val updated: Int = 0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterNarratorPicker(all: List<org.foxgirls.audioranobe.data.NarratorCard>, value: List<Int>, onChange: (List<Int>) -> Unit, label: String? = null) {
    if (all.isEmpty()) return
    if (label != null) FieldLabel(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        all.forEach { n -> Pill(n.name, active = n.id in value, onClick = { onChange(if (n.id in value) value - n.id else value + n.id) }) }
    }
}

/** components/TitleContentManager: volumes, chapters, audio uploads, versions, bulk actions and conversion jobs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleContentManager(title: TitleFull, reload: suspend () -> Unit) {
    val auth = LocalAuth.current
    val context = LocalContext.current
    val isMod = auth.isMod
    val isAdmin = auth.can("chapters.edit")
    val canManageVersions = auth.can("versions.manage")
    val scope = rememberCoroutineScope()
    val titleId = title.id

    var currentVersion by remember(titleId) { mutableStateOf(0) }
    var versionForm by remember { mutableStateOf<String?>(null) }
    var versionName by remember { mutableStateOf("") }
    var versionBusy by remember { mutableStateOf(false) }
    var versionToDelete by remember { mutableStateOf<TitleVersion?>(null) }
    var openVolumes by remember(titleId) { mutableStateOf<Set<Int>>(emptySet()) }
    var volumeForm by remember { mutableStateOf<Volume?>(null) }
    var showAddVolume by remember { mutableStateOf(false) }
    var volumeToDelete by remember { mutableStateOf<Volume?>(null) }
    var addChapterVol by remember { mutableStateOf<Volume?>(null) }
    var editingChapter by remember { mutableStateOf<ChapterRow?>(null) }
    var chapterToDelete by remember { mutableStateOf<ChapterRow?>(null) }
    var chapterToPurge by remember { mutableStateOf<ChapterRow?>(null) }
    var chapterMenu by remember { mutableStateOf<ChapterRow?>(null) }
    var uploads by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
    var altUploads by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var selected by remember(titleId, currentVersion) { mutableStateOf<List<Int>>(emptyList()) }
    var bulkBusy by remember { mutableStateOf(false) }
    var bulkEditOpen by remember { mutableStateOf(false) }
    var selectionAction by remember { mutableStateOf<String?>(null) }
    var bulkReview by remember { mutableStateOf<Triple<String, Volume?, List<Int>?>?>(null) }
    var jobs by remember { mutableStateOf<JobsPage?>(null) }
    var jobsPage by remember { mutableStateOf(1) }

    suspend fun loadJobs() { runCatching { jobs = Api.get<JobsPage>("/panel/titles/$titleId/jobs", mapOf("page" to jobsPage, "per_page" to JOBS_PER_PAGE)) } }
    LaunchedEffect(titleId, jobsPage) { loadJobs() }
    val activeJobs = jobs?.active ?: 0
    LaunchedEffect(activeJobs) {
        if (activeJobs == 0) return@LaunchedEffect
        while (true) {
            delay(5000)
            val before = jobs?.active ?: 0
            loadJobs()
            if ((jobs?.active ?: 0) < before) reload()
        }
    }
    LaunchedEffect(title.versions) { if (currentVersion != 0 && title.versions.none { it.id == currentVersion }) currentVersion = 0 }

    val isAlt = currentVersion != 0
    val altByKey = remember(title, currentVersion) {
        if (!isAlt) emptyMap() else (title.alt_chapters[currentVersion.toString()] ?: emptyList()).filter { !it.is_deleted }.associateBy { "${it.volume_id}:${it.number}" }
    }
    fun altSlot(volumeId: Int, number: Double) = altByKey["$volumeId:$number"]
    fun visibleChapters(v: Volume) = if (isAdmin) v.chapters else v.chapters.filter { !it.is_deleted }
    fun pendingIn(v: Volume) = v.chapters.count { it.mod_status == "pending" && !it.is_deleted }
    val pendingTotal = if (isAlt) 0 else title.volumes.sumOf { pendingIn(it) }
    val allChapters = remember(title, currentVersion) { if (isAlt) title.alt_chapters[currentVersion.toString()] ?: emptyList() else title.volumes.flatMap { it.chapters } }
    LaunchedEffect(allChapters) { selected = selected.filter { id -> allChapters.any { it.id == id } } }

    // Audio pickers: one for "replace / upload audio of chapter X", one for "upload alt slot".
    var uploadTarget by remember { mutableStateOf<Int?>(null) }
    var altTarget by remember { mutableStateOf<Triple<Int, Double, List<Int>>?>(null) }
    val audioPicker = rememberFilePicker(AUDIO_MIME) { uri ->
        val chapterId = uploadTarget ?: return@rememberFilePicker
        uploadTarget = null
        val f = context.describeFile(uri)
        validAudio(f)?.let { toastError(it); return@rememberFilePicker }
        uploads = uploads + (chapterId to 0f)
        scope.launch {
            try {
                val uploadId = uploadInChunks(context, f) { p -> uploads = uploads + (chapterId to p) }
                Api.post<Unit>("/panel/chapters/$chapterId/audio", buildJsonObject { put("upload_id", uploadId) })
                toast("Аудио загружено — поставлено в очередь на конвертацию")
                reload(); loadJobs()
            } catch (e: Exception) { toastError(e) }
            uploads = uploads - chapterId
        }
    }
    val altPicker = rememberFilePicker(AUDIO_MIME) { uri ->
        val t = altTarget ?: return@rememberFilePicker
        altTarget = null
        val f = context.describeFile(uri)
        validAudio(f)?.let { toastError(it); return@rememberFilePicker }
        val key = "${t.first}:${t.second}"
        altUploads = altUploads + (key to 0f)
        scope.launch {
            try {
                val uploadId = uploadInChunks(context, f) { p -> altUploads = altUploads + (key to p) }
                Api.post<Unit>("/panel/titles/$titleId/chapters", buildJsonObject {
                    put("volume_id", t.first); put("number", t.second)
                    put("narrator_ids", buildJsonArray { t.third.forEach { add(JsonPrimitive(it)) } })
                    put("upload_id", uploadId); put("version_id", currentVersion)
                })
                toast("Озвучка загружена — в очереди на конвертацию")
                reload(); loadJobs()
            } catch (e: Exception) { toastError(e) }
            altUploads = altUploads - key
        }
    }

    fun toggleSelected(id: Int) { selected = if (id in selected) selected - id else selected + id }

    // ---- versions ----
    if (canManageVersions) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(title.version_name, active = currentVersion == 0, onClick = { currentVersion = 0 })
                title.versions.forEach { v -> Pill(v.name, active = currentVersion == v.id, onClick = { currentVersion = v.id }) }
            }
            if (currentVersion == 0) IconBtn(Lucide.Pencil, "Переименовать основную версию", { versionName = title.version_name; versionForm = "main" }, size = 32.dp, iconSize = 14.dp)
            else {
                IconBtn(Lucide.Pencil, "Переименовать версию", { versionName = title.versions.firstOrNull { it.id == currentVersion }?.name ?: ""; versionForm = "rename" }, size = 32.dp, iconSize = 14.dp)
                IconBtn(Lucide.Trash2, "Удалить версию", { versionToDelete = title.versions.firstOrNull { it.id == currentVersion } }, size = 32.dp, iconSize = 14.dp, tint = Ar.danger)
            }
            IconBtn(Lucide.Plus, "Добавить версию", { versionName = ""; versionForm = "add" }, size = 32.dp, iconSize = 14.dp)
        }
    }
    if (isAlt) Text("Альтернативная версия: загружайте аудио для тех же глав, что и в основной. Главы без своего аудио будут проигрываться из основной версии.", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp))

    // ---- header row ----
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FieldLabel("Главы")
        Spacer(Modifier.weight(1f))
        if (!isAlt) ArButton("Добавить том", { showAddVolume = true }, kind = ButtonKind.Primary, icon = Lucide.Plus, small = true)
    }
    if (isMod && pendingTotal > 0) FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("На проверке: $pendingTotal", color = Ar.amber, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
        ArButton("Одобрить все", { bulkReview = Triple("approve", null, null) }, kind = ButtonKind.Primary, icon = Lucide.Check, small = true)
        ArButton("Отклонить все", { bulkReview = Triple("reject", null, null) }, kind = ButtonKind.Danger, icon = Lucide.X, small = true)
    }
    if (selected.isNotEmpty()) GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp), borderColor = Ar.accent.copy(alpha = 0.4f)) {
        Text("Выбрано глав: ${selected.size}", color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isMod && !isAlt) {
                ArButton("Одобрить", { bulkReview = Triple("approve", null, selected) }, kind = ButtonKind.Primary, icon = Lucide.Check, small = true, enabled = !bulkBusy)
                ArButton("Отклонить", { bulkReview = Triple("reject", null, selected) }, icon = Lucide.X, small = true, enabled = !bulkBusy)
            }
            ArButton(if (isAlt) "Чтецы" else "Том / чтецы", { bulkEditOpen = true }, icon = Lucide.Layers, small = true, enabled = !bulkBusy)
            ArButton("Удалить", { selectionAction = "delete" }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true, enabled = !bulkBusy)
            if (isAdmin) ArButton("Стереть навсегда", { selectionAction = "purge" }, kind = ButtonKind.Danger, icon = Lucide.Flame, small = true, enabled = !bulkBusy)
            ArButton("Снять выделение", { selected = emptyList() }, kind = ButtonKind.Ghost, small = true, enabled = !bulkBusy)
        }
    }

    // ---- volumes ----
    if (title.volumes.isEmpty()) EmptyState("Томов пока нет", "Сначала добавьте том — главы живут внутри томов.", Lucide.Layers)
    title.volumes.forEach { v ->
        val open = v.id in openVolumes
        val vis = visibleChapters(v)
        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth().clickable { openVolumes = if (open) openVolumes - v.id else openVolumes + v.id }.padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isAlt && vis.isNotEmpty()) Checkbox(vis.all { it.id in selected }, { on -> val ids = vis.map { it.id }; selected = if (on) (selected + ids).distinct() else selected - ids.toSet() }, colors = CheckboxDefaults.colors(checkedColor = Ar.accent, uncheckedColor = Ar.textMuted))
                else Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${title.volume_label} ${v.number}", color = Ar.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (v.name.isNotBlank()) Text("  ${v.name}", color = Ar.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        if (isAlt) "Озвучено: ${liveChapters(v).count { altSlot(v.id, it.number) != null }} из ${liveChapters(v).size}" else "Глав: ${liveChapters(v).size}" + (if (isMod && pendingIn(v) > 0) " · на проверке: ${pendingIn(v)}" else ""),
                        color = Ar.textMuted, fontSize = 12.sp,
                    )
                }
                if (!isAlt) {
                    if (isMod && pendingIn(v) > 0) {
                        IconBtn(Lucide.Check, "Одобрить все главы тома", { bulkReview = Triple("approve", v, null) }, size = 32.dp, iconSize = 15.dp, tint = Ar.ok)
                        IconBtn(Lucide.X, "Отклонить все главы тома", { bulkReview = Triple("reject", v, null) }, size = 32.dp, iconSize = 15.dp, tint = Ar.danger)
                    }
                    IconBtn(Lucide.Plus, "Добавить главу", { openVolumes = openVolumes + v.id; addChapterVol = v }, size = 32.dp, iconSize = 15.dp)
                    IconBtn(Lucide.Pencil, "Переименовать том", { volumeForm = v }, size = 32.dp, iconSize = 14.dp)
                    IconBtn(Lucide.Trash2, "Удалить том", { volumeToDelete = v }, size = 32.dp, iconSize = 14.dp, tint = Ar.danger)
                }
                Icon(Lucide.ChevronRight, null, tint = Ar.textMuted, modifier = Modifier.size(16.dp).rotate(if (open) 90f else 0f))
            }
            AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
                    HairlineDivider(Modifier.padding(bottom = 4.dp))
                    if (isAlt) {
                        if (liveChapters(v).isEmpty()) Text("В основной версии нет глав.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                        liveChapters(v).forEach { mainCh ->
                            val alt = altSlot(v.id, mainCh.number)
                            val key = "${v.id}:${mainCh.number}"
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (alt != null) Checkbox(alt.id in selected, { toggleSelected(alt.id) }, colors = CheckboxDefaults.colors(checkedColor = Ar.accent, uncheckedColor = Ar.textMuted)) else Spacer(Modifier.width(48.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.chapterLabel(mainCh.number, mainCh.number_end, mainCh.name), color = Ar.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (alt != null) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (alt.duration_seconds > 0) Text(Fmt.duration(alt.duration_seconds), color = Ar.textMuted, fontSize = 11.sp)
                                        StatusBadge(alt.audio_status); if (alt.mod_status != "approved") StatusBadge(alt.mod_status)
                                    } else Text("нет озвучки — играет из основной", color = Ar.textMuted, fontSize = 11.sp)
                                    uploads[alt?.id]?.let { ProgressTrack(it, Modifier.padding(top = 4.dp)) }
                                    altUploads[key]?.let { ProgressTrack(it, Modifier.padding(top = 4.dp)) }
                                }
                                if (alt != null) IconBtn(Lucide.Ellipsis, "Действия", { chapterMenu = alt }, size = 32.dp, iconSize = 16.dp)
                                else ArButton(altUploads[key]?.let { "${(it * 100).toInt()}%" } ?: "Загрузить", { altTarget = Triple(v.id, mainCh.number, mainCh.narrators.map { it.id }); altPicker.pick() }, icon = Lucide.Upload, small = true, enabled = altUploads[key] == null)
                            }
                        }
                    } else {
                        if (vis.isEmpty()) Text("В этом томе пока нет глав.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                        vis.forEach { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(c.id in selected, { toggleSelected(c.id) }, colors = CheckboxDefaults.colors(checkedColor = Ar.accent, uncheckedColor = Ar.textMuted))
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.chapterLabel(c.number, c.number_end, c.name), color = if (c.is_deleted) Ar.textMuted else Ar.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textDecoration = if (c.is_deleted) TextDecoration.LineThrough else null)
                                    FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (c.duration_seconds > 0) Text(Fmt.duration(c.duration_seconds), color = Ar.textMuted, fontSize = 11.sp)
                                        if (c.narrators.isNotEmpty()) Text(c.narrators.joinToString(", ") { it.name }, color = Ar.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (c.is_deleted) Text("удалена", color = Ar.danger, fontSize = 11.sp)
                                        StatusBadge(c.audio_status); if (c.mod_status != "approved") StatusBadge(c.mod_status)
                                    }
                                    uploads[c.id]?.let { ProgressTrack(it, Modifier.padding(top = 4.dp)) }
                                }
                                IconBtn(Lucide.Ellipsis, "Действия", { chapterMenu = c }, size = 32.dp, iconSize = 16.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- bulk upload ----
    if (!isAlt || title.volumes.isNotEmpty()) BulkUploadPanel(title, currentVersion, reload, ::loadJobs)

    // ---- jobs ----
    FieldLabel("Задачи конвертации", modifier = Modifier.padding(top = 16.dp))
    val j = jobs
    when {
        j == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { Spinner() }
        j.items.isEmpty() -> Text("Задач конвертации пока нет — загрузите аудио, и они появятся здесь.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        else -> {
            GlassPanel(Modifier.fillMaxWidth()) {
                j.items.forEachIndexed { i, job ->
                    if (i > 0) HairlineDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(job.chapter_name.ifBlank { "#${job.chapter_id}" }, color = Ar.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("попытки: ${job.attempts} · ${Fmt.timeAgo(job.created_at)}" + (job.finished_at?.let { " · завершена ${Fmt.timeAgo(it)}" } ?: ""), color = Ar.textMuted, fontSize = 11.sp)
                            if (job.error.isNotBlank()) Text(job.error, color = Ar.danger, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        StatusBadge(job.status)
                    }
                }
            }
            Pagination(j.page, j.total, j.per_page) { jobsPage = it }
        }
    }

    // ---- dialogs ----
    if (versionForm != null) ArModal(true, { versionForm = null }, when (versionForm) { "add" -> "Новая версия"; "main" -> "Основная версия"; else -> "Переименовать версию" }) {
        ArTextField(versionName, { versionName = it }, label = if (versionForm == "add") "Название новой версии" else "Название версии", maxLength = 100, placeholder = "Напр. «Мужской голос»")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", { versionForm = null }, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton("Сохранить", kind = ButtonKind.Primary, busy = versionBusy, onClick = {
                val nm = versionName.trim()
                if (nm.isEmpty()) { toastError("Укажите название версии"); return@ArButton }
                versionBusy = true
                scope.launch {
                    try {
                        when (versionForm) {
                            "add" -> { Api.post<Unit>("/panel/titles/$titleId/versions", buildJsonObject { put("name", nm) }); toast("Версия добавлена") }
                            "main" -> { Api.put<Unit>("/panel/titles/$titleId/version-name", buildJsonObject { put("name", nm) }); toast("Название версии обновлено") }
                            else -> { Api.patch<Unit>("/panel/versions/$currentVersion", buildJsonObject { put("name", nm) }); toast("Версия переименована") }
                        }
                        versionForm = null; reload()
                    } catch (e: Exception) { toastError(e) }
                    versionBusy = false
                }
            })
        }
    }
    ConfirmDialog(versionToDelete != null, { versionToDelete = null }, onConfirm = {
        val v = versionToDelete ?: return@ConfirmDialog
        scope.launch { try { Api.delete<Unit>("/panel/versions/${v.id}"); toast("Версия удалена"); versionToDelete = null; currentVersion = 0; reload() } catch (e: Exception) { toastError(e) } }
    }, title = "Удалить версию", body = "Удалить версию «${versionToDelete?.name}» вместе со всеми загруженными в неё главами? Слушатели, выбравшие её, вернутся к основной версии.", danger = true, confirmLabel = "Удалить")

    if (showAddVolume || volumeForm != null) VolumeDialog(title, volumeForm, onClose = { showAddVolume = false; volumeForm = null }, reload = reload)
    ConfirmDialog(volumeToDelete != null, { volumeToDelete = null }, onConfirm = {
        val v = volumeToDelete ?: return@ConfirmDialog
        scope.launch { try { Api.delete<Unit>("/panel/volumes/${v.id}"); toast("Том удалён"); volumeToDelete = null; reload() } catch (e: Exception) { toastError(e) } }
    }, title = "Удалить том", body = "Удалить том ${volumeToDelete?.number}${volumeToDelete?.name?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}? Тома с главами удалить нельзя.", danger = true, confirmLabel = "Удалить")

    addChapterVol?.let { v -> ChapterDialog(title, v, null, currentVersion, onClose = { addChapterVol = null }, reload = reload, loadJobs = ::loadJobs) }
    editingChapter?.let { c -> ChapterDialog(title, title.volumes.firstOrNull { it.id == c.volume_id } ?: title.volumes.first(), c, currentVersion, onClose = { editingChapter = null }, reload = reload, loadJobs = ::loadJobs) }

    chapterMenu?.let { c ->
        ArSheet(true, { chapterMenu = null }, Fmt.chapterLabel(c.number, c.number_end, c.name)) {
            if (c.is_deleted && isAdmin) MenuRow(Lucide.RotateCcw, "Восстановить главу", { chapterMenu = null; scope.launch { try { Api.post<Unit>("/mod/trash/chapter/${c.id}/restore"); toast("Глава восстановлена"); reload() } catch (e: Exception) { toastError(e) } } })
            MenuRow(if (c.audio_status == "none") Lucide.Upload else Lucide.RefreshCw, if (c.audio_status == "none") "Загрузить аудио" else "Заменить аудио", { chapterMenu = null; uploadTarget = c.id; audioPicker.pick() })
            if (!isAlt) MenuRow(Lucide.Pencil, "Редактировать главу", { chapterMenu = null; editingChapter = c })
            MenuRow(Lucide.Trash2, if (isAlt) "Удалить озвучку этой главы" else "Удалить главу", { chapterMenu = null; chapterToDelete = c }, tint = Ar.danger)
            if (isAdmin) MenuRow(Lucide.Flame, "Удалить навсегда — вместе с аудио", { chapterMenu = null; chapterToPurge = c }, tint = Ar.danger)
            Spacer(Modifier.height(12.dp))
        }
    }
    ConfirmDialog(chapterToDelete != null, { chapterToDelete = null }, onConfirm = {
        val c = chapterToDelete ?: return@ConfirmDialog
        scope.launch {
            try { val res = Api.delete<AppliedResult?>("/panel/chapters/${c.id}"); toast(if (res?.applied == false) "Удаление отправлено на модерацию" else "Глава удалена"); chapterToDelete = null; reload() } catch (e: Exception) { toastError(e) }
        }
    }, title = "Удалить главу", body = "Удалить главу ${chapterToDelete?.let { fmtNum(it.number) }} — ${chapterToDelete?.name?.ifBlank { "без названия" }}? Аудио будет удалено, когда удаление вступит в силу.", danger = true, confirmLabel = "Удалить")
    ConfirmDialog(chapterToPurge != null, { chapterToPurge = null }, onConfirm = {
        val c = chapterToPurge ?: return@ConfirmDialog
        scope.launch { try { Api.delete<Unit>("/mod/chapters/${c.id}/purge"); toast("Глава удалена навсегда"); chapterToPurge = null; reload() } catch (e: Exception) { toastError(e) } }
    }, title = "Удалить навсегда", body = "Глава ${chapterToPurge?.let { fmtNum(it.number) }} — ${chapterToPurge?.name?.ifBlank { "без названия" }} и её аудиофайл будут удалены окончательно, минуя корзину. Восстановить будет нечего.", danger = true, confirmLabel = "Удалить")

    ConfirmDialog(selectionAction != null, { selectionAction = null }, onConfirm = {
        val action = selectionAction ?: return@ConfirmDialog
        bulkBusy = true
        scope.launch {
            try {
                var n = 0
                for (id in selected) { if (action == "purge") Api.delete<Unit>("/mod/chapters/$id/purge") else Api.delete<Unit>("/panel/chapters/$id"); n++ }
                toast(if (action == "purge") "Удалено навсегда: $n" else "Удалено глав: $n")
                selected = emptyList(); selectionAction = null
            } catch (e: Exception) { toastError(e) }
            reload(); bulkBusy = false
        }
    }, title = if (selectionAction == "purge") "Стереть выбранные навсегда" else "Удалить выбранные",
        body = if (selectionAction == "purge") "Выбранные главы (${selected.size}) и их аудиофайлы будут удалены окончательно, минуя корзину." else "Выбранные главы (${selected.size}) будут удалены. Восстановить их можно из корзины.", danger = true, confirmLabel = "Удалить")

    if (bulkEditOpen) {
        var volumeId by remember { mutableStateOf<Int?>(null) }
        var changeNarr by remember { mutableStateOf(false) }
        var narrIds by remember { mutableStateOf<List<Int>>(emptyList()) }
        ArModal(true, { bulkEditOpen = false }, "Изменить выбранные главы") {
            Text("Изменения применятся к выбранным главам (${selected.size}) одним запросом.", color = Ar.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            if (!isAlt) SelectMenu(volumeId, listOf(SelectOption<Int?>(null, "— не менять —")) + title.volumes.map { SelectOption<Int?>(it.id, "${title.volume_label} ${it.number}" + (if (it.name.isNotBlank()) " — ${it.name}" else "")) }, { volumeId = it }, label = "Переместить в том")
            if (title.narrators.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ArToggle(changeNarr, { changeNarr = it }, "Изменить чтецов", "Заменит список чтецов у выбранных глав")
                if (changeNarr) ChapterNarratorPicker(title.narrators, narrIds, { narrIds = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                ArButton("Отмена", { bulkEditOpen = false }, kind = ButtonKind.Ghost)
                Spacer(Modifier.width(8.dp))
                ArButton("Применить", kind = ButtonKind.Primary, icon = Lucide.Check, busy = bulkBusy, onClick = {
                    if (volumeId == null && !changeNarr) { toastError("Выберите том или отметьте смену чтецов"); return@ArButton }
                    bulkBusy = true
                    scope.launch {
                        try {
                            val res = Api.post<UpdatedResult>("/panel/titles/$titleId/chapters/bulk-edit", buildJsonObject {
                                put("chapter_ids", buildJsonArray { selected.forEach { add(JsonPrimitive(it)) } })
                                volumeId?.let { put("volume_id", it) }
                                if (changeNarr) put("narrator_ids", buildJsonArray { narrIds.forEach { add(JsonPrimitive(it)) } })
                            })
                            toast("Изменено глав: ${res.updated}"); bulkEditOpen = false; selected = emptyList(); reload()
                        } catch (e: Exception) { toastError(e) }
                        bulkBusy = false
                    }
                })
            }
        }
    }

    bulkReview?.let { (decision, volume, ids) ->
        var note by remember { mutableStateOf("") }
        var reviewing by remember { mutableStateOf(false) }
        ArModal(true, { bulkReview = null }, if (decision == "reject") "Отклонить главы" else "Одобрить главы") {
            Text(
                (when {
                    ids != null -> "Решение применится к выбранным главам (${ids.size}) — в том числе к уже рассмотренным."
                    volume != null -> "Решение применится ко всем главам тома ${volume.number}, ожидающим проверки (${pendingIn(volume)})."
                    else -> "Решение применится ко всем главам тайтла, ожидающим проверки ($pendingTotal)."
                }) + if (decision == "approve") " Одобренные главы появятся на странице тайтла, как только их аудио будет сконвертировано." else " Отклонённые главы останутся скрытыми, а загрузивший получит уведомление с причиной.",
                color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 10.dp),
            )
            ArTextField(note, { note = it }, placeholder = if (decision == "reject") "Причина отклонения (обязательно)…" else "Комментарий — необязательно", maxLength = 1000, singleLine = false, minLines = 2)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                ArButton("Отмена", { bulkReview = null }, kind = ButtonKind.Ghost, enabled = !reviewing)
                Spacer(Modifier.width(8.dp))
                ArButton(if (decision == "reject") "Отклонить" else "Одобрить", kind = if (decision == "reject") ButtonKind.Danger else ButtonKind.Primary, busy = reviewing, onClick = {
                    if (decision == "reject" && note.trim().length < 3) { toastError("Укажите причину отклонения"); return@ArButton }
                    reviewing = true
                    scope.launch {
                        try {
                            val res = Api.post<CountResult>("/mod/titles/$titleId/chapters/moderate", buildJsonObject {
                                put("decision", decision)
                                if (ids == null && volume != null) put("volume_id", volume.id)
                                if (ids != null) put("chapter_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                                if (note.isNotBlank()) put("note", note.trim())
                            })
                            toast(if (decision == "approve") "Одобрено глав: ${res.count}" else "Отклонено глав: ${res.count}")
                            bulkReview = null; if (ids != null) selected = emptyList(); reload()
                        } catch (e: Exception) { toastError(e) }
                        reviewing = false
                    }
                })
            }
        }
    }
}

@Composable
private fun VolumeDialog(title: TitleFull, existing: Volume?, onClose: () -> Unit, reload: suspend () -> Unit) {
    var number by remember { mutableStateOf(existing?.number?.toString() ?: ((title.volumes.maxOfOrNull { it.number } ?: 0) + 1).toString()) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ArModal(true, onClose, if (existing == null) "Новый том" else "Том ${existing.number}") {
        ArTextField(number, { number = it }, label = "Номер", keyboardType = KeyboardType.Number)
        Spacer(Modifier.height(10.dp))
        ArTextField(name, { name = it }, label = "Название (необязательно)", maxLength = 200, placeholder = "Часть первая")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onClose, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton(if (existing == null) "Добавить" else "Сохранить", kind = ButtonKind.Primary, busy = busy, onClick = {
                val n = number.trim().toIntOrNull()
                if (n == null || n < 0) { toastError("Номер тома должен быть числом не меньше 0"); return@ArButton }
                busy = true
                scope.launch {
                    try {
                        val body = buildJsonObject { put("number", n); put("name", name.trim()) }
                        if (existing == null) { Api.post<Unit>("/panel/titles/${title.id}/volumes", body); toast("Том добавлен") }
                        else { Api.patch<Unit>("/panel/volumes/${existing.id}", body); toast("Том обновлён") }
                        onClose(); reload()
                    } catch (e: Exception) { toastError(e) }
                    busy = false
                }
            })
        }
    }
}

/** Add (with a required audio file) or edit a chapter. */
@Composable
private fun ChapterDialog(title: TitleFull, volume: Volume, existing: ChapterRow?, versionId: Int, onClose: () -> Unit, reload: suspend () -> Unit, loadJobs: suspend () -> Unit) {
    val auth = LocalAuth.current
    val context = LocalContext.current
    val isMod = auth.isMod
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf(existing?.let { fmtNum(it.number) } ?: fmtNum(nextNumberIn(volume))) }
    var range by remember { mutableStateOf(existing?.number_end != null) }
    var numberEnd by remember { mutableStateOf(existing?.number_end?.let { fmtNum(it) } ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var narratorIds by remember { mutableStateOf(existing?.narrators?.map { it.id } ?: emptyList()) }
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberFilePicker(AUDIO_MIME) { uri -> val f = context.describeFile(uri); validAudio(f)?.let { toastError(it) } ?: run { file = f } }

    ArModal(true, onClose, if (existing == null) "Новая глава — ${title.volume_label} ${volume.number}" else "Редактировать главу") {
        Row(verticalAlignment = Alignment.Bottom) {
            ArTextField(number, { number = it }, Modifier.weight(1f), label = if (range) "С главы" else "Номер главы", hint = if (!range) "Может быть дробным: 4.1 встанет между 4 и 5" else null, keyboardType = KeyboardType.Decimal)
            if (isMod && range) {
                Spacer(Modifier.width(8.dp))
                ArTextField(numberEnd, { numberEnd = it }, Modifier.weight(1f), label = "По главу", placeholder = "по", keyboardType = KeyboardType.Decimal)
            }
        }
        if (isMod) ArToggle(range, { range = it }, "Несколько глав в одном файле", "Один файл охватывает несколько глав, например 30–35")
        Spacer(Modifier.height(10.dp))
        ArTextField(name, { name = it }, label = "Название главы (необязательно)", maxLength = 300)
        Spacer(Modifier.height(10.dp))
        ChapterNarratorPicker(title.narrators, narratorIds, { narratorIds = it }, label = "Чтецы главы")
        if (existing == null) {
            Spacer(Modifier.height(10.dp))
            ArButton(file?.name ?: "Выбрать аудио", { picker.pick() }, icon = Lucide.Upload, enabled = !busy, fullWidth = true)
            progress?.let { ProgressTrack(it, Modifier.padding(top = 8.dp), height = 4.dp) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", onClose, kind = ButtonKind.Ghost, enabled = !busy)
            Spacer(Modifier.width(8.dp))
            ArButton(
                if (busy) (progress?.let { "Загружаем… ${(it * 100).toInt()}%" } ?: "Сохраняем…") else if (existing == null) "Добавить главу" else "Сохранить",
                kind = ButtonKind.Primary, busy = busy, enabled = existing != null || file != null,
                onClick = {
                    val n = number.trim().replace(',', '.').toDoubleOrNull()
                    if (n == null || n < 0) { toastError("Номер главы должен быть числом не меньше 0"); return@ArButton }
                    var end: Double? = null
                    if (isMod && range) {
                        end = numberEnd.trim().replace(',', '.').toDoubleOrNull()
                        if (end == null || end <= n) { toastError("Конец диапазона должен быть больше начального номера"); return@ArButton }
                    }
                    if (existing == null && title.narrators.isNotEmpty() && narratorIds.isEmpty()) { toastError("Отметьте чтеца главы"); return@ArButton }
                    busy = true
                    scope.launch {
                        try {
                            if (existing == null) {
                                val f = file ?: error("Выберите аудиофайл — глава без аудио не создаётся")
                                progress = 0f
                                val uploadId = uploadInChunks(context, f) { progress = it }
                                val row = Api.post<ChapterCreated>("/panel/titles/${title.id}/chapters", buildJsonObject {
                                    put("volume_id", volume.id); put("number", n); put("number_end", end?.let { JsonPrimitive(it) } ?: JsonNull)
                                    put("name", name.trim()); put("narrator_ids", buildJsonArray { narratorIds.forEach { add(JsonPrimitive(it)) } })
                                    put("upload_id", uploadId); put("version_id", versionId)
                                })
                                toast(if (row.mod_status == "approved") "Глава добавлена — аудио в очереди на конвертацию" else "Глава добавлена — отправлена на модерацию")
                                onClose(); reload(); loadJobs()
                            } else {
                                val res = Api.patch<AppliedResult?>("/panel/chapters/${existing.id}", buildJsonObject {
                                    put("name", name.trim()); put("number", n)
                                    put("narrator_ids", buildJsonArray { narratorIds.forEach { add(JsonPrimitive(it)) } })
                                    if (isMod) put("number_end", end?.let { JsonPrimitive(it) } ?: JsonNull)
                                })
                                toast(if (res?.applied == false) "Отправлено на модерацию" else "Изменения применены")
                                onClose(); reload()
                            }
                        } catch (e: Exception) { toastError(e) }
                        busy = false; progress = null
                    }
                },
            )
        }
    }
}

@Composable
private fun BulkUploadPanel(title: TitleFull, versionId: Int, reload: suspend () -> Unit, loadJobs: suspend () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var volumeId by remember(title.volumes) { mutableStateOf(title.volumes.firstOrNull()?.id) }
    var start by remember { mutableStateOf("") }
    var narratorIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    var files by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var useFileNames by remember { mutableStateOf(false) }
    val target = title.volumes.firstOrNull { it.id == volumeId }
    val firstNumber: Double? = start.trim().let { t -> if (t.isEmpty()) (target?.let { nextNumberIn(it) } ?: 1.0) else t.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } }
    val picker = rememberMultiFilePicker(AUDIO_MIME) { uris ->
        val picked = uris.map { context.describeFile(it) }
        for (f in picked) validAudio(f)?.let { toastError(it); return@rememberMultiFilePicker }
        files = picked.sortedBy { naturalKey(it.name) }
    }

    FieldLabel("Массовая загрузка", modifier = Modifier.padding(top = 16.dp))
    GlassPanel(Modifier.fillMaxWidth()) {
        Text("Файлы сортируются по имени и добавляются в выбранный том одной пачкой. Названия глав по умолчанию не задаются — главы показываются как «Глава N».", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
        SelectMenu(volumeId, title.volumes.map { SelectOption<Int?>(it.id, "${title.volume_label} ${it.number}" + (if (it.name.isNotBlank()) " — ${it.name}" else "")) }, { volumeId = it }, label = "Целевой том", placeholder = "Томов пока нет", enabled = title.volumes.isNotEmpty())
        Spacer(Modifier.height(10.dp))
        ArTextField(start, { start = it }, label = "Начать с главы (${target?.let { v -> liveChapters(v).size.takeIf { it > 0 }?.let { "сейчас $it" } } ?: "том пуст"})", placeholder = target?.let { fmtNum(nextNumberIn(it)) } ?: "1", keyboardType = KeyboardType.Decimal)
        if (title.narrators.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ChapterNarratorPicker(title.narrators, narratorIds, { narratorIds = it }, label = "Чтецы этих глав (обязательно)") }
        Spacer(Modifier.height(8.dp))
        ArToggle(useFileNames, { useFileNames = it }, "Использовать имена файлов как названия глав")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArButton(if (files.isNotEmpty()) "Выбрать другие файлы" else "Выбрать файлы", { picker.pick() }, icon = Lucide.Plus, small = true, enabled = progress == null && title.volumes.isNotEmpty())
            ArButton(if (progress != null) "Загружаем…" else if (files.isNotEmpty()) "Загрузить (${files.size})" else "Загрузить", kind = ButtonKind.Primary, icon = Lucide.Upload, small = true, enabled = progress == null && files.isNotEmpty(), onClick = {
                val volId = volumeId ?: run { toastError("Сначала выберите том"); return@ArButton }
                val first = firstNumber ?: run { toastError("Начальный номер должен быть числом не меньше 0"); return@ArButton }
                if (title.narrators.isNotEmpty() && narratorIds.isEmpty()) { toastError("Отметьте чтецов этих глав"); return@ArButton }
                val total = files.sumOf { it.size }.takeIf { it > 0 } ?: 1L
                var done = 0L
                progress = 0f
                scope.launch {
                    try {
                        val ids = mutableListOf<Int>()
                        for (f in files) {
                            ids += uploadInChunks(context, f) { frac -> progress = minOf(1f, (done + frac * f.size) / total) }
                            done += f.size; progress = minOf(1f, done.toFloat() / total)
                        }
                        val res = Api.post<kotlinx.serialization.json.JsonObject>("/panel/titles/${title.id}/chapters/bulk", buildJsonObject {
                            put("volume_id", volId); put("start_number", first); put("use_file_names", useFileNames)
                            put("narrator_ids", buildJsonArray { narratorIds.forEach { add(JsonPrimitive(it)) } })
                            put("upload_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                            put("version_id", versionId)
                        })
                        val n = (res["chapters"] as? kotlinx.serialization.json.JsonArray)?.size ?: files.size
                        toast("Создано глав: $n — аудио в очереди на конвертацию")
                        files = emptyList(); start = ""; reload(); loadJobs()
                    } catch (e: Exception) { toastError(e) }
                    progress = null
                }
            })
            if (files.isNotEmpty() && progress == null) ArButton("Очистить", { files = emptyList() }, kind = ButtonKind.Ghost, small = true)
        }
        if (files.isNotEmpty()) Column(Modifier.padding(top = 10.dp)) {
            files.forEachIndexed { i, f ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(firstNumber?.let { fmtNum(it + i) } ?: "?", color = Ar.accent, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                    Text(f.name, color = Ar.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        progress?.let { ProgressTrack(it, Modifier.padding(top = 10.dp), height = 4.dp) }
    }
}
