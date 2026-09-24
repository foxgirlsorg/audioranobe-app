package org.foxgirls.audioranobe.ui.screens.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.AuditEntry
import org.foxgirls.audioranobe.data.Banner
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.NarrationJob
import org.foxgirls.audioranobe.data.NarrationJobList
import org.foxgirls.audioranobe.data.Paginated
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArTabs
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.SelectMenu
import org.foxgirls.audioranobe.ui.components.SelectOption
import org.foxgirls.audioranobe.ui.components.StatusBadge
import org.foxgirls.audioranobe.ui.components.TabItem
import org.foxgirls.audioranobe.ui.components.TabsVariant
import org.foxgirls.audioranobe.ui.components.rememberImageCropper
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.catalog.Pagination
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable private data class ItemList<T>(val items: List<T> = emptyList())

// ---------- app/mod/banners ----------

@Composable
fun ModBannersPage() {
    val loader = rememberLoader(Unit, keepOnReload = true) { Api.get<ItemList<Banner>>("/mod/banners").items }
    var uploading by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Banner?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberImageCropper(3, 1, 2048, 2048) { picked ->
        uploading = true
        scope.launch { try { val created = Api.upload<Banner>("/mod/banners") { addPart(picked.part("file", "banner.webp")) }; loader.update { it + created }; toast("Баннер добавлен") } catch (e: Exception) { toastError(e) }; uploading = false }
    }
    fun patch(b: Banner, body: JsonObject) = scope.launch { try { val next = Api.patch<Banner>("/mod/banners/${b.id}", body); loader.update { l -> l.map { if (it.id == b.id) next else it } } } catch (e: Exception) { toastError(e) } }
    fun move(index: Int, delta: Int) {
        val items = loader.data ?: return
        val to = index + delta
        if (to < 0 || to >= items.size) return
        val next = items.toMutableList(); val tmp = next[index]; next[index] = next[to]; next[to] = tmp
        loader.set(next)
        scope.launch { try { Api.post<Unit>("/mod/banners/reorder", buildJsonObject { put("ids", buildJsonArray { next.forEach { add(JsonPrimitive(it.id)) } }) }) } catch (e: Exception) { loader.set(items); toastError(e) } }
    }
    ModHint("Карусель на главной под блоком «Новые тайтлы». Формат 3:1, 2048×683.")
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner()
        is Load.Err -> ErrorState(s.message, { loader.reload() })
        is Load.Ok -> s.data.forEachIndexed { i, b ->
            var url by remember(b.id, b.url) { mutableStateOf(b.url) }
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                ArImage(b.image_url, Modifier.fillMaxWidth().aspectRatio(3f), shape = RoundedCornerShape(8.dp))
                Spacer(Modifier.height(8.dp))
                ArTextField(url, { url = it }, placeholder = "ссылка (необязательно)", imeAction = ImeAction.Done, onImeAction = { if (url != b.url) patch(b, buildJsonObject { put("url", url) }) })
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArToggle(b.is_public ?: false, { on -> patch(b, buildJsonObject { put("is_public", on) }) }, "Публичный")
                    Spacer(Modifier.width(12.dp))
                    ArToggle(b.is_enabled ?: false, { on -> patch(b, buildJsonObject { put("is_enabled", on) }) }, "Включён")
                    Spacer(Modifier.weight(1f))
                    IconBtn(Lucide.ArrowUp, "Выше", { move(i, -1) }, size = 30.dp, iconSize = 14.dp, enabled = i > 0)
                    IconBtn(Lucide.ArrowDown, "Ниже", { move(i, 1) }, size = 30.dp, iconSize = 14.dp, enabled = i < s.data.size - 1)
                    IconBtn(Lucide.Trash2, "Удалить", { toDelete = b }, size = 30.dp, iconSize = 14.dp, tint = Ar.danger)
                }
            }
        }
    }
    ArButton(if (uploading) "Загрузка…" else "Добавить баннер", { picker.pick() }, kind = ButtonKind.Primary, icon = Lucide.Upload, busy = uploading, fullWidth = true)
    ConfirmDialog(toDelete != null, { toDelete = null }, onConfirm = {
        val b = toDelete ?: return@ConfirmDialog
        toDelete = null
        scope.launch { try { Api.delete<Unit>("/mod/banners/${b.id}"); loader.update { l -> l.filter { it.id != b.id } } } catch (e: Exception) { toastError(e) } }
    }, title = "Удалить баннер", body = "Удалить этот баннер?", danger = true, confirmLabel = "Удалить")
}

