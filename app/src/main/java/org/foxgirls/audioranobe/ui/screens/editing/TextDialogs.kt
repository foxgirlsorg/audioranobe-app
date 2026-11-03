package org.foxgirls.audioranobe.ui.screens.editing

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.BookChapter
import org.foxgirls.audioranobe.core.BookParser
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArModal
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.SelectMenu
import org.foxgirls.audioranobe.ui.components.SelectOption
import org.foxgirls.audioranobe.ui.components.Spinner
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError

data class VolumeOption(val id: Int, val number: Double, val name: String)

private fun volumeLabel(word: String, v: VolumeOption) = "$word ${Fmt.trimNum(v.number)}" + if (v.name.isNotBlank()) " · ${v.name}" else ""

@Serializable
private data class Updated(val updated: Int = 0)

@Composable
private fun DialogFoot(busy: Boolean, busyLabel: String, label: String, enabled: Boolean, onClose: () -> Unit, onSubmit: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        ArButton("Отмена", onClose, kind = ButtonKind.Ghost, small = true, enabled = !busy)
        ArButton(if (busy) busyLabel else label, onSubmit, kind = ButtonKind.Primary, small = true, busy = busy, enabled = enabled)
    }
}

/** TextDialogs AddChapterModal: a new text chapter with a pasted text. */
@Composable
fun AddTextChapterDialog(titleId: Int, volumes: List<VolumeOption>, volumeWord: String, defaultVolumeId: Int, nextNumber: (Int) -> Int, onClose: () -> Unit, onDone: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var volumeId by remember { mutableStateOf(defaultVolumeId) }
    var number by remember { mutableStateOf(nextNumber(defaultVolumeId).toString()) }
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    ArModal(true, { if (!busy) onClose() }, "Добавить главу") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectMenu(volumeId, volumes.map { SelectOption(it.id, volumeLabel(volumeWord, it)) }, { volumeId = it; number = nextNumber(it).toString() }, Modifier.weight(1.6f), label = volumeWord)
            ArTextField(number, { number = it }, Modifier.weight(1f), label = "Номер", keyboardType = KeyboardType.Decimal)
        }
        Spacer(Modifier.height(10.dp))
        ArTextField(name, { name = it }, label = "Название", placeholder = "Необязательно", maxLength = 300)
        Spacer(Modifier.height(10.dp))
        ArTextField(text, { text = it }, Modifier.heightIn(min = 180.dp, max = 360.dp), label = "Текст", singleLine = false, minLines = 8)
        DialogFoot(busy, "Добавляем…", "Добавить", true, onClose) {
            val n = number.replace(',', '.').toDoubleOrNull()
            if (n == null || n < 0) { toastError("Номер должен быть числом не меньше 0"); return@DialogFoot }
            if (text.isBlank()) { toastError("Вставьте текст главы"); return@DialogFoot }
            busy = true
            scope.launch {
                try {
                    Api.post<Unit>("/panel/titles/$titleId/texts/chapters", buildJsonObject { put("volume_id", volumeId); put("number", n); put("name", name.trim()); put("text", text) })
                    toast("Глава добавлена"); onDone(); onClose()
                } catch (e: Exception) { toastError(e) }
                busy = false
            }
        }
    }
}

/** TextDialogs EditVolumeModal: the number and name of a volume. */
@Composable
fun EditVolumeDialog(volume: VolumeOption, volumeWord: String, onClose: () -> Unit, onDone: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf(Fmt.trimNum(volume.number)) }
    var name by remember { mutableStateOf(volume.name) }
    var busy by remember { mutableStateOf(false) }
    ArModal(true, { if (!busy) onClose() }, "Править: ${volumeLabel(volumeWord, volume)}") {
        ArTextField(number, { number = it }, label = "Номер", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        ArTextField(name, { name = it }, label = "Название", placeholder = "Необязательно", maxLength = 300)
        DialogFoot(busy, "Сохраняем…", "Сохранить", true, onClose) {
            val n = number.replace(',', '.').toDoubleOrNull()
            if (n == null || n < 0) { toastError("Номер должен быть числом не меньше 0"); return@DialogFoot }
            busy = true
            scope.launch {
                try {
                    Api.patch<Unit>("/panel/volumes/${volume.id}", buildJsonObject { put("number", n); put("name", name.trim()) })
                    toast("Сохранено"); onDone(); onClose()
                } catch (e: Exception) { toastError(e) }
                busy = false
            }
        }
    }
}

