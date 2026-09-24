package org.foxgirls.audioranobe.ui.screens.editing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.Illustration
import org.foxgirls.audioranobe.data.TitleFull
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArTabs
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.FieldLabel
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.SelectMenu
import org.foxgirls.audioranobe.ui.components.SelectOption
import org.foxgirls.audioranobe.ui.components.TabItem
import org.foxgirls.audioranobe.ui.components.TabsVariant
import org.foxgirls.audioranobe.ui.components.rememberImagePicker
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable private data class IllustrationOrder(val items: List<Illustration> = emptyList())

/** components/Illustrations/IllustrationManager: upload, bind to a chapter, caption, reorder, delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IllustrationManager(title: TitleFull) {
    val scope = rememberCoroutineScope()
    var items by remember(title.illustrations) { mutableStateOf(title.illustrations) }
    var bindMode by remember { mutableStateOf("title") }
    var targetVol by remember { mutableStateOf<Int?>(null) }
    var targetChap by remember { mutableStateOf<Int?>(null) }
    var uploading by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirmDelete by remember { mutableStateOf<Illustration?>(null) }
    var busy by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(targetVol) { targetChap = null }

    val volOptions = title.volumes.map { SelectOption<Int?>(it.id, "${title.volume_label} ${it.number}") }
    fun chapOptions(volId: Int?) = title.volumes.firstOrNull { it.id == volId }?.chapters?.filter { !it.is_deleted }?.map { SelectOption<Int?>(it.id, Fmt.chapterNumber(it.number, it.number_end)) } ?: emptyList()
    val volumeOfChapter = remember(title.volumes) { title.volumes.flatMap { v -> v.chapters.map { it.id to v.id } }.toMap() }

    val picker = rememberImagePicker(2048, 2048) { picked ->
        val chapterId = if (bindMode == "chapter") targetChap else null
        uploading = 0 to 1
        scope.launch {
            try {
                val created = Api.upload<Illustration>("/panel/titles/${title.id}/illustrations") {
                    addPart(picked.part("file", "illustration.webp"))
                    if (chapterId != null) addFormDataPart("chapter_id", chapterId.toString())
                }
                items = items + created; toast("Иллюстрация добавлена")
            } catch (e: Exception) { toastError(e) }
            uploading = null
        }
    }
    suspend fun patch(ill: Illustration, body: kotlinx.serialization.json.JsonObject): Boolean {
        busy = ill.id
        return try { val updated = Api.patch<Illustration>("/panel/illustrations/${ill.id}", body); items = items.map { if (it.id == ill.id) updated else it }; true }
        catch (e: Exception) { toastError(e); false } finally { busy = null }
    }
    fun move(index: Int, delta: Int) {
        val to = index + delta
        if (to < 0 || to >= items.size) return
        val next = items.toMutableList(); val tmp = next[index]; next[index] = next[to]; next[to] = tmp
        val before = items; items = next
        scope.launch {
            try { items = Api.put<IllustrationOrder>("/panel/titles/${title.id}/illustrations/order", buildJsonObject { put("ids", buildJsonArray { next.forEach { add(JsonPrimitive(it.id)) } }) }).items }
            catch (e: Exception) { items = before; toastError(e) }
        }
    }

    val volumeCovers = title.volumes.filter { it.cover_url != null }
    if (volumeCovers.isNotEmpty()) {
        FieldLabel(title.volume_label_plural)
        FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
            volumeCovers.forEach { v ->
                Column(Modifier.weight(1f)) {
                    ArImage(v.cover_thumb_url ?: v.cover_url, Modifier.fillMaxWidth().aspectRatio(2f / 3f), shape = RoundedCornerShape(8.dp))
                    Text(v.name.ifBlank { "${title.volume_label} ${v.number}" }, color = Ar.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        FieldLabel("Привязать к")
        ArTabs(listOf(TabItem("title", "Тайтлу"), TabItem("chapter", "Главе")), bindMode, { bindMode = it; if (it == "title") { targetVol = null; targetChap = null } }, variant = TabsVariant.Square, scrollable = false)
        if (bindMode == "chapter") Row(Modifier.padding(top = 8.dp)) {
            SelectMenu(targetVol, volOptions, { targetVol = it }, Modifier.weight(1f), placeholder = title.volume_label, small = true)
            Spacer(Modifier.width(8.dp))
            SelectMenu(targetChap, chapOptions(targetVol), { targetChap = it }, Modifier.weight(1f), placeholder = "Глава", enabled = targetVol != null, small = true)
        }
        Spacer(Modifier.height(12.dp))
        ArButton(if (uploading != null) "Загрузка…" else "Выбрать изображение", { picker.pick() }, kind = ButtonKind.Primary, icon = Lucide.ImagePlus, enabled = uploading == null, fullWidth = true)
        Text("JPEG, PNG или WebP; картинка уменьшается до 2048 px.", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }

    if (items.isEmpty()) Text("Иллюстраций пока нет. Привязанные к главе показываются вместо обложки в полноэкранном плеере, пока играет эта глава; все остальные — во вкладке «Иллюстрации» на странице тайтла.", color = Ar.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
    items.forEachIndexed { index, ill ->
        var caption by remember(ill.id, ill.caption) { mutableStateOf(ill.caption) }
        var editVol by remember(ill.id, ill.chapter_id) { mutableStateOf(ill.chapter_id?.let { volumeOfChapter[it] }) }
        val isChapter = editVol != null
        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row {
                Box(Modifier.width(96.dp).aspectRatio(ill.width.toFloat() / ill.height.coerceAtLeast(1)).clip(RoundedCornerShape(8.dp)).background(Ar.surfaceRaised)) {
                    ArImage(ill.thumb_url.ifBlank { ill.url }, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${ill.width}×${ill.height}", color = Ar.textMuted, fontSize = 11.sp)
                    ArTabs(listOf(TabItem("title", "Тайтл"), TabItem("chapter", "Глава")), if (isChapter) "chapter" else "title", { k ->
                        if (k == "title") { editVol = null; scope.launch { patch(ill, buildJsonObject { put("chapter_id", JsonNull) }) } }
                        else if (editVol == null && volOptions.isNotEmpty()) editVol = volOptions.first().value
                    }, Modifier.padding(top = 4.dp), variant = TabsVariant.Square, scrollable = false)
                    if (isChapter) Row(Modifier.padding(top = 6.dp)) {
                        SelectMenu(editVol, volOptions, { v -> editVol = v; scope.launch { patch(ill, buildJsonObject { put("chapter_id", JsonNull) }) } }, Modifier.weight(1f), small = true)
                        Spacer(Modifier.width(6.dp))
                        SelectMenu(ill.chapter_id?.takeIf { volumeOfChapter[it] == editVol }, chapOptions(editVol), { c -> scope.launch { patch(ill, buildJsonObject { put("chapter_id", c?.let { JsonPrimitive(it) } ?: JsonNull) }) } }, Modifier.weight(1f), placeholder = "Глава", small = true)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ArTextField(caption, { caption = it }, placeholder = "Подпись (необязательно)", maxLength = 200, imeAction = ImeAction.Done, onImeAction = {
                val next = caption.trim()
                if (next != ill.caption) scope.launch { if (!patch(ill, buildJsonObject { put("caption", next) })) caption = ill.caption }
            })
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Lucide.ArrowLeft, "Сдвинуть раньше", { move(index, -1) }, size = 32.dp, iconSize = 15.dp, enabled = index > 0)
                IconBtn(Lucide.ArrowRight, "Сдвинуть позже", { move(index, 1) }, size = 32.dp, iconSize = 15.dp, enabled = index < items.size - 1)
                Spacer(Modifier.weight(1f))
                if (busy == ill.id) org.foxgirls.audioranobe.ui.components.Spinner(size = 16.dp)
                IconBtn(Lucide.Trash2, "Удалить", { confirmDelete = ill }, size = 32.dp, iconSize = 15.dp, tint = Ar.danger)
            }
        }
    }

    ConfirmDialog(confirmDelete != null, { confirmDelete = null }, onConfirm = {
        val ill = confirmDelete ?: return@ConfirmDialog
        confirmDelete = null; busy = ill.id
        scope.launch {
            try { Api.delete<Unit>("/panel/illustrations/${ill.id}"); items = items.filter { it.id != ill.id }; toast("Иллюстрация удалена") } catch (e: Exception) { toastError(e) }
            busy = null
        }
    }, title = "Удалить иллюстрацию?", body = "Изображение удалится с сайта насовсем — вернуть его можно будет только повторной загрузкой.", danger = true, confirmLabel = "Удалить")
}
