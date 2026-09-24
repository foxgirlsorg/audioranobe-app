package org.foxgirls.audioranobe.ui.components.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.data.Author
import org.foxgirls.audioranobe.data.Genre
import org.foxgirls.audioranobe.data.NarratorCard
import org.foxgirls.audioranobe.data.Paginated
import org.foxgirls.audioranobe.data.UserSearchHit
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.FieldLabel
import org.foxgirls.audioranobe.ui.components.NarratorAvatar
import org.foxgirls.audioranobe.ui.components.UserAvatar
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toastError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class Created(val id: Int, val slug: String = "", val name: String = "")

@Serializable
private data class UserHits(val items: List<UserSearchHit> = emptyList())

/** A selected chip with a remove button. */
@Composable
fun Chip(text: String, onRemove: (() -> Unit)?, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(modifier.clip(CircleShape).background(Ar.accentSoft).border(1.dp, Ar.accent.copy(alpha = 0.35f), CircleShape).padding(start = if (leading != null) 4.dp else 10.dp, end = if (onRemove != null) 4.dp else 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(text, color = Ar.accentHover, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.X, "Убрать", tint = Ar.accentHover, modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onRemove).padding(4.dp))
        }
    }
}

/** Shared bar: chips + "add" affordance that opens a search sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerBar(placeholder: String, chips: @Composable () -> Unit, hasChips: Boolean, onOpen: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Ar.fill04).border(1.dp, Ar.border, RoundedCornerShape(9.dp)).clickable(onClick = onOpen).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips()
        Row(Modifier.height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Plus, null, tint = Ar.textMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (hasChips) "Добавить" else placeholder, color = Ar.textMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun OptionRow(text: String, sub: String? = null, leading: (@Composable () -> Unit)? = null, accent: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Text(text, color = if (accent) Ar.accent else Ar.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (sub != null) Text(sub, color = Ar.textMuted, fontSize = 12.sp)
    }
}

/** components/GenrePicker */
@Composable
fun GenrePicker(value: List<Int>, onChange: (List<Int>) -> Unit, modifier: Modifier = Modifier, allowCreate: Boolean = true, genres: List<Genre>? = null, placeholder: String = "Найти или создать тег…", label: String? = null) {
    var own by remember { mutableStateOf<List<Genre>>(emptyList()) }
    val all = genres ?: own
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(genres == null) { if (genres == null) own = runCatching { Api.get<Paginated<Genre>>("/genres", mapOf("per_page" to 2000)).items }.getOrDefault(emptyList()) }
    val byId = remember(all) { all.associateBy { it.id } }
    val selected = value.mapNotNull { byId[it] }
    val ql = q.trim().lowercase()
    val matches = all.filter { it.id !in value }.filter { ql.isEmpty() || it.name.lowercase().contains(ql) }.take(40)
    val canCreate = allowCreate && ql.isNotEmpty() && all.none { it.name.trim().lowercase() == ql }

    Column(modifier) {
        if (label != null) FieldLabel(label)
        PickerBar(placeholder, { for (g in selected) Chip(g.name, { onChange(value - g.id) }) }, selected.isNotEmpty()) { open = true; q = "" }
    }
    ArSheet(open, { open = false }, "Теги") {
        ArTextField(q, { q = it }, placeholder = placeholder, maxLength = Limits.genreName, leading = Lucide.Search)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
            for (g in matches) OptionRow(g.name, g.titles_count.toString()) { onChange(value + g.id); q = "" }
            if (canCreate) OptionRow("Создать тег «${q.trim()}»", accent = true, leading = { Icon(Lucide.Plus, null, tint = Ar.accent, modifier = Modifier.size(14.dp)) }) {
                scope.launch {
                    try {
                        val c = Api.post<Created>("/genres", buildJsonObject { put("name", q.trim()) })
                        val g = Genre(c.id, c.slug, c.name)
                        if (genres == null) own = own + g
                        onChange(value + g.id); q = ""
                    } catch (e: Exception) { toastError(e) }
                }
            }
        }
    }
}

/** components/AuthorPicker */
@Composable
fun AuthorPicker(value: Author?, onChange: (Author?) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Найти или создать автора…", label: String? = null) {
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Author>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(q, open) {
        if (!open) return@LaunchedEffect
        delay(250)
        results = runCatching { Api.get<Paginated<Author>>("/authors", mapOf("q" to q.trim(), "per_page" to 20)).items }.getOrDefault(emptyList())
    }
    val ql = q.trim().lowercase()
    val canCreate = ql.isNotEmpty() && results.none { it.name.trim().lowercase() == ql }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        PickerBar(placeholder, { if (value != null) Chip(value.name, { onChange(null) }) }, value != null) { open = true; q = "" }
    }
    ArSheet(open, { open = false }, "Автор") {
        ArTextField(q, { q = it }, placeholder = placeholder, leading = Lucide.Search)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
            for (a in results.filter { it.id != value?.id }) OptionRow(a.name, a.titles_count.toString()) { onChange(a); open = false }
            if (canCreate) OptionRow("Создать автора «${q.trim()}»", accent = true, leading = { Icon(Lucide.Plus, null, tint = Ar.accent, modifier = Modifier.size(14.dp)) }) {
                scope.launch {
                    try { val c = Api.post<Created>("/authors", buildJsonObject { put("name", q.trim()) }); onChange(Author(c.id, c.name, c.slug, 0)); open = false } catch (e: Exception) { toastError(e) }
                }
            }
        }
    }
}

