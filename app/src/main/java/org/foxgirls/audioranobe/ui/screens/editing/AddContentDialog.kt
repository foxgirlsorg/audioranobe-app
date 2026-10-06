package org.foxgirls.audioranobe.ui.screens.editing

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.core.msg
import org.foxgirls.audioranobe.data.Author
import org.foxgirls.audioranobe.data.Labels
import org.foxgirls.audioranobe.data.NarratorFull
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.data.TitleFull
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArModal
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.FieldLabel
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.MarkdownEditor
import org.foxgirls.audioranobe.ui.components.Pill
import org.foxgirls.audioranobe.ui.components.SelectMenu
import org.foxgirls.audioranobe.ui.components.SelectOption
import org.foxgirls.audioranobe.ui.components.pickers.AuthorPicker
import org.foxgirls.audioranobe.ui.components.pickers.GenrePicker
import org.foxgirls.audioranobe.ui.components.pickers.SocialsEditor
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** components/AddContentDialog: "what to add?" menu → book / narrator / author forms. */
@Composable
fun AddContentDialog(open: Boolean, onClose: () -> Unit) {
    if (!open) return
    var kind by remember { mutableStateOf("menu") }
    val title = when (kind) { "title" -> "Новая книга"; "narrator" -> "Новый чтец"; "author" -> "Новый автор"; else -> "Что добавить?" }
    ArModal(true, onClose, title) {
        when (kind) {
            "menu" -> {
                Choice(Lucide.BookPlus, "Книга", "Новый тайтл, привязанный к одному из ваших чтецов") { kind = "title" }
                Choice(Lucide.Mic2, "Чтец", "Профиль озвучки с описанием и контактами") { kind = "narrator" }
                Choice(Lucide.PenLine, "Автор", "Автор оригинального произведения") { kind = "author" }
            }
            "title" -> TitleForm(onClose) { kind = "menu" }
            "narrator" -> NarratorForm(onClose) { kind = "menu" }
            else -> AuthorForm(onClose) { kind = "menu" }
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, name: String, hint: String, onClick: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Ar.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, color = Ar.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(hint, color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun FootRow(onBack: () -> Unit, busy: Boolean, submitLabel: String, onSubmit: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
        ArButton("Назад", onBack, kind = ButtonKind.Ghost, enabled = !busy)
        Spacer(Modifier.width(8.dp))
        ArButton(if (busy) "Создаём…" else submitLabel, onSubmit, kind = ButtonKind.Primary, busy = busy)
    }
}

@Composable
private fun ErrorLine(error: String) { if (error.isNotEmpty()) Text(error, color = Ar.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp)) }

@Serializable
private data class CreatedTitle(val title: TitleFull, val applied: Boolean = true)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleForm(onDone: () -> Unit, onBack: () -> Unit) {
    val nav = LocalNav.current
    val narrators by Stores.myNarrators.narrators.collectAsStateWithLifecycle()
    val loaded by Stores.myNarrators.loaded.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Stores.myNarrators.ensureLoaded() }
    var narratorIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(narrators) { if (narrators.isNotEmpty() && narratorIds.isEmpty()) narratorIds = listOf(narrators.first().id) }
    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var author by remember { mutableStateOf<Author?>(null) }
    var description by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("ongoing") }
    var country by remember { mutableStateOf<String?>(null) }
    var genreIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (loaded && narrators.isEmpty()) {
        Text("Сначала создайте чтеца — книга всегда привязана к профилю озвучки.", color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) { ArButton("Назад", onBack, kind = ButtonKind.Ghost) }
        return
    }
    ErrorLine(error)
    FieldLabel("Чтецы")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        narrators.forEach { n -> Pill(n.name, active = n.id in narratorIds, onClick = { narratorIds = if (n.id in narratorIds) narratorIds - n.id else narratorIds + n.id }) }
    }
    Spacer(Modifier.height(10.dp))
    ArTextField(name, { name = it }, label = "Название", maxLength = 300)
    Spacer(Modifier.height(10.dp))
    ArTextField(slug, { slug = it }, label = "Slug (необязательно)", maxLength = 200, placeholder = "my-book-slug")
    Spacer(Modifier.height(10.dp))
    AuthorPicker(author, { author = it }, label = "Автор")
    Spacer(Modifier.height(10.dp))
    Row {
        ArTextField(year, { year = it }, Modifier.weight(1f), label = "Год", keyboardType = KeyboardType.Number)
        Spacer(Modifier.width(10.dp))
        SelectMenu(status, Labels.statusValues.map { SelectOption(it, Labels.releaseStatus[it] ?: it) }, { status = it }, Modifier.weight(1f), label = "Статус тайтла")
    }
    Spacer(Modifier.height(10.dp))
    SelectMenu(country, Labels.countryValues.map { SelectOption<String?>(it, Labels.country[it] ?: it) }, { country = it }, Modifier.fillMaxWidth(), label = "Страна", placeholder = "Выберите страну")
    Spacer(Modifier.height(10.dp))
    GenrePicker(genreIds, { genreIds = it }, label = "Теги")
    Spacer(Modifier.height(10.dp))
    MarkdownEditor(description, { description = it }, label = "Описание", maxLength = Limits.titleDescription, placeholder = "О чём эта книга? **Markdown** поддерживается.", slim = true)
    FootRow(onBack, busy, "Создать книгу") {
        if (name.isBlank()) { error = "Укажите название"; return@FootRow }
        if (narratorIds.isEmpty()) { error = "Выберите хотя бы одного чтеца"; return@FootRow }
        if (country == null) { error = "Выберите страну"; return@FootRow }
        error = ""; busy = true
        scope.launch {
            try {
                val res = Api.post<CreatedTitle>("/panel/titles", buildJsonObject {
                    put("narrator_ids", buildJsonArray { narratorIds.forEach { add(JsonPrimitive(it)) } })
                    put("name", name.trim())
                    if (slug.isNotBlank()) put("slug", slug.trim())
                    put("author_id", author?.id?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("description", description)
                    put("year", year.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("release_status", status)
                    put("country", country)
                    put("genre_ids", buildJsonArray { genreIds.forEach { add(JsonPrimitive(it)) } })
                })
                toast(if (res.applied) "Книга создана" else "Книга отправлена на модерацию")
                onDone(); nav.go(Routes.titleEdit(res.title.slug, "content"))
            } catch (e: Exception) { error = e.msg() }
            busy = false
        }
    }
}

@Composable
private fun NarratorForm(onDone: () -> Unit, onBack: () -> Unit) {
    val nav = LocalNav.current
    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var socials by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSelf by remember { mutableStateOf(false) }
    var adminContact by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    ErrorLine(error)
    ArTextField(name, { name = it }, label = "Название", maxLength = 100)
    Spacer(Modifier.height(10.dp))
    ArTextField(slug, { slug = it }, label = "Slug (необязательно)", maxLength = 200, placeholder = "my-narrator-slug")
    Spacer(Modifier.height(10.dp))
    MarkdownEditor(bio, { bio = it }, label = "Описание (необязательно)", maxLength = 10000, placeholder = "Расскажите слушателям об этом чтеце…", slim = true)
    Spacer(Modifier.height(10.dp))
    SocialsEditor(socials, { socials = it }, label = "Соцсети (необязательно)")
    Spacer(Modifier.height(10.dp))
    ArToggle(isSelf, { isSelf = it }, "Это мой собственный профиль")
    if (isSelf) {
        Spacer(Modifier.height(10.dp))
        ArTextField(adminContact, { adminContact = it }, label = "Контакт для администрации", maxLength = 2000, placeholder = "Telegram, почта — как с вами связаться…", hint = "Виден только модераторам и администраторам, не публикуется.", singleLine = false, minLines = 2)
    }
    FootRow(onBack, busy, "Создать чтеца") {
        if (name.isBlank()) { error = "Укажите название"; return@FootRow }
        if (isSelf && adminContact.isBlank()) { error = "Укажите контакт для администрации — он нужен для своего профиля"; return@FootRow }
        error = ""; busy = true
        scope.launch {
            try {
                val created = Api.post<NarratorFull>("/narrators", buildJsonObject {
                    put("name", name.trim()); if (slug.isNotBlank()) put("slug", slug.trim()); put("bio", bio.trim())
                    put("socials", buildJsonArray { socials.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } })
                    put("is_self", isSelf); put("admin_contact", if (isSelf) adminContact.trim() else "")
                })
                toast(if (created.mod_status == "approved") "Чтец создан" else "Чтец отправлен на модерацию")
                Stores.myNarrators.ensureLoaded(force = true)
                onDone(); nav.go(Routes.narratorEdit(created.slug))
            } catch (e: Exception) { error = e.msg() }
            busy = false
        }
    }
}

