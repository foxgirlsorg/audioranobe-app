package org.foxgirls.audioranobe.ui.screens.editing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.CompositionLocalProvider
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.LocalFlatUi
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.data.AuthorFull
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.NarratorFull
import org.foxgirls.audioranobe.data.NarratorMember
import org.foxgirls.audioranobe.data.UserSearchHit
import org.foxgirls.audioranobe.ui.components.NarratorStatsDashboard
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArTabs
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.Skeleton
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.FieldLabel
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.Loader
import org.foxgirls.audioranobe.ui.components.MarkdownEditor
import org.foxgirls.audioranobe.ui.components.Spinner
import org.foxgirls.audioranobe.ui.components.TabItem
import org.foxgirls.audioranobe.ui.components.TabsVariant
import org.foxgirls.audioranobe.ui.components.UserAvatar
import org.foxgirls.audioranobe.ui.components.pickers.SocialsEditor
import org.foxgirls.audioranobe.ui.components.pickers.UserPicker
import org.foxgirls.audioranobe.ui.components.rememberImageCropper
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.pagePadding
import org.foxgirls.audioranobe.ui.screens.content.NarratorPosts
import org.foxgirls.audioranobe.ui.screens.me.RequireAuth
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Header shared by the edit pages: back button, big heading, subject name. */
@Composable
fun EditHeader(heading: String, subject: String?, onBack: () -> Unit, avatarUrl: String? = null, avatarIcon: ImageVector? = null, tags: List<String> = emptyList()) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Lucide.ArrowLeft, "Назад", onBack, tint = Ar.text)
        if (avatarIcon != null) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Ar.surfaceRaised), contentAlignment = Alignment.Center) {
                if (avatarUrl != null) ArImage(avatarUrl, Modifier.fillMaxSize(), shape = CircleShape) else Icon(avatarIcon, null, tint = Ar.textMuted, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(heading, color = Ar.white, fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp)
            if (subject != null) Text(subject, color = Ar.textMuted, fontSize = 13.sp)
            if (tags.isNotEmpty()) Text(tags.joinToString("  ·  "), color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Screen frame for the redesigned edit pages: scrolling column, optional bar pinned to the bottom, flat button style. */
@Composable
fun EditScaffold(bar: (@Composable BoxScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalFlatUi provides true) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding()).padding(bottom = if (bar != null) 84.dp else 0.dp),
                content = content,
            )
            bar?.invoke(this)
        }
    }
}

/** A titled group of fields (the site's page.module.css `.section`), stacked on a phone. */
@Composable
fun EditSection(title: String, desc: String? = null, first: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (!first) HairlineDivider(Modifier.padding(vertical = 18.dp))
        Text(title, color = Ar.white, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (desc != null) Text(desc, color = Ar.textMuted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Sticky "unsaved changes" bar. */
@Composable
fun BoxScope.EditSaveBar(saving: Boolean, note: String?, error: String?, onReset: () -> Unit, onSave: () -> Unit) {
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp)).background(Ar.surfaceRaised).border(1.dp, Ar.border, RoundedCornerShape(14.dp)).padding(12.dp),
    ) {
        if (error != null) Text(error, color = Ar.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        else Text(
            "Есть несохранённые изменения" + (note?.let { " · $it" } ?: ""),
            color = Ar.text, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArButton("Сбросить", onReset, kind = ButtonKind.Ghost, enabled = !saving)
            ArButton(if (saving) "Сохраняем…" else "Сохранить изменения", onSave, Modifier.weight(1f), kind = ButtonKind.Primary, busy = saving)
        }
    }
}

/** Placeholder for an edit page: [EditHeader] (back, avatar, heading), a tab strip, then a form panel that fills the rest of the screen. */
@Composable
private fun EditPageSkeleton() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(40.dp), CircleShape)
            Spacer(Modifier.width(4.dp))
            Skeleton(Modifier.size(44.dp), CircleShape)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Skeleton(Modifier.width(180.dp).height(20.dp))
                Skeleton(Modifier.width(120.dp).height(13.dp))
            }
        }
        Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Skeleton(Modifier.width(84.dp).height(30.dp), RoundedCornerShape(15.dp)) }
        }
        GlassPanel(Modifier.fillMaxWidth().weight(1f)) {
            repeat(3) { i ->
                Skeleton(Modifier.padding(top = if (i == 0) 0.dp else 14.dp).width(90.dp).height(11.dp))
                Skeleton(Modifier.padding(top = 8.dp).fillMaxWidth().height(44.dp), RoundedCornerShape(10.dp))
            }
            Skeleton(Modifier.padding(top = 18.dp).width(140.dp).height(44.dp), RoundedCornerShape(10.dp))
        }
    }
}