data class NarratorRef(val id: Int, val slug: String, val name: String, val avatar_url: String? = null)

/** components/NarratorPicker */
@Composable
fun NarratorPicker(value: List<NarratorRef>, onChange: (List<NarratorRef>) -> Unit, modifier: Modifier = Modifier, lockedIds: List<Int> = emptyList(), placeholder: String = "Найти чтеца по названию…", label: String? = null) {
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<NarratorCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(q, open) {
        if (!open) return@LaunchedEffect
        loading = true; delay(250)
        results = runCatching { Api.get<Paginated<NarratorCard>>("/narrators", mapOf("q" to q.trim(), "per_page" to 20)).items }.getOrDefault(emptyList())
        loading = false
    }
    val ids = value.map { it.id }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        PickerBar(placeholder, { for (n in value) Chip(n.name, if (n.id in lockedIds) null else ({ onChange(value.filter { it.id != n.id }) })) }, value.isNotEmpty()) { open = true; q = "" }
    }
    ArSheet(open, { open = false }, "Чтецы") {
        ArTextField(q, { q = it }, placeholder = placeholder, leading = Lucide.Search)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
            val m = results.filter { it.id !in ids }
            if (m.isEmpty()) Text(if (loading) "Ищем…" else "Ничего не найдено", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
            for (n in m) OptionRow(n.name, n.titles_count.toString(), leading = { NarratorAvatar(n.name, n.avatar_url, 26.dp) }) { onChange(value + NarratorRef(n.id, n.slug, n.name, n.avatar_url)); q = "" }
        }
    }
}

/** components/UserPicker: single account picker backed by GET /users/search. */
@Composable
fun UserPicker(value: UserSearchHit?, onChange: (UserSearchHit?) -> Unit, modifier: Modifier = Modifier, excludeIds: List<Int> = emptyList(), placeholder: String = "Имя пользователя или ник…", enabled: Boolean = true, label: String? = null) {
    var open by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserSearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val qq = q.trim().removePrefix("@")
    LaunchedEffect(qq, open) {
        if (!open || qq.length < 2) { results = emptyList(); return@LaunchedEffect }
        loading = true; delay(250)
        results = runCatching { Api.get<UserHits>("/users/search", mapOf("q" to qq)).items }.getOrDefault(emptyList())
        loading = false
    }
    Column(modifier) {
        if (label != null) FieldLabel(label)
        PickerBar(placeholder, {
            if (value != null) Chip(value.display_name.ifBlank { value.username } + "  @${value.username}", if (enabled) ({ onChange(null) }) else null, leading = { UserAvatar(value.username, value.avatar_url, 20.dp, thumbUrl = value.avatar_thumb_url) })
        }, value != null) { if (enabled) { open = true; q = "" } }
    }
    ArSheet(open, { open = false }, "Пользователь") {
        ArTextField(q, { q = it }, placeholder = placeholder, leading = Lucide.Search)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
            val m = results.filter { it.id !in excludeIds }
            if (qq.length < 2) Text("Введите не менее 2 символов", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
            else if (m.isEmpty()) Text(if (loading) "Ищем…" else if (results.isNotEmpty()) "Все совпадения — уже участники или вы" else "Никого не нашли", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
            for (u in m) OptionRow(u.display_name.ifBlank { u.username }, "@${u.username}", leading = { UserAvatar(u.username, u.avatar_url, 26.dp, thumbUrl = u.avatar_thumb_url) }) { onChange(u); open = false }
        }
    }
}

/** components/SocialsEditor: list of http(s) URLs. */
@Composable
fun SocialsEditor(value: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier, label: String? = null, maxLinks: Int = Limits.socialsCount) {
    Column(modifier) {
        if (label != null) FieldLabel(label)
        if (value.isEmpty()) Text("Пока нет ссылок.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        value.forEachIndexed { i, u ->
            val invalid = u.trim().isNotEmpty() && !Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(u.trim())
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(org.foxgirls.audioranobe.ui.components.social.Brands.iconFor(u), null, tint = Ar.textMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                ArTextField(u, { v -> onChange(value.mapIndexed { j, x -> if (j == i) v else x }) }, Modifier.weight(1f), placeholder = "https://…", maxLength = Limits.socialUrl, error = if (invalid) "Должен быть http(s) URL" else null)
                Spacer(Modifier.width(4.dp))
                org.foxgirls.audioranobe.ui.components.IconBtn(Lucide.Trash2, "Удалить ссылку", { onChange(value.filterIndexed { j, _ -> j != i }) }, tint = Ar.danger.copy(alpha = 0.8f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            org.foxgirls.audioranobe.ui.components.ArButton("Добавить ссылку", { if (value.size < maxLinks) onChange(value + "") }, icon = Lucide.Plus, small = true, kind = org.foxgirls.audioranobe.ui.components.ButtonKind.Ghost, enabled = value.size < maxLinks)
            Spacer(Modifier.weight(1f))
            Text("${value.size}/$maxLinks", color = Ar.textMuted, fontSize = 11.sp)
        }
    }
}