// ---------- app/mod/donations ----------

@Serializable private data class ServiceCfg(val url: String = "", val auto: Boolean = false, val blog: String? = null, val has_token: Boolean = false, val has_refresh: Boolean? = null, val expires_at: Long? = null, val has_device: Boolean? = null)
@Serializable private data class BadgeRef(val slug: String = "", val name: String = "")
@Serializable private data class GoalCfg(val enabled: Boolean = false, val target_cents: Long = 0, val period: String = "once", val title: String = "")
@Serializable private data class DonationsCfg(val kofi: ServiceCfg = ServiceCfg(), val boosty: ServiceCfg = ServiceCfg(), val badge_min_cents: Long = 0, val badge_slug: String = "", val badges: List<BadgeRef> = emptyList(), val goal: GoalCfg = GoalCfg())
@Serializable private data class DonationUser(val id: Int, val username: String = "")
@Serializable private data class DonationRow(val id: Int, val service: String = "", val amount: Double = 0.0, val currency: String = "", val donor_name: String = "", val message: String = "", val manual: Boolean = false, val user: DonationUser? = null)

private val SERVICE_LABEL = mapOf("kofi" to "Ko-fi", "boosty" to "Boosty", "other" to "Другое")

@Composable
private fun SectionHead(icon: ImageVector?, name: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = Ar.accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) }
        Text(name, color = Ar.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun ModDonationsPage() {
    val loader = rememberLoader(Unit, keepOnReload = true) { Api.get<DonationsCfg>("/admin/donations") }
    val rows = rememberLoader(Unit, keepOnReload = true) { runCatching { Api.get<ItemList<DonationRow>>("/admin/donations/list").items }.getOrDefault(emptyList()) }
    val scope = rememberCoroutineScope()
    val base = loader.data
    var cfg by remember(base) { mutableStateOf(base) }
    var kofiTok by remember { mutableStateOf("") }
    var boostyAuth by remember { mutableStateOf("") }
    var boostyDevice by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var addService by remember { mutableStateOf("kofi") }
    var addAmount by remember { mutableStateOf("") }
    var addCurrency by remember { mutableStateOf("USD") }
    var addDonor by remember { mutableStateOf("") }
    var addMessage by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    val c = cfg
    when (val s = loader.state) {
        is Load.Loading -> { CenterSpinner(); return }
        is Load.Err -> { ErrorState(s.message, { loader.reload() }); return }
        is Load.Ok -> {}
    }
    if (c == null) return

    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SectionHead(Lucide.Heart, "Ko-fi") { ArToggle(c.kofi.auto, { cfg = c.copy(kofi = c.kofi.copy(auto = it)) }, "Автотрекинг") }
        ArTextField(c.kofi.url, { cfg = c.copy(kofi = c.kofi.copy(url = it)) }, label = "Ссылка (ko-fi.com/…)", placeholder = "https://ko-fi.com/yourname", keyboardType = KeyboardType.Uri)
        Spacer(Modifier.height(8.dp))
        ArTextField(kofiTok, { kofiTok = it }, label = "Verification token (для вебхука)", placeholder = if (c.kofi.has_token) "сохранён — оставьте пустым" else "", password = true)
        Text("Вебхук для Ko-fi (укажите в настройках Ko-fi → Webhooks): ${Api.baseUrl}/webhooks/kofi", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SectionHead(Lucide.Heart, "Boosty") { ArToggle(c.boosty.auto, { cfg = c.copy(boosty = c.boosty.copy(auto = it)) }, "Автотрекинг") }
        ArTextField(c.boosty.url, { cfg = c.copy(boosty = c.boosty.copy(url = it)) }, label = "Ссылка (boosty.to/…)", placeholder = "https://boosty.to/yourname", keyboardType = KeyboardType.Uri)
        Spacer(Modifier.height(8.dp))
        ArTextField(c.boosty.blog ?: "", { cfg = c.copy(boosty = c.boosty.copy(blog = it)) }, label = "Имя блога (для автотрекинга)", placeholder = "yourname")
        Spacer(Modifier.height(8.dp))
        ArTextField(boostyAuth, { boostyAuth = it }, label = "Cookie «auth» (значение из DevTools)", placeholder = if (c.boosty.has_token) "сохранена — оставьте пустым" else "{\"accessToken\":\"…\"}", password = true)
        Spacer(Modifier.height(8.dp))
        ArTextField(boostyDevice, { boostyDevice = it }, label = "device_id (cookie _clientId, необязательно)", placeholder = if (c.boosty.has_device == true) "сохранён" else "подставится автоматически")
        Text(
            "Токен обновляется автоматически по refresh-токену из cookie. " + (if (c.boosty.has_token) (c.boosty.expires_at?.let { "Текущий действует до ${Fmt.dateTime(it * 1000)}. " } ?: "Токен сохранён. ") else "Cookie не задана — автотрекинг выключен. ") + "У Boosty нет официального API, запрос неофициальный и может ломаться; ручное внесение ниже надёжнее.",
            color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp),
        )
    }
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SectionHead(null, "Бейдж и цель сбора")
        SelectMenu(c.badge_slug, listOf(SelectOption("", "Не выдавать")) + c.badges.map { SelectOption(it.slug, it.name) }, { cfg = c.copy(badge_slug = it) }, label = "Бейдж за пожертвование")
        Spacer(Modifier.height(8.dp))
        ArTextField((c.badge_min_cents / 100.0).let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() }, { v -> v.replace(',', '.').toDoubleOrNull()?.let { cfg = c.copy(badge_min_cents = Math.round(it * 100)) } }, label = "Минимум для бейджа, $", keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(10.dp))
        ArToggle(c.goal.enabled, { cfg = c.copy(goal = c.goal.copy(enabled = it)) }, "Показывать цель в футере")
        Spacer(Modifier.height(8.dp))
        ArTextField(c.goal.title, { cfg = c.copy(goal = c.goal.copy(title = it)) }, label = "Название цели", placeholder = "Серверы на месяц")
        Spacer(Modifier.height(8.dp))
        Row {
            ArTextField((c.goal.target_cents / 100).toString(), { v -> v.toLongOrNull()?.let { cfg = c.copy(goal = c.goal.copy(target_cents = it * 100)) } }, Modifier.weight(1f), label = "Цель, $", keyboardType = KeyboardType.Number)
            Spacer(Modifier.width(8.dp))
            SelectMenu(c.goal.period, listOf(SelectOption("once", "Разовый"), SelectOption("weekly", "за неделю"), SelectOption("monthly", "за месяц")), { cfg = c.copy(goal = c.goal.copy(period = it)) }, Modifier.weight(1f), label = "Период")
        }
    }
    ArButton("Сохранить настройки", kind = ButtonKind.Primary, icon = Lucide.Save, busy = saving, fullWidth = true, onClick = {
        saving = true
        scope.launch {
            try {
                val d = Api.put<DonationsCfg>("/admin/donations", buildJsonObject {
                    put("kofi", buildJsonObject { put("url", c.kofi.url); put("auto", c.kofi.auto); if (kofiTok.isNotEmpty()) put("token", kofiTok) })
                    put("boosty", buildJsonObject { put("url", c.boosty.url); put("auto", c.boosty.auto); put("blog", c.boosty.blog ?: ""); if (boostyAuth.isNotEmpty()) put("auth", boostyAuth); if (boostyDevice.isNotEmpty()) put("device_id", boostyDevice) })
                    put("badge_min_cents", c.badge_min_cents); put("badge_slug", c.badge_slug)
                    put("goal", buildJsonObject { put("enabled", c.goal.enabled); put("target_cents", c.goal.target_cents); put("period", c.goal.period); put("title", c.goal.title) })
                })
                loader.set(d); kofiTok = ""; boostyAuth = ""; boostyDevice = ""; toast("Сохранено")
            } catch (e: Exception) { toastError(e) }
            saving = false
        }
    })
    Spacer(Modifier.height(16.dp))
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        SectionHead(Lucide.Plus, "Внести вручную")
        Row {
            SelectMenu(addService, listOf(SelectOption("kofi", "Ko-fi"), SelectOption("boosty", "Boosty"), SelectOption("other", "Другое")), { addService = it }, Modifier.weight(1f), label = "Сервис")
            Spacer(Modifier.width(8.dp))
            ArTextField(addAmount, { addAmount = it }, Modifier.weight(1f), label = "Сумма", keyboardType = KeyboardType.Decimal)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            ArTextField(addCurrency, { addCurrency = it.uppercase() }, Modifier.weight(1f), label = "Валюта")
            Spacer(Modifier.width(8.dp))
            ArTextField(addDonor, { addDonor = it }, Modifier.weight(1f), label = "Имя донатера")
        }
        Spacer(Modifier.height(8.dp))
        ArTextField(addMessage, { addMessage = it }, label = "Комментарий (ник для привязки к аккаунту)", placeholder = "ник на сайте")
        Spacer(Modifier.height(10.dp))
        ArButton("Добавить", kind = ButtonKind.Primary, icon = Lucide.Plus, enabled = addAmount.isNotBlank() && !adding, busy = adding, onClick = {
            adding = true
            scope.launch {
                try {
                    Api.post<Unit>("/admin/donations", buildJsonObject { put("service", addService); put("amount", addAmount.replace(',', '.').toDoubleOrNull() ?: 0.0); put("currency", addCurrency); put("donor_name", addDonor); put("message", addMessage) })
                    addService = "kofi"; addAmount = ""; addCurrency = "USD"; addDonor = ""; addMessage = ""; toast("Пожертвование добавлено"); rows.reload()
                } catch (e: Exception) { toastError(e) }
                adding = false
            }
        })
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        SectionHead(null, "Последние пожертвования")
        val items = rows.data ?: emptyList()
        if (items.isEmpty()) Text("Пока пусто.", color = Ar.textMuted, fontSize = 13.sp)
        items.forEachIndexed { i, r ->
            if (i > 0) HairlineDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${Fmt.trimNum(r.amount)} ${r.currency}", color = Ar.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(SERVICE_LABEL[r.service] ?: r.service, color = Ar.textMuted, fontSize = 12.sp)
                        if (!r.manual) { Spacer(Modifier.width(6.dp)); Tag("авто", Ar.blue) }
                    }
                    Text(r.donor_name.ifBlank { "—" } + (r.user?.let { " · @${it.username}" } ?: ""), color = Ar.textSecondary, fontSize = 12.sp)
                }
                IconBtn(Lucide.Trash2, "Удалить", { scope.launch { try { Api.delete<Unit>("/admin/donations/${r.id}"); rows.update { l -> l.filter { it.id != r.id } } } catch (e: Exception) { toastError(e) } } }, size = 30.dp, iconSize = 14.dp, tint = Ar.danger)
            }
            fun link(username: String) = scope.launch { try { val u = Api.post<DonationRow>("/admin/donations/${r.id}/link", buildJsonObject { put("username", username) }); rows.update { l -> l.map { if (it.id == r.id) u else it } }; linkInput = linkInput - r.id; toast(if (username.isNotEmpty()) "Привязано" else "Отвязано") } catch (e: Exception) { toastError(e) } }
            if (r.user != null) ArButton("Отвязать", { link("") }, kind = ButtonKind.Ghost, icon = Lucide.X, small = true)
            else Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ArTextField(linkInput[r.id] ?: "", { linkInput = linkInput + (r.id to it) }, Modifier.weight(1f), placeholder = "привязать к нику", imeAction = ImeAction.Done, onImeAction = { linkInput[r.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { link(it) } })
                IconBtn(Lucide.Link2, "Привязать", { linkInput[r.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { link(it) } }, size = 36.dp, iconSize = 15.dp, enabled = !linkInput[r.id].isNullOrBlank())
            }
        }
    }
}

