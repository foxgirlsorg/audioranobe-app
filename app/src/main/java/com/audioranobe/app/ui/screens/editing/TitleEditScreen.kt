package com.audioranobe.app.ui.screens.editing

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.Limits
import com.audioranobe.app.data.Author
import com.audioranobe.app.data.Labels
import com.audioranobe.app.data.LocalAuth
import com.audioranobe.app.data.Stores
import com.audioranobe.app.data.TitleFull
import com.audioranobe.app.data.TitleInfoBanner
import com.audioranobe.app.data.Volume
import com.audioranobe.app.ui.components.ArButton
import com.audioranobe.app.ui.components.ArImage
import com.audioranobe.app.ui.components.ArTabs
import com.audioranobe.app.ui.components.ArTextField
import com.audioranobe.app.ui.components.ArToggle
import com.audioranobe.app.ui.components.ButtonKind
import com.audioranobe.app.ui.components.ConfirmDialog
import com.audioranobe.app.ui.components.FieldLabel
import com.audioranobe.app.ui.components.GlassPanel
import com.audioranobe.app.ui.components.IconBtn
import com.audioranobe.app.ui.components.MarkdownEditor
import com.audioranobe.app.ui.components.PickedImage
import com.audioranobe.app.ui.components.SelectMenu
import com.audioranobe.app.ui.components.SelectOption
import com.audioranobe.app.ui.components.TabItem
import com.audioranobe.app.ui.components.TabsVariant
import com.audioranobe.app.ui.components.pickers.AuthorPicker
import com.audioranobe.app.ui.components.pickers.GenrePicker
import com.audioranobe.app.ui.components.pickers.NarratorPicker
import com.audioranobe.app.ui.components.pickers.NarratorRef
import com.audioranobe.app.ui.components.rememberImageCropper
import com.audioranobe.app.ui.components.rememberLoader
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.nav.LocalNav
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.pagePadding
import com.audioranobe.app.ui.screens.me.RequireAuth
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.toast.toast
import com.audioranobe.app.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar

private val CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR)

// ---------- app/title/[slug]/edit ----------