/** TextDialogs MoveModal: moves the chosen chapters into another volume. */
@Composable
fun MoveChaptersDialog(titleId: Int, ids: List<Int>, volumes: List<VolumeOption>, volumeWord: String, onClose: () -> Unit, onDone: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var volumeId by remember { mutableStateOf(volumes.firstOrNull()?.id) }
    var busy by remember { mutableStateOf(false) }
    ArModal(true, { if (!busy) onClose() }, "Переместить глав: ${ids.size}") {
        SelectMenu(volumeId, volumes.map { SelectOption<Int?>(it.id, volumeLabel(volumeWord, it)) }, { volumeId = it }, Modifier.fillMaxWidth(), label = "В ${volumeWord.lowercase()}")
        DialogFoot(busy, "Переносим…", "Переместить", volumeId != null, onClose) {
            val target = volumeId ?: return@DialogFoot
            busy = true
            scope.launch {
                try {
                    val res = Api.post<Updated>("/panel/titles/$titleId/chapters/bulk-edit", buildJsonObject {
                        put("chapter_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }); put("volume_id", target)
                    })
                    toast("Перемещено глав: ${res.updated.takeIf { it > 0 } ?: ids.size}"); onDone(); onClose()
                } catch (e: Exception) { toastError(e) }
                busy = false
            }
        }
    }
}

private data class ImportRow(val name: String, val text: String, val on: Boolean)

private const val SHORT = 200

/** BookImportModal: splits an EPUB or FB2 file into chapters on the device, lets you pick and tidy them, then uploads the texts. */
@Composable
fun BookImportDialog(titleId: Int, volumeWord: String, defaultVolume: Double, onClose: () -> Unit, onDone: suspend () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ImportRow>?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var stripFirst by remember { mutableStateOf(false) }
    var fromZero by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(Fmt.trimNum(defaultVolume)) }
    var open by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun body(r: ImportRow) = if (stripFirst) BookParser.withoutFirstLine(r.text) else r.text
    val picked = rows.orEmpty().filter { it.on && body(it).isNotBlank() }
    val chars = picked.sumOf { body(it).length }

    fun load(uri: Uri) {
        parsing = true; rows = null
        scope.launch {
            try {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
                fileName = name
                val parsed: List<BookChapter> = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось открыть файл")
                    BookParser.parse(name, bytes)
                }
                rows = parsed.map { ImportRow(it.name, it.text, it.text.length >= SHORT) }
            } catch (e: Exception) { toastError(e) }
            parsing = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) load(uri) }

    ArModal(true, { if (!busy) onClose() }, "Импорт из файла") {
        Text("EPUB или FB2", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArButton(if (rows != null) "Другой файл" else "Выбрать файл", { picker.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "application/octet-stream", "text/xml", "*/*")) }, icon = Lucide.Upload, small = true, enabled = !parsing && !busy)
            Spacer(Modifier.width(10.dp))
            Text(fileName.ifEmpty { "Файл не выбран" }, color = Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (parsing) Spinner(Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
        val list = rows
        if (list != null) {
            Spacer(Modifier.height(12.dp))
            ArTextField(volume, { volume = it }, label = "$volumeWord, в который добавить главы", keyboardType = KeyboardType.Decimal)
            Spacer(Modifier.height(6.dp))
            ArToggle(fromZero, { fromZero = it }, "Нумеровать с нуля", "Первая глава получит номер 0 — для пролога.")
            ArToggle(stripFirst, { stripFirst = it }, "Убрать первую строку глав", "Включите, если первая строка каждой главы — её название: при озвучке оно не будет прочитано дважды.")
            val allOn = list.all { it.on }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(allOn, { on -> rows = list.map { it.copy(on = on) } }, colors = CheckboxDefaults.colors(checkedColor = Ar.accent, uncheckedColor = Ar.textMuted))
                Text("Выбрано ${picked.size} из ${list.size} · ${"%,d".format(chars).replace(',', ' ')} знаков", color = Ar.textSecondary, fontSize = 12.sp)
            }
            var number = if (fromZero) 0 else 1
            list.forEachIndexed { i, r ->
                val text = body(r)
                val counted = r.on && text.isNotBlank()
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(r.on, { on -> rows = list.toMutableList().also { it[i] = r.copy(on = on) } }, colors = CheckboxDefaults.colors(checkedColor = Ar.accent, uncheckedColor = Ar.textMuted))
                        Text(if (counted) number.toString() else "—", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                        ArTextField(r.name, { v -> rows = list.toMutableList().also { it[i] = r.copy(name = v) } }, Modifier.weight(1f), placeholder = "Без названия", maxLength = 300)
                        if (i > 0) IconBtn(Lucide.ArrowUp, "Склеить с предыдущей главой", {
                            rows = list.toMutableList().also { it[i - 1] = it[i - 1].copy(text = it[i - 1].text + "\n" + r.text); it.removeAt(i) }
                        }, size = 36.dp, iconSize = 15.dp)
                    }
                    Text(
                        "${"%,d".format(text.length).replace(',', ' ')} зн." + (if (text.length < SHORT) " · короткая" else "") + " · " + (if (open == i) "скрыть" else "текст"),
                        color = Ar.accent, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 76.dp).clickable { open = if (open == i) null else i },
                    )
                    if (open == i) Text(text, color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 30, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 76.dp, top = 4.dp))
                }
                if (counted) number++
            }
        }
        DialogFoot(busy, "Импортируем…", "Импортировать" + if (picked.isNotEmpty()) " (${picked.size})" else "", picked.isNotEmpty(), onClose) {
            val v = volume.replace(',', '.').toDoubleOrNull()
            if (v == null || v < 0) { toastError("Номер тома должен быть числом не меньше 0"); return@DialogFoot }
            busy = true
            scope.launch {
                try {
                    Api.post<Unit>("/panel/titles/$titleId/texts/import", buildJsonObject {
                        put("volume", v); put("from_zero", fromZero)
                        put("chapters", buildJsonArray { picked.forEach { r -> add(buildJsonObject { put("name", r.name); put("text", body(r)) }) } })
                    })
                    toast("Импортировано глав: ${picked.size}"); onDone(); onClose()
                } catch (e: Exception) { toastError(e) }
                busy = false
            }
        }
    }
}