// ---------- app/mod/audit ----------

private val AUDIT_LABELS = mapOf(
    "comment.edit" to "Содержимое комментария изменено", "comment.delete" to "Комментарий удалён", "comment.purge" to "Комментарий стёрт навсегда",
    "user.ban" to "Пользователь ограничен", "user.unban" to "Ограничение снято", "user.role" to "Роль пользователя изменена", "user.edit" to "Профиль изменён",
    "user.delete" to "Пользователь удалён", "user.image" to "Изображение профиля изменено", "user.email_change" to "Email изменён", "user.password_reset" to "Сброшен пароль",
    "user.totp_reset" to "Сброшена 2FA", "user.skip_moderation" to "Право пропуска модерации изменено", "title.edit" to "Тайтл изменён", "title.delete" to "Тайтл удалён",
    "title.hide" to "Тайтл скрыт", "title.unhide" to "Тайтл показан", "title.review_check" to "Проверка тайтла", "chapter.create" to "Глава создана", "chapter.edit" to "Глава изменена",
    "chapter.delete" to "Глава удалена", "chapter.purge" to "Глава стёрта навсегда", "chapter.audio" to "Загружено аудио главы", "chapter.bulk_edit" to "Массовое изменение глав",
    "chapter.bulk_upload" to "Массовая загрузка глав", "volume.edit" to "Том изменён", "volume.delete" to "Том удалён", "volume.cover" to "Обложка тома заменена",
    "volume.cover_delete" to "Обложка тома удалена", "version.create" to "Альт-озвучка создана", "version.edit" to "Альт-озвучка изменена", "version.delete" to "Альт-озвучка удалена",
    "version.rename_main" to "Название основной озвучки изменено", "title.cover" to "Обложка тайтла заменена", "title.bg" to "Фон тайтла заменён", "narrator.avatar" to "Аватар чтеца заменён",
    "narrator.cover" to "Обложка чтеца заменена", "node.create" to "Нода авторизована", "node.enable" to "Нода включена", "node.disable" to "Нода отключена", "node.delete" to "Нода удалена",
    "post.edit" to "Пост чтеца изменён", "post.delete" to "Пост чтеца удалён", "post.hide" to "Пост чтеца скрыт", "post.unhide" to "Пост чтеца показан",
    "illustration.add" to "Иллюстрация добавлена", "illustration.edit" to "Иллюстрация изменена", "illustration.delete" to "Иллюстрация удалена", "genre.edit" to "Тег изменён",
    "genre.delete" to "Тег удалён", "role.create" to "Роль создана", "role.update" to "Роль изменена", "role.delete" to "Роль удалена", "word.create" to "Стоп-слово добавлено",
    "word.update" to "Стоп-слово изменено", "word.delete" to "Стоп-слово удалено", "request.approve" to "Заявка одобрена", "request.reject" to "Заявка отклонена",
    "report.resolve" to "Жалоба обработана", "dmca.resolve" to "DMCA-обращение обработано", "broadcast.send" to "Рассылка отправлена", "narrator.edit" to "Озвучка изменена",
    "narrator.delete" to "Озвучка удалена", "author.update" to "Автор изменён", "author.delete" to "Автор удалён",
)
private val AUDIT_ICONS: List<Pair<String, ImageVector>> = listOf(
    "comment.delete" to Lucide.MessageSquareX, "comment.purge" to Lucide.Trash2, "comment" to Lucide.MessageSquare, "user.ban" to Lucide.Ban, "user.unban" to Lucide.ShieldCheck,
    "user.role" to Lucide.UserCog, "user.delete" to Lucide.UserX, "user.image" to Lucide.Image, "user" to Lucide.User, "title.cover" to Lucide.Image, "title.bg" to Lucide.Image,
    "title" to Lucide.BookMarked, "chapter.audio" to Lucide.Radio, "chapter" to Lucide.BookMarked, "volume" to Lucide.BookMarked, "version" to Lucide.BookMarked, "genre" to Lucide.BookMarked,
    "illustration" to Lucide.Image, "post" to Lucide.MessageSquare, "narrator.avatar" to Lucide.Image, "narrator.cover" to Lucide.Image, "node" to Lucide.Radio, "word" to Lucide.Shield,
    "role" to Lucide.Users, "request" to Lucide.ShieldCheck, "report" to Lucide.Shield, "dmca" to Lucide.Shield, "broadcast" to Lucide.Radio, "narrator" to Lucide.User, "author" to Lucide.User,
    "banner" to Lucide.Image, "badge" to Lucide.Image,
)
private val HANDLED_KEYS = setOf("old_body", "new_body", "source", "author_id", "changes", "fields", "username")
private val AUDIT_FIELDS = mapOf(
    "username" to "Имя пользователя", "displayName" to "Отображаемое имя", "email" to "Email", "bio" to "Био", "role" to "Роль", "name" to "Название", "slug" to "Ссылка (slug)", "year" to "Год",
    "releaseStatus" to "Статус выпуска", "country" to "Страна", "isNsfw" to "NSFW", "isAi" to "AI-озвучка", "isSensitive" to "Чувствительный тег", "isHidden" to "Скрыт", "isVerified" to "Верифицирован",
    "modStatus" to "Статус модерации", "description" to "Описание", "altNames" to "Альт. названия", "links" to "Ссылки", "socials" to "Соцсети", "word" to "Слово", "caption" to "Подпись",
    "number" to "Номер", "numberEnd" to "Конец диапазона", "versionName" to "Название озвучки", "title" to "Заголовок", "volume" to "Том", "skipModeration" to "Пропуск модерации",
)

