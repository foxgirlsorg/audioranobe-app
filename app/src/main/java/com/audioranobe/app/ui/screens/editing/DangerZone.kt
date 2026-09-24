package com.audioranobe.app.ui.screens.editing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioranobe.app.core.Api
import com.audioranobe.app.data.LocalAuth
import com.audioranobe.app.ui.components.ArButton
import com.audioranobe.app.ui.components.ArModal
import com.audioranobe.app.ui.components.ArTextField
import com.audioranobe.app.ui.components.ButtonKind
import com.audioranobe.app.ui.components.ConfirmDialog
import com.audioranobe.app.ui.components.GlassPanel
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.nav.LocalNav
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.toast.toast
import com.audioranobe.app.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class AppliedResult(val applied: Boolean? = null)

@Serializable
private data class WipedResult(val wiped: Int = 0)

private val LABELS = mapOf("title" to "тайтл", "narrator" to "чтеца", "author" to "автора", "chapter" to "главу", "comment" to "комментарий")

/** components/DangerZone: delete / hide / restore / purge / wipe-audio actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DangerZone(kind: String, id: Int, name: String, redirectTo: String, modifier: Modifier = Modifier, isDeleted: Boolean = false, isHidden: Boolean = false, onChanged: suspend () -> Unit = {}) {
    val auth = LocalAuth.current
    val nav = LocalNav.current
    val isMod = auth.isMod
    val trashView = auth.can("trash.view.$kind")
    val canRestore = trashView && auth.can("trash.restore")
    val canPurge = trashView && auth.can("trash.purge")
    val canHide = kind == "title" && auth.can("titles.hide")
    val canWipeAudio = kind == "title" && auth.can("titles.wipe_audio")
    val canDelete = if (kind == "title" || kind == "narrator") true else isMod
    if (!canDelete && !canRestore && !canPurge && !canHide && !canWipeAudio) return
    val label = LABELS[kind] ?: kind
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var wipePassword by remember { mutableStateOf("") }

    fun run(block: suspend () -> Unit) { busy = true; scope.launch { try { block() } catch (e: Exception) { toastError(e) }; busy = false } }

    GlassPanel(modifier.fillMaxWidth(), borderColor = Ar.danger.copy(alpha = 0.4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.TriangleAlert, null, tint = Ar.danger, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Опасная зона", color = Ar.danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            when {
                isDeleted && (canRestore || canPurge) -> "Этот $label удалён и не виден на сайте. Восстановить его можно только отсюда или из корзины."
                isHidden && canHide -> "Этот $label скрыт и никому не виден, кроме модераторов."
                else -> "Этот $label исчезнет с сайта, из каталога и из списков пользователей. Отменить удаление нельзя."
            },
            color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isDeleted && canRestore) ArButton("Восстановить", { run { Api.post<Unit>("/mod/trash/$kind/$id/restore"); toast("Восстановлено"); onChanged() } }, icon = Lucide.RotateCcw, small = true, busy = busy)
            if (!isDeleted && canHide) ArButton(if (isHidden) "Показать" else "Скрыть", { run { Api.post<Unit>("/titles/$id/hide"); toast(if (isHidden) "Тайтл снова виден всем" else "Тайтл скрыт"); onChanged() } }, icon = if (isHidden) Lucide.Eye else Lucide.EyeOff, small = true, busy = busy)
            if (!isDeleted && canWipeAudio) ArButton("Стереть озвучку", { confirm = "wipe_audio" }, kind = ButtonKind.Danger, icon = Lucide.VolumeX, small = true, busy = busy)
            if (!isDeleted && canDelete) ArButton("Удалить $label", { confirm = "delete" }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true, busy = busy)
            if (canPurge) ArButton("Стереть навсегда", { confirm = "purge" }, kind = ButtonKind.Danger, icon = Lucide.Trash2, small = true, busy = busy)
        }
    }

    ConfirmDialog(
        confirm == "delete", { confirm = null },
        onConfirm = {
            run {
                val path = if (kind == "title" || kind == "narrator") "/panel/${kind}s/$id" else "/mod/${kind}s/$id"
                val res = Api.delete<AppliedResult?>(path)
                toast(if (res?.applied == false) "Удаление отправлено на модерацию" else "Удалено")
                confirm = null; nav.replace(redirectTo)
            }
        },
        title = "Удалить $label?", body = "«$name» будет удалён безвозвратно. Отменить это действие нельзя.", danger = true, confirmLabel = "Удалить",
    )
    ConfirmDialog(
        confirm == "purge", { confirm = null },
        onConfirm = { run { Api.delete<Unit>("/mod/trash/$kind/$id"); toast("Удалено навсегда"); confirm = null; nav.replace(redirectTo) } },
        title = "Стереть навсегда?", body = "«$name» и все связанные файлы будут удалены из базы окончательно. Восстановить будет нечего.", danger = true, confirmLabel = "Стереть",
    )
    if (confirm == "wipe_audio") ArModal(true, { confirm = null; wipePassword = "" }, "Стереть озвучку?") {
        Text("Аудиофайлы всех глав «$name» будут удалены с хранилища. Тайтл, главы, тексты и статистика пользователей останутся. Отменить нельзя — главы придётся переозвучивать заново.", color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(10.dp))
        ArTextField(wipePassword, { wipePassword = it }, placeholder = "Ваш пароль", password = true)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            ArButton("Отмена", { confirm = null; wipePassword = "" }, kind = ButtonKind.Ghost)
            Spacer(Modifier.width(8.dp))
            ArButton("Стереть", kind = ButtonKind.Danger, busy = busy, onClick = {
                if (wipePassword.isEmpty()) { toastError("Введите пароль"); return@ArButton }
                run {
                    val res = Api.delete<WipedResult>("/mod/titles/$id/audio", buildJsonObject { put("password", wipePassword) })
                    toast("Озвучка удалена (${res.wiped} гл.)"); confirm = null; wipePassword = ""; onChanged()
                }
            })
        }
    }
}