/** Renders the loading / error / no-access states of an edit page; returns the loaded value or null. */
@Composable
fun <T> editGate(loader: Loader<T>, canEdit: (T) -> Boolean, noAccessBody: String, backLabel: String, backRoute: String, errorTitle: String): T? {
    val nav = LocalNav.current
    when (val s = loader.state) {
        is Load.Loading -> { EditPageSkeleton(); return null }
        is Load.Err -> { Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) { ErrorState(s.message, { loader.reload() }, title = errorTitle) }; return null }
        is Load.Ok -> if (!canEdit(s.data)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) {
                EmptyState("Нет доступа", noAccessBody, Lucide.Lock, action = { ArButton(backLabel, { nav.replace(backRoute) }, kind = ButtonKind.Ghost, icon = Lucide.ArrowLeft) })
            }
            return null
        } else return s.data
    }
}

@Composable
fun SaveRow(saving: Boolean, note: String?, onSave: () -> Unit, label: String = "Сохранить изменения") {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ArButton(if (saving) "Сохраняем…" else label, onSave, kind = ButtonKind.Primary, busy = saving)
        if (note != null) { Spacer(Modifier.width(10.dp)); Text(note, color = Ar.textMuted, fontSize = 12.sp) }
    }
}

// ---------- app/author/[id]/edit ----------

@Composable
fun AuthorEditScreen(id: Int) {
    if (RequireAuth()) return
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val loader = rememberLoader(id) { Api.get<AuthorFull>("/authors/$id") }
    val a = editGate(loader, { it.can_edit }, "У вас нет прав на редактирование этого автора.", "Вернуться к автору", Routes.author(id), "Не удалось загрузить автора") ?: return
    var name by remember(a) { mutableStateOf(a.name) }
    var bio by remember(a) { mutableStateOf(a.bio) }
    var links by remember(a) { mutableStateOf(a.links) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        EditHeader("Редактирование автора", a.name, { nav.back() })
        GlassPanel(Modifier.fillMaxWidth()) {
            ArTextField(name, { name = it; nameError = null }, label = "Название", maxLength = Limits.authorName, error = nameError)
            Spacer(Modifier.height(10.dp))
            MarkdownEditor(bio, { bio = it }, label = "О себе", maxLength = Limits.authorBio, placeholder = "Расскажите об этом авторе…", slim = true)
            Spacer(Modifier.height(10.dp))
            SocialsEditor(links, { links = it }, label = "Ссылки")
            SaveRow(saving, if (!auth.isMod) "Правки проходят модерацию" else null, onSave = {
                if (name.isBlank()) { nameError = "Укажите название"; return@SaveRow }
                saving = true
                scope.launch {
                    try {
                        val res = Api.patch<AppliedResult>("/authors/${a.id}", buildJsonObject {
                            put("name", name.trim()); put("bio", bio)
                            put("links", buildJsonArray { links.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } })
                        })
                        toast(if (res.applied != false) "Изменения применены" else "Отправлено на модерацию")
                        nav.replace(Routes.author(a.id))
                    } catch (e: Exception) { toastError(e) }
                    saving = false
                }
            })
        }
        Spacer(Modifier.height(16.dp))
        DangerZone("author", a.id, a.name, Routes.catalog(), onChanged = { loader.reload() })
    }
}

// ---------- app/narrator/[slug]/edit ----------