@Composable
fun TitleEditScreen(slug: String, initialTab: String?) {
    if (RequireAuth()) return
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val loader = rememberLoader(slug, keepOnReload = true) { Api.get<TitleFull>("/titles/${Routes.enc(slug)}") }
    val title = editGate(loader, { it.can_edit }, "У вас нет прав на редактирование этого тайтла.", "Вернуться к тайтлу", Routes.title(slug), "Не удалось загрузить тайтл") ?: return
    val canIllustrations = auth.can("illustrations.edit")
    var tab by remember { mutableStateOf(initialTab ?: "info") }
    val tabs = buildList {
        add(TabItem("info", "Инфо")); add(TabItem("artwork", "Оформление"))
        if (canIllustrations) add(TabItem("illustrations", "Иллюстрации"))
        add(TabItem("content", "Главы"))
    }
    val reload: suspend () -> Unit = { loader.reload() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        EditHeader("Редактирование тайтла", title.name, { nav.back() })
        ArTabs(tabs, tab, { tab = it }, Modifier.padding(bottom = 14.dp), variant = TabsVariant.Underline)
        when (tab) {
            "info" -> TitleInfoForm(title, loader.nonce, reload)
            "artwork" -> TitleArtwork(title, reload)
            "illustrations" -> IllustrationManager(title)
            "content" -> TitleContentManager(title, reload)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleInfoForm(title: TitleFull, nonce: Int, reload: suspend () -> Unit) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val isMod = auth.isMod
    val isAdmin = auth.can("titles.edit")
    val canInfoBanner = auth.can("titles.info_banner")
    val canVolumeLabel = auth.can("titles.volume_label")
    val scope = rememberCoroutineScope()
    val myNarrators by Stores.myNarrators.narrators.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Stores.myNarrators.ensureLoaded() }

    // The form is seeded once per loaded title (like formInit.current in the site).
    var name by remember(title.id) { mutableStateOf(title.name) }
    var slug by remember(title.id) { mutableStateOf(title.slug) }
    var alt by remember(title.id) { mutableStateOf(title.alt_names.joinToString(", ")) }
    var desc by remember(title.id) { mutableStateOf(title.description) }
    var year by remember(title.id) { mutableStateOf(title.year?.toString() ?: "") }
    var status by remember(title.id) { mutableStateOf(title.release_status) }
    var country by remember(title.id) { mutableStateOf(title.country) }
    var author by remember(title.id) { mutableStateOf(title.author?.let { Author(it.id, it.name, it.slug) }) }
    var genreIds by remember(title.id) { mutableStateOf(title.genres.map { it.id }) }
    var narrators by remember(title.id) { mutableStateOf(title.narrators.map { NarratorRef(it.id, it.slug, it.name, it.avatar_url) }) }
    var narrationStatuses by remember(title.id) { mutableStateOf(title.narrators.associate { it.id to (it.narration_status ?: "ongoing") }) }
    var isAi by remember(title.id) { mutableStateOf(title.is_ai) }
    var isNsfw by remember(title.id) { mutableStateOf(title.is_nsfw) }
    var confirmNsfw by remember { mutableStateOf(false) }
    var infoBanner by remember(title.id) { mutableStateOf(title.info_banner ?: TitleInfoBanner()) }
    var volumeLabel by remember(title.id) { mutableStateOf(title.volume_label) }
    var volumeLabelPlural by remember(title.id) { mutableStateOf(title.volume_label_plural) }
    var commentSub by remember(nonce) { mutableStateOf(title.comment_subscribed) }
    var commentSubBusy by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val aiLocked = title.is_ai
    val nsfwLocked = title.is_nsfw
    val losingAccess = !isMod && myNarrators.isNotEmpty() && narrators.none { n -> myNarrators.any { it.id == n.id } }

    GlassPanel(Modifier.fillMaxWidth()) {
        if (formError.isNotEmpty()) Text(formError, color = Ar.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        ArTextField(name, { name = it }, label = "Название", maxLength = 300)
        Spacer(Modifier.height(10.dp))
        ArTextField(slug, { slug = it }, label = "Slug (адрес страницы)", maxLength = 200, placeholder = "my-book-slug")
        Spacer(Modifier.height(10.dp))
        AuthorPicker(author, { author = it }, label = "Автор")
        Spacer(Modifier.height(10.dp))
        ArTextField(alt, { alt = it }, label = "Альт. названия (через запятую)", placeholder = "Оригинальное название, перевод…")
        Spacer(Modifier.height(10.dp))
        Row {
            ArTextField(year, { year = it }, Modifier.weight(1f), label = "Год", placeholder = "2020", keyboardType = KeyboardType.Number)
            Spacer(Modifier.width(10.dp))
            SelectMenu(status, Labels.statusValues.map { SelectOption(it, Labels.releaseStatus[it] ?: it) }, { status = it }, Modifier.weight(1f), label = "Статус тайтла")
        }
        Spacer(Modifier.height(10.dp))
        SelectMenu(country, Labels.countryValues.map { SelectOption(it, Labels.country[it] ?: it) }, { country = it }, label = "Страна")
        Spacer(Modifier.height(10.dp))
        NarratorPicker(narrators, { narrators = it }, label = "Чтецы")
        Text("Можно добавить любого существующего чтеца. Отмечать чтецов по главам можно в управлении главами.", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        if (losingAccess) Text("Вы убираете всех своих чтецов из этого тайтла — после сохранения вы потеряете к нему доступ.", color = Ar.amber, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        if (narrators.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FieldLabel("Статус озвучки")
            narrators.forEach { n ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.name, color = Ar.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    SelectMenu(narrationStatuses[n.id] ?: "ongoing", Labels.statusValues.map { SelectOption(it, Labels.narrationStatus[it] ?: it) }, { v -> narrationStatuses = narrationStatuses + (n.id to v) }, Modifier.width(170.dp), small = true)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MarkdownEditor(desc, { desc = it }, label = "Описание", maxLength = Limits.titleDescription, placeholder = "О чём эта книга? **Markdown** поддерживается.", slim = true)
        Spacer(Modifier.height(10.dp))
        GenrePicker(genreIds, { genreIds = it }, label = "Теги")
        Spacer(Modifier.height(10.dp))
        ArToggle(isAi, { isAi = it }, "Озвучено ИИ", "Отметьте, если главы озвучены синтезированным голосом.", enabled = !(aiLocked && !isMod))
        ArToggle(
            isNsfw,
            { next -> if (next) confirmNsfw = true else if (nsfwLocked && !isAdmin) toastError("Снять отметку 18+ может только администратор") else isNsfw = false },
            "Материал 18+",
            if (nsfwLocked && !isAdmin) "Отметка 18+ уже установлена. Снять её может только администратор." else "После включения отметку нельзя снять самостоятельно — только через администратора.",
            enabled = !(nsfwLocked && !isAdmin),
        )
        if (canInfoBanner) {
            ArToggle(infoBanner.enabled, { infoBanner = infoBanner.copy(enabled = it) }, "Инфо-баннер", "Заметка над тайтлом — её видят все посетители страницы.")
            if (infoBanner.enabled) Column(Modifier.padding(start = 8.dp, top = 6.dp)) {
                ArTextField(infoBanner.title, { infoBanner = infoBanner.copy(title = it) }, label = "Заголовок (необязательно)", maxLength = 120, placeholder = "Переозвучка в работе")
                Spacer(Modifier.height(8.dp))
                ArTextField(infoBanner.text, { infoBanner = infoBanner.copy(text = it) }, label = "Описание (необязательно)", maxLength = 500, placeholder = "Что важно знать читателям об этом тайтле?", singleLine = false, minLines = 2)
                Spacer(Modifier.height(8.dp))
                ArTextField(infoBanner.url, { infoBanner = infoBanner.copy(url = it) }, label = "Ссылка кнопки «Открыть» (необязательно)", maxLength = 2000, placeholder = "https://example.com/post", hint = "Без ссылки баннер показывается без кнопки.", keyboardType = KeyboardType.Uri)
                Spacer(Modifier.height(8.dp))
                FieldLabel("Как это выглядит")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ar.accent.copy(alpha = 0.1f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Info, null, tint = Ar.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        if (infoBanner.title.isNotBlank()) Text(infoBanner.title, color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (infoBanner.text.isNotBlank()) Text(infoBanner.text, color = Ar.textSecondary, fontSize = 12.sp)
                        if (infoBanner.title.isBlank() && infoBanner.text.isBlank()) Text("Заполните заголовок или описание…", color = Ar.textMuted, fontSize = 12.sp)
                    }
                    if (infoBanner.url.isNotBlank()) ArButton("Открыть", {}, small = true, kind = ButtonKind.Primary)
                }
            }
        }
        if (canVolumeLabel) {
            Spacer(Modifier.height(10.dp))
            Row {
                ArTextField(volumeLabel, { volumeLabel = it }, Modifier.weight(1f), label = "Название тома", maxLength = 40, placeholder = "Том", hint = "Слово, которым заменяется «Том» — например, «Арка».")
                Spacer(Modifier.width(10.dp))
                ArTextField(volumeLabelPlural, { volumeLabelPlural = it }, Modifier.weight(1f), label = "Во множественном числе", maxLength = 40, placeholder = "Тома", hint = "Например, «Арки».")
            }
        }
        SaveRow(saving, if (!isMod && title.mod_status == "approved") "Правки проходят модерацию" else null, onSave = {
            if (name.isBlank()) { formError = "Укажите название"; return@SaveRow }
            if (narrators.isEmpty()) { formError = "У тайтла должен остаться хотя бы один чтец"; return@SaveRow }
            var yearNum: Int? = null
            if (year.isNotBlank()) {
                val y = year.trim().toIntOrNull()
                if (y == null || y < 0 || y > CURRENT_YEAR + 5) { formError = "Похоже, год указан неверно"; return@SaveRow }
                yearNum = y
            }
            formError = ""; saving = true
            scope.launch {
                try {
                    val body = buildJsonObject {
                        put("narrator_ids", buildJsonArray { narrators.forEach { add(JsonPrimitive(it.id)) } })
                        put("name", name.trim()); put("slug", slug.trim())
                        put("alt_names", buildJsonArray { alt.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } })
                        put("author_id", author?.id?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("description", desc)
                        put("year", yearNum?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("release_status", status); put("country", country)
                        put("genre_ids", buildJsonArray { genreIds.forEach { add(JsonPrimitive(it)) } })
                        put("narration_statuses", buildJsonObject { narrationStatuses.forEach { (k, v) -> put(k.toString(), v) } })
                        if (isNsfw != title.is_nsfw) put("is_nsfw", isNsfw)
                        if (isAi != title.is_ai) put("is_ai", isAi)
                        if (canInfoBanner) put("info_banner", buildJsonObject { put("enabled", infoBanner.enabled); put("title", infoBanner.title); put("text", infoBanner.text); put("url", infoBanner.url) })
                        if (canVolumeLabel && volumeLabel.trim() != title.volume_label) put("volume_label", volumeLabel.trim())
                        if (canVolumeLabel && volumeLabelPlural.trim() != title.volume_label_plural) put("volume_label_plural", volumeLabelPlural.trim())
                    }
                    val res = Api.patch<AppliedResult>("/panel/titles/${title.id}", body)
                    toast(if (res.applied != false) "Изменения применены" else "Отправлено на модерацию")
                    val fresh = Api.get<TitleFull>("/titles/${title.id}")
                    nav.replace(Routes.title(fresh.slug))
                } catch (e: Exception) { toastError(e) }
                saving = false
            }
        })
    }
    Spacer(Modifier.height(12.dp))
    GlassPanel(Modifier.fillMaxWidth()) {
        ArToggle(commentSub, { next ->
            if (commentSubBusy) return@ArToggle
            commentSubBusy = true; commentSub = next
            scope.launch {
                try {
                    if (next) Api.put<Unit>("/titles/${title.id}/comment-subscription") else Api.delete<Unit>("/titles/${title.id}/comment-subscription")
                    toast(if (next) "Уведомления о комментариях включены" else "Уведомления о комментариях выключены")
                } catch (e: Exception) { commentSub = !next; toastError(e) }
                commentSubBusy = false
            }
        }, "Уведомлять о новых комментариях", "Уведомления о новых комментариях к этому тайтлу.", enabled = !commentSubBusy)
    }
    Spacer(Modifier.height(16.dp))
    DangerZone("title", title.id, title.name, Routes.catalog(), isDeleted = title.is_deleted, isHidden = title.is_hidden, onChanged = reload)
    ConfirmDialog(confirmNsfw, { confirmNsfw = false }, onConfirm = { isNsfw = true; confirmNsfw = false }, title = "Пометить как 18+", body = "Это действие необратимо: снять отметку 18+ сможет только администратор.", danger = true)
}

// ---------- components/TitleArtwork ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleArtwork(title: TitleFull, reload: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Int?>(null) }
    var volumeTarget by remember { mutableStateOf<Int?>(null) }

    fun send(url: String, picked: PickedImage, ok: String) {
        saving = true
        scope.launch {
            try { Api.upload<Unit>(url) { addPart(picked.part("file", "cover.webp")) }; toast(ok); reload() } catch (e: Exception) { toastError(e) }
            saving = false
        }
    }
    val coverCropper = rememberImageCropper(2, 3, 2048, 2048) { send("/panel/titles/${title.id}/cover", it, "Обложка обновлена") }
    val bgCropper = rememberImageCropper(3, 1, 2048, 2048) { send("/panel/titles/${title.id}/bg", it, "Фон обновлён") }
    val volumeCropper = rememberImageCropper(2, 3, 2048, 2048) { p -> volumeTarget?.let { send("/panel/volumes/$it/cover", p, "Обложка тома обновлена") } }

    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row { FieldLabel("Обложка"); Spacer(Modifier.weight(1f)); Text("2:3", color = Ar.textMuted, fontSize = 12.sp) }
        Box(Modifier.width(140.dp).aspectRatio(2f / 3f).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(10.dp)).background(Ar.surfaceRaised), contentAlignment = Alignment.Center) {
            if (title.cover_url != null) ArImage(title.cover_url, Modifier.fillMaxSize()) else Icon(Lucide.ImagePlus, null, tint = Ar.textMuted, modifier = Modifier.size(24.dp))
        }
        Text("Показывается в каталоге и на карточках. Лучше всего — вертикальное изображение от 600 пикселей по ширине.", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        ArButton(if (title.cover_url != null) "Сменить обложку" else "Загрузить обложку", { coverCropper.pick() }, icon = Lucide.ImagePlus, enabled = !saving, fullWidth = true)
    }
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row { FieldLabel("Фоновый баннер"); Spacer(Modifier.weight(1f)); Text("3:1", color = Ar.textMuted, fontSize = 12.sp) }
        Box(Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(10.dp)).background(Ar.surfaceRaised), contentAlignment = Alignment.Center) {
            if (title.bg_url != null) ArImage(title.bg_url, Modifier.fillMaxSize()) else Icon(Lucide.ImagePlus, null, tint = Ar.textMuted, modifier = Modifier.size(24.dp))
        }
        Text("Широкая подложка в шапке страницы тайтла. Она затемняется и размывается, поэтому мелкие детали и текст на ней не читаются.", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        ArButton(if (title.bg_url != null) "Сменить фон" else "Загрузить фон", { bgCropper.pick() }, icon = Lucide.ImagePlus, enabled = !saving, fullWidth = true)
    }
    if (title.volumes.isNotEmpty()) GlassPanel(Modifier.fillMaxWidth()) {
        Row { FieldLabel("Обложки томов"); Spacer(Modifier.weight(1f)); Text("2:3", color = Ar.textMuted, fontSize = 12.sp) }
        Text("Показывается в плеере, пока играет глава из этого тома. Без своей обложки том использует обложку тайтла.", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
            title.volumes.forEach { v ->
                VolumeCoverItem(v, "${title.volume_label} ${v.number}", saving, removing == v.id, Modifier.weight(1f), reload,
                    onCrop = { volumeTarget = v.id; volumeCropper.pick() },
                    onRemove = { removing = v.id; scope.launch { try { Api.delete<Unit>("/panel/volumes/${v.id}/cover"); toast("Обложка тома удалена"); reload() } catch (e: Exception) { toastError(e) }; removing = null } })
            }
        }
    }
}

@Composable
private fun VolumeCoverItem(volume: Volume, fallbackLabel: String, saving: Boolean, removing: Boolean, modifier: Modifier, reload: suspend () -> Unit, onCrop: () -> Unit, onRemove: () -> Unit) {
    val scope = rememberCoroutineScope()
    var label by remember(volume.name, fallbackLabel) { mutableStateOf(volume.name.ifBlank { fallbackLabel }) }
    fun commit() {
        val current = volume.name.ifBlank { fallbackLabel }
        val next = label.trim().ifEmpty { fallbackLabel }
        if (next == current) { label = next; return }
        scope.launch {
            try { Api.patch<Unit>("/panel/volumes/${volume.id}", buildJsonObject { put("name", if (next == fallbackLabel) "" else next) }); toast("Подпись обложки сохранена"); reload() }
            catch (e: Exception) { label = current; toastError(e) }
        }
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Ar.surfaceRaised), contentAlignment = Alignment.Center) {
            if (volume.cover_url != null) ArImage(volume.cover_thumb_url ?: volume.cover_url, Modifier.fillMaxSize()) else Icon(Lucide.ImagePlus, null, tint = Ar.textMuted, modifier = Modifier.size(20.dp))
            if (volume.cover_url != null) IconBtn(Lucide.X, "Удалить обложку тома", onRemove, Modifier.align(Alignment.TopEnd).padding(4.dp), size = 28.dp, iconSize = 13.dp, background = Ar.bg.copy(alpha = 0.7f), enabled = !removing)
        }
        Spacer(Modifier.height(6.dp))
        ArTextField(label, { label = it }, maxLength = 300, imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = { commit() })
        Spacer(Modifier.height(6.dp))
        ArButton(if (volume.cover_url != null) "Сменить" else "Загрузить", onCrop, kind = ButtonKind.Ghost, icon = Lucide.ImagePlus, small = true, enabled = !saving, fullWidth = true)
    }
}
