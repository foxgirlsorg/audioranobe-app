package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Poll
import org.foxgirls.audioranobe.data.PollVoters
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError

private const val MAX_OPTIONS = 10

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PollCard(poll: Poll, onChange: (Poll?) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val auth = LocalAuth.current
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var voters by remember { mutableStateOf<PollVoters?>(null) }
    var votersOpen by remember { mutableStateOf(false) }
    var editDraft by remember { mutableStateOf<PollDraft?>(null) }
    var reopen by remember { mutableStateOf(false) }
    val showResults = poll.total_votes != null

    fun run(block: suspend () -> Poll?) {
        busy = true
        scope.launch {
            try { onChange(block()) } catch (e: Exception) { toastError(e) }
            busy = false; confirm = null
        }
    }

    val editing = editDraft
    if (editing != null) {
        Column(modifier.fillMaxWidth()) {
            PollBuilder(editing, { editDraft = it }, removable = false)
            if (poll.is_closed) Row(Modifier.padding(top = 8.dp)) { ArToggle(reopen, { reopen = it }, "Возобновить опрос") }
            Text("Голоса за удалённые варианты пропадут.", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                ArButton("Отмена", { editDraft = null }, kind = ButtonKind.Ghost, enabled = !busy)
                Spacer(Modifier.width(8.dp))
                ArButton("Сохранить", kind = ButtonKind.Primary, busy = busy, onClick = {
                    val payload = editing.editPayload(PollDraft.of(poll), reopen) ?: return@ArButton
                    run { Api.patch<Poll>("/polls/${poll.id}", payload).also { editDraft = null; voters = null; votersOpen = false } }
                })
            }
        }
        return
    }

    GlassPanel(modifier.fillMaxWidth()) {
        Text(poll.title, color = Ar.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
        poll.options.forEach { o ->
            val mine = poll.my_vote == o.id
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.padding(top = 8.dp).fillMaxWidth().clip(shape).background(Ar.fill04)
                    .border(BorderStroke(1.dp, if (mine) Ar.accent.copy(alpha = 0.55f) else Ar.border), shape)
                    .clickable(enabled = poll.can_vote && !busy) {
                        if (auth.user.value == null) { toastError("Войдите, чтобы голосовать"); return@clickable }
                        run { Api.post<Poll>("/polls/${poll.id}/vote", buildJsonObject { put("option_id", o.id) }) }
                    },
            ) {
                if (showResults) Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxWidth((o.percent ?: 0) / 100f).fillMaxHeight().background(Ar.accent.copy(alpha = 0.18f))) }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                        if (mine) Icon(Lucide.Check, null, tint = Ar.accent, modifier = Modifier.size(14.dp))
                        else if (!showResults) Icon(Lucide.Circle, null, tint = Ar.textMuted, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(o.text, color = Ar.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (showResults) Text("${o.percent ?: 0}%", color = Ar.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (votersOpen) voters?.let { v ->
            poll.options.forEach { o ->
                val names = v.options.firstOrNull { it.option_id == o.id }?.users?.joinToString(", ") { it.display_name.ifBlank { it.username } }.orEmpty()
                Text("${o.text}: ${names.ifEmpty { "—" }}", color = Ar.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        val total = poll.total_votes
        val meta = buildString {
            append(if (total != null) "$total ${Fmt.plural(total, "голос", "голоса", "голосов")}" else "Проголосуйте, чтобы увидеть результаты")
            if (poll.is_closed) append(" · завершён")
            else if (poll.closes_at != null) append(" · до ${Fmt.date(poll.closes_at)} ${Fmt.time(poll.closes_at)}")
        }
        Text(meta, color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        if (poll.can_retract || poll.can_manage || poll.can_view_voters || poll.can_edit) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (poll.can_edit) ArButton("Изменить", { reopen = false; editDraft = PollDraft.of(poll) }, icon = Lucide.Pencil, small = true, busy = busy)
            if (poll.can_view_voters) ArButton(if (votersOpen) "Скрыть голоса" else "Кто голосовал", {
                if (votersOpen) votersOpen = false
                else scope.launch { try { voters = Api.get<PollVoters>("/polls/${poll.id}/voters"); votersOpen = true } catch (e: Exception) { toastError(e) } }
            }, small = true, busy = busy)
            if (poll.can_retract) ArButton("Отменить голос", { run { Api.delete<Poll>("/polls/${poll.id}/vote") } }, icon = Lucide.Undo2, small = true, busy = busy)
            if (poll.can_manage && !poll.is_closed) ArButton("Завершить", { confirm = "stop" }, small = true, busy = busy)
            if (poll.can_manage) ArButton("Удалить", { confirm = "remove" }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true, busy = busy)
        }
    }
    ConfirmDialog(
        confirm == "stop", { confirm = null },
        onConfirm = { run { Api.post<Poll>("/polls/${poll.id}/stop") } },
        title = "Завершить опрос", body = "Голосовать и отменять голос после этого будет нельзя. Результаты увидят все.", confirmLabel = "Завершить",
    )
    ConfirmDialog(
        confirm == "remove", { confirm = null },
        onConfirm = { run { Api.delete<Unit>("/polls/${poll.id}"); null } },
        title = "Удалить опрос", body = "Опрос и все голоса будут удалены.", danger = true, confirmLabel = "Удалить",
    )
}

class DraftOption(val id: Int?, val text: String)

class PollDraft {
    var title by mutableStateOf("")
    var options by mutableStateOf(listOf(DraftOption(null, ""), DraftOption(null, "")))
    var closesAt by mutableStateOf<Long?>(null)

    private fun valid(): List<DraftOption>? {
        val opts = options.map { DraftOption(it.id, it.text.trim()) }.filter { it.text.isNotEmpty() }
        if (title.isBlank()) { toastError("Укажите вопрос опроса"); return null }
        if (opts.size < 2) { toastError("В опросе нужно минимум два варианта"); return null }
        return opts
    }

    fun payload(): JsonObject? {
        val opts = valid() ?: return null
        if (closesAt != null && closesAt!! * 1000 <= System.currentTimeMillis()) { toastError("Время окончания опроса должно быть в будущем"); return null }
        return buildJsonObject {
            put("title", title.trim())
            put("options", JsonArray(opts.map { JsonPrimitive(it.text) }))
            closesAt?.let { put("closes_at", it) }
        }
    }

    fun editPayload(original: PollDraft, reopen: Boolean): JsonObject? {
        val opts = valid() ?: return null
        val changedClose = closesAt != original.closesAt
        if (changedClose && closesAt != null && closesAt!! * 1000 <= System.currentTimeMillis()) { toastError("Время окончания опроса должно быть в будущем"); return null }
        return buildJsonObject {
            put("title", title.trim())
            put("options", JsonArray(opts.map { o -> buildJsonObject { o.id?.let { put("id", it) }; put("text", o.text) } }))
            if (changedClose) put("closes_at", closesAt?.let { JsonPrimitive(it) } ?: JsonNull)
            if (reopen) put("reopen", true)
        }
    }

    companion object {
        fun of(p: Poll) = PollDraft().apply {
            title = p.title
            options = p.options.map { DraftOption(it.id, it.text) }
            closesAt = p.closes_at
        }
    }
}

@Composable
fun PollBuilder(draft: PollDraft?, onChange: (PollDraft?) -> Unit, removable: Boolean = true) {
    if (draft == null) {
        ArButton("Добавить опрос", { onChange(PollDraft()) }, kind = ButtonKind.Ghost, icon = Lucide.Plus, small = true)
        return
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ОПРОС", color = Ar.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
            if (removable) ArButton("Убрать", { onChange(null) }, kind = ButtonKind.Ghost, small = true)
        }
        ArTextField(draft.title, { draft.title = it }, placeholder = "Вопрос", maxLength = 255, modifier = Modifier.padding(top = 8.dp))
        draft.options.forEachIndexed { i, o ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArTextField(o.text, { v -> draft.options = draft.options.mapIndexed { j, x -> if (j == i) DraftOption(x.id, v) else x } }, placeholder = "Вариант ${i + 1}", maxLength = 100, modifier = Modifier.weight(1f))
                if (draft.options.size > 2) IconBtn(Lucide.X, "Убрать вариант", { draft.options = draft.options.filterIndexed { j, _ -> j != i } }, size = 36.dp, iconSize = 16.dp)
            }
        }
        if (draft.options.size < MAX_OPTIONS) ArButton("Добавить вариант", { draft.options = draft.options + DraftOption(null, "") }, kind = ButtonKind.Ghost, icon = Lucide.Plus, small = true, modifier = Modifier.padding(top = 8.dp))
        Text("ЗАВЕРШИТЬ АВТОМАТИЧЕСКИ", color = Ar.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        DateTimeField(draft.closesAt, { draft.closesAt = it })
    }
}