@Composable
fun NarratorEditScreen(slug: String) {
    if (RequireAuth()) return
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val isMod = auth.isMod
    val loader = rememberLoader(slug, keepOnReload = true) { Api.get<NarratorFull>("/narrators/${Routes.enc(slug)}") }
    val n = editGate(loader, { it.can_edit }, "У вас нет прав на редактирование этого чтеца.", "Вернуться к чтецу", Routes.narrator(slug), "Не удалось загрузить чтеца") ?: return
    val isOwner = n.my_role == "owner" || isMod
    var tab by remember { mutableStateOf("info") }
    val scope = rememberCoroutineScope()

    var name by remember(n.id) { mutableStateOf(n.name) }
    var slugField by remember(n.id) { mutableStateOf(n.slug) }
    var bio by remember(n.id) { mutableStateOf(n.bio) }
    var socials by remember(n.id) { mutableStateOf(n.socials) }
    var isSelf by remember(n.id) { mutableStateOf(n.is_self) }
    var isAi by remember(n.id) { mutableStateOf(n.is_ai) }
    var isVerified by remember(n.id) { mutableStateOf(n.is_verified) }
    var adminContact by remember(n.id) { mutableStateOf(n.admin_contact ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val members = rememberLoader(n.id) { Api.get<List<NarratorMember>>("/panel/narrators/${n.id}/members") }

    var transferTo by remember { mutableStateOf<UserSearchHit?>(null) }
    var confirmTransfer by remember { mutableStateOf(false) }
    var transferring by remember { mutableStateOf(false) }
    val transferIsInstant = isMod || user?.skip_moderation == true || auth.can("bypass.moderation") || auth.can("bypass.moderation.narrator")

    fun uploadImage(kind: String, picked: org.foxgirls.audioranobe.ui.components.PickedImage) = scope.launch {
        try {
            Api.upload<Unit>("/panel/narrators/${n.id}/$kind") { addPart(picked.part("file", "$kind.webp")) }
            toast(if (kind == "avatar") "Аватар обновлён" else "Обложка обновлена")
            loader.reload()
        } catch (e: Exception) { toastError(e) }
    }
    val avatarCropper = rememberImageCropper(1, 1, 1024, 1024, circle = true) { uploadImage("avatar", it) }
    val coverCropper = rememberImageCropper(3, 1, 2048, 2048) { uploadImage("cover", it) }

    fun snap() = listOf(name, slugField, bio, socials.map { it.trim() }.filter { it.isNotEmpty() }, isSelf, isAi, isVerified, adminContact).toString()
    var baseline by remember(n.id) { mutableStateOf(snap()) }
    val dirty = snap() != baseline

    fun resetForm() {
        name = n.name; slugField = n.slug; bio = n.bio; socials = n.socials
        isSelf = n.is_self; isAi = n.is_ai; isVerified = n.is_verified; adminContact = n.admin_contact ?: ""
        nameError = null
    }

    fun save() {
        if (name.isBlank()) { nameError = "Укажите название"; return }
        saving = true
        scope.launch {
            try {
                val res = Api.patch<AppliedResult>("/panel/narrators/${n.id}", buildJsonObject {
                    put("name", name.trim()); put("slug", slugField.trim()); put("bio", bio)
                    put("socials", buildJsonArray { socials.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } })
                    put("is_self", isSelf)
                    if (isAi != n.is_ai) put("is_ai", isAi)
                    if (isMod && isVerified != n.is_verified) put("is_verified", isVerified)
                    put("admin_contact", if (isSelf) JsonPrimitive(adminContact) else JsonNull)
                })
                if (res.applied != false) { toast("Изменения применены"); baseline = snap(); loader.reload() }
                else { toast("Отправлено на модерацию"); resetForm() }
            } catch (e: Exception) { toastError(e) }
            saving = false
        }
    }

    val tabs = buildList {
        add(TabItem("info", "Инфо")); add(TabItem("images", "Изображения"))
        if (isMod || n.is_verified) add(TabItem("posts", "Записи"))
        add(TabItem("stats", "Статистика"))
        if (isOwner) add(TabItem("transfer", "Перенос"))
    }

    val bar: (@Composable BoxScope.() -> Unit)? = if (tab == "info" && (dirty || saving)) {
        { EditSaveBar(saving, if (!isMod && n.mod_status == "approved") "Правки проходят модерацию" else null, null, { resetForm() }, { save() }) }
    } else null

    EditScaffold(bar) {
        EditHeader(n.name, null, { nav.back() }, n.avatar_thumb_url ?: n.avatar_url, Lucide.Mic, tags = listOfNotNull(if (n.is_verified) "Подтверждён" else null, if (n.is_ai) "ИИ-голос" else null))
        ArTabs(tabs, tab, { tab = it }, Modifier.padding(bottom = 16.dp), variant = TabsVariant.Underline)
        when (tab) {
            "info" -> {
                EditSection("Основное", "Название, адрес страницы и пометки", first = true) {
                    ArTextField(name, { name = it; nameError = null }, label = "Название", maxLength = Limits.narratorName, error = nameError)
                    Spacer(Modifier.height(10.dp))
                    ArTextField(slugField, { slugField = it }, label = "Адрес страницы", maxLength = 200, placeholder = "narrator-slug")
                    Spacer(Modifier.height(8.dp))
                    ArToggle(isAi, { isAi = it }, "Синтезированный голос", "Рядом с именем появится метка.", enabled = !(n.is_ai && !isMod))
                    ArToggle(isSelf, { isSelf = it }, "Собственный профиль")
                    if (isMod) ArToggle(isVerified, { isVerified = it }, "Подтверждённый профиль", "Рядом с именем появится галочка.")
                }
                EditSection("О чтеце", "Показывается на странице чтеца") {
                    MarkdownEditor(bio, { bio = it }, maxLength = Limits.narratorBio, placeholder = "Расскажите слушателям об этом чтеце…", media = "image", slim = true)
                }
                EditSection("Ссылки", "Соцсети и сайты") {
                    SocialsEditor(socials, { socials = it })
                }
                if (isSelf) EditSection("Контакт для связи", "Виден только администрации") {
                    ArTextField(adminContact, { adminContact = it }, maxLength = 2000, placeholder = "Как связаться с владельцем профиля…", singleLine = false, minLines = 2)
                }
                EditSection("Владелец", "У чтеца всегда ровно один владелец") {
                    when (val ms = members.state) {
                        is Load.Err -> Text(ms.message, color = Ar.danger, fontSize = 13.sp)
                        is Load.Loading -> Text("Загружаем…", color = Ar.textMuted, fontSize = 13.sp)
                        is Load.Ok -> if (ms.data.isEmpty()) Text("Владелец не назначен.", color = Ar.textMuted, fontSize = 13.sp) else ms.data.forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(m.user.username, m.user.avatar_url, 26.dp, thumbUrl = m.user.avatar_thumb_url)
                                Spacer(Modifier.width(10.dp))
                                Text(m.user.username, color = Ar.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (isOwner) Text("Передать права →", color = Ar.accent, fontSize = 13.sp, modifier = Modifier.clickable { tab = "transfer" }.padding(vertical = 6.dp))
                            }
                        }
                    }
                }
                EditSection("Опасная зона", "Удалить чтеца") {
                    DangerZone("narrator", n.id, n.name, Routes.catalog(), bare = true, isDeleted = n.is_deleted, onChanged = { loader.reload(); org.foxgirls.audioranobe.data.Stores.myNarrators.ensureLoaded(force = true) })
                }
            }
            "posts" -> NarratorPosts(n.id, n.can_edit)
            "images" -> GlassPanel(Modifier.fillMaxWidth()) {
                FieldLabel("Аватар")
                Box(Modifier.size(130.dp).clip(CircleShape).background(Ar.surfaceRaised), contentAlignment = Alignment.Center) {
                    if (n.avatar_url != null) ArImage(n.avatar_url, Modifier.fillMaxSize()) else Icon(Lucide.Mic, null, tint = Ar.textMuted, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(10.dp))
                ArButton(if (n.avatar_url != null) "Заменить" else "Загрузить аватар", { avatarCropper.pick() }, small = true)
                Spacer(Modifier.height(20.dp))
                FieldLabel("Обложка")
                Box(Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(10.dp)).background(Ar.surfaceRaised), contentAlignment = Alignment.Center) {
                    if (n.cover_url != null) ArImage(n.cover_url, Modifier.fillMaxSize()) else Icon(Lucide.Mic, null, tint = Ar.textMuted, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(10.dp))
                ArButton(if (n.cover_url != null) "Заменить" else "Загрузить обложку", { coverCropper.pick() }, small = true)
            }
            "stats" -> NarratorStatsDashboard(n.id)
            "transfer" -> {
                EditSection("Передать права", "Новый владелец получит полный доступ к чтецу", first = true) {
                    FieldLabel("Пользователь")
                    UserPicker(transferTo, { transferTo = it }, excludeIds = listOfNotNull(user?.id) + (members.data?.map { it.user.id } ?: emptyList()), enabled = !transferring)
                    Text(
                        if (transferIsInstant) "Передача произойдёт сразу, без модерации." else "Запрос уйдёт на модерацию; до одобрения чтец остаётся за вами.",
                        color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    )
                    ArButton(if (transferring) "Передаём…" else "Передать права", { confirmTransfer = true }, kind = ButtonKind.Primary, enabled = transferTo != null, busy = transferring)
                }
                ConfirmDialog(
                    confirmTransfer, { confirmTransfer = false },
                    onConfirm = {
                        val to = transferTo ?: return@ConfirmDialog
                        transferring = true
                        scope.launch {
                            try {
                                val res = Api.post<AppliedResult>("/panel/narrators/${n.id}/transfer", buildJsonObject { put("user_id", to.id) })
                                if (res.applied != false) {
                                    org.foxgirls.audioranobe.data.Stores.myNarrators.ensureLoaded(force = true)
                                    toast("Права на чтеца переданы @${to.username}"); nav.replace(Routes.narrator(n.slug))
                                }
                                else { toast("Запрос на передачу отправлен на модерацию"); transferTo = null }
                            } catch (e: Exception) { toastError(e) }
                            transferring = false; confirmTransfer = false
                        }
                    },
                    title = "Передать права на чтеца?",
                    body = "«${n.name}» перейдёт к @${transferTo?.username}. " + when {
                        isMod -> "Передача произойдёт сразу; нынешние участники чтеца потеряют к нему доступ."
                        transferIsInstant -> "Это произойдёт сразу, и вы потеряете доступ к редактированию чтеца."
                        else -> "Заявка уйдёт на модерацию; после одобрения вы потеряете доступ к редактированию чтеца."
                    },
                    danger = true, confirmLabel = "Передать",
                )
            }
        }
    }
}