private fun auditIcon(action: String) = AUDIT_ICONS.firstOrNull { (p, _) -> action == p || action.startsWith("$p.") }?.second ?: Lucide.Activity
private fun fmtAuditValue(v: kotlinx.serialization.json.JsonElement?): String = when (v) {
    null, JsonNull -> "∅"
    is JsonArray -> if (v.isEmpty()) "∅" else v.joinToString(", ") { fmtAuditValue(it) }
    is JsonPrimitive -> v.content.ifEmpty { "∅" }
    else -> v.toString()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModAuditPage() {
    val nav = LocalNav.current
    var actorInput by remember { mutableStateOf("") }
    var actor by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    LaunchedEffect(actorInput) { delay(300); actor = actorInput.trim(); page = 1 }
    val loader = rememberLoader(actor, page) { Api.get<Paginated<AuditEntry>>("/mod/audit", mapOf("page" to page, "actor" to actor.ifBlank { null })) }
    ModSearch(actorInput, { actorInput = it }, "Фильтр по модератору…")
    PagedBox(loader, empty = { EmptyState(if (actor.isNotEmpty()) "Ничего не найдено" else "Пока ничего не записано", if (actor.isNotEmpty()) "Попробуйте другое имя модератора." else "Действия модераторов будут появляться здесь.", Lucide.ScrollText) }, onPage = { page = it }) { items ->
        items.forEach { en ->
            val d = en.details
            val changes = (d["changes"] as? JsonObject)?.entries?.filter { (_, c) -> c is JsonObject && "new" in c } ?: emptyList()
            val bits = d.entries.filter { (k, v) -> k !in HANDLED_KEYS && v is JsonPrimitive }.map { (k, v) -> "$k: ${(v as JsonPrimitive).content}" }
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(auditIcon(en.action), null, tint = Ar.accent, modifier = Modifier.size(17.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Событие #${en.id} · ${Fmt.dateTime(en.created_at)}", color = Ar.textMuted, fontSize = 11.sp)
                        Text(AUDIT_LABELS[en.action] ?: en.action, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (bits.isNotEmpty()) Text(bits.joinToString(" · "), color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (en.actor != null) Chip(Lucide.Shield, en.actor.username) { nav.go(Routes.user(en.actor.id)) } else Chip(Lucide.Shield, "модератор удалён", null)
                    if (en.target != null) Chip(Lucide.User, en.target.username) { nav.go(Routes.user(en.target.id)) }
                    if (en.source != null) Chip(null, en.source.label + " →") { Links.open(nav, en.source.href) }
                }
                changes.forEach { (field, c) ->
                    val co = c as JsonObject
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                        Text(AUDIT_FIELDS[field] ?: field, color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.width(120.dp))
                        val nv = co["new"]
                        if (nv is JsonPrimitive && nv.content in listOf("true", "false")) Text(if (nv.content == "true") "включено" else "выключено", color = if (nv.content == "true") Ar.ok else Ar.danger, fontSize = 12.sp)
                        else Text((co["old"]?.takeIf { it !is JsonNull && fmtAuditValue(it) != "∅" }?.let { "${fmtAuditValue(it)} → " } ?: "") + fmtAuditValue(nv), color = Ar.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
                val oldBody = (d["old_body"] as? JsonPrimitive)?.content
                val newBody = (d["new_body"] as? JsonPrimitive)?.content
                if (oldBody != null) { Text("БЫЛО", color = Ar.textMuted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp)); Text(oldBody.ifBlank { "—" }, color = Ar.textSecondary, fontSize = 12.sp, maxLines = 8, overflow = TextOverflow.Ellipsis) }
                if (newBody != null) { Text("СТАЛО", color = Ar.ok, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp)); Text(newBody.ifBlank { "—" }, color = Ar.text, fontSize = 12.sp, maxLines = 8, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun Chip(icon: ImageVector?, text: String, onClick: (() -> Unit)?) {
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Ar.fill04).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = Ar.textMuted, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = if (onClick != null) Ar.accentHover else Ar.textMuted, fontSize = 12.sp)
    }
}

// ---------- app/mod/tasks ----------

@Composable
fun ModTasksPage() {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val canRequeue = auth.can("nodes.manage")
    var status by remember { mutableStateOf("error") }
    var page by remember { mutableIntStateOf(1) }
    val loader = rememberLoader(status, page, keepOnReload = true) { Api.get<NarrationJobList>("/mod/narration-jobs", mapOf("status" to status.ifBlank { null }, "page" to page)) }
    var retrying by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val counts = loader.data?.counts
    ArTabs(listOf(TabItem("error", "С ошибкой", counts?.error ?: 0), TabItem("queued", "В очереди", counts?.queued ?: 0), TabItem("processing", "В работе", counts?.processing ?: 0), TabItem("done", "Готово", counts?.done ?: 0), TabItem("", "Все")), status, { status = it; page = 1 }, Modifier.padding(bottom = 12.dp), variant = TabsVariant.Underline)
    fun run(job: NarrationJob, action: String) {
        val key = "${job.kind}-${job.id}"
        if (retrying != null) return
        retrying = key
        scope.launch {
            try { Api.post<Unit>((if (job.kind == "convert") "/mod/convert-jobs" else "/mod/narration-jobs") + "/${job.id}/$action"); toast(if (action == "requeue") "Задача возвращена в очередь" else "Задача перезапущена"); loader.reload() } catch (e: Exception) { toastError(e) }
            retrying = null
        }
    }
    when (val s = loader.state) {
        is Load.Loading -> CenterSpinner()
        is Load.Err -> ErrorState(s.message, { loader.reload() })
        is Load.Ok -> if (s.data.items.isEmpty()) EmptyState("Задач нет", "Здесь появятся задачи.", Lucide.ListChecks) else {
            s.data.items.forEach { job ->
                val key = "${job.kind}-${job.id}"
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(job.title.name, color = Ar.accentHover, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { nav.go(Routes.title(job.title.slug)) })
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(if (job.kind == "convert") "Конвертация" else "Озвучка", if (job.kind == "convert") Ar.blue else Ar.accent)
                        Text("Том ${job.volume.ifBlank { "—" }} · Глава ${Fmt.chapterNumber(job.number, null)}" + (if (job.name.isNotBlank()) " · ${job.name}" else ""), color = Ar.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (job.error.isNotBlank()) Text(job.error, color = Ar.danger, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (job.status == "processing" && job.claimed_at != null) Text("в работе ${Fmt.timeAgo(job.claimed_at)}", color = Ar.textMuted, fontSize = 12.sp)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (job.attempts > 0) Text("попыток: ${job.attempts}", color = Ar.textMuted, fontSize = 12.sp)
                        StatusBadge(job.status)
                        Spacer(Modifier.weight(1f))
                        if (job.status == "error") ArButton("Перезапустить", { run(job, "retry") }, kind = ButtonKind.Ghost, icon = Lucide.RotateCcw, small = true, busy = retrying == key)
                        if (job.status == "processing" && canRequeue) ArButton("В очередь", { run(job, "requeue") }, kind = ButtonKind.Ghost, icon = Lucide.Undo2, small = true, busy = retrying == key)
                    }
                }
            }
            Pagination(s.data.page, s.data.total, s.data.per_page) { page = it }
        }
    }
}