@Composable
private fun AuthorForm(onDone: () -> Unit, onBack: () -> Unit) {
    val nav = LocalNav.current
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var links by remember { mutableStateOf(listOf("")) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    ErrorLine(error)
    ArTextField(name, { name = it }, label = "Имя автора", maxLength = 120)
    Spacer(Modifier.height(10.dp))
    MarkdownEditor(bio, { bio = it }, label = "Описание (необязательно)", maxLength = 10000, placeholder = "Расскажите об этом авторе…", slim = true)
    Spacer(Modifier.height(10.dp))
    SocialsEditor(links, { links = it }, label = "Ссылки")
    FootRow(onBack, busy, "Создать автора") {
        if (name.isBlank()) { error = "Укажите имя автора"; return@FootRow }
        error = ""; busy = true
        scope.launch {
            try {
                val created = Api.post<Author>("/authors", buildJsonObject { put("name", name.trim()) })
                val clean = links.map { it.trim() }.filter { it.isNotEmpty() }
                if (bio.isNotBlank() || clean.isNotEmpty()) Api.patch<Unit>("/authors/${created.id}", buildJsonObject {
                    put("bio", bio.trim()); put("links", buildJsonArray { clean.forEach { add(JsonPrimitive(it)) } })
                })
                toast("Автор создан")
                onDone(); nav.go(Routes.author(created.id))
            } catch (e: Exception) { error = e.msg() }
            busy = false
        }
    }
}
