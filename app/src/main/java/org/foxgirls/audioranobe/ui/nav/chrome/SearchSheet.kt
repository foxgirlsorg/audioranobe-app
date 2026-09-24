package org.foxgirls.audioranobe.ui.nav.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.SearchSuggest
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.NarratorAvatar
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.screens.catalog.rememberRequestNarration
import org.foxgirls.audioranobe.ui.theme.Ar
import kotlinx.coroutines.delay

/** NavBar search with live suggestions (GET /search/suggest). */
@Composable
fun SearchSheet(open: Boolean, onClose: () -> Unit) {
    val nav = LocalNav.current
    var q by remember { mutableStateOf("") }
    var sug by remember { mutableStateOf<SearchSuggest?>(null) }
    val focus = remember { FocusRequester() }
    val request = rememberRequestNarration()

    LaunchedEffect(open) { if (open) { q = ""; sug = null; delay(150); runCatching { focus.requestFocus() } } }
    LaunchedEffect(q) {
        val query = q.trim()
        if (query.length < 2) { sug = null; return@LaunchedEffect }
        delay(300)
        sug = runCatching { Api.get<SearchSuggest>("/search/suggest", mapOf("q" to query)) }.getOrNull()
    }

    fun go(route: String) { onClose(); nav.go(route) }
    fun submit() { val t = q.trim(); if (t.isNotEmpty()) go(Routes.catalog(q = t)) }

    ArSheet(open, onClose) {
        ArTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus), placeholder = "Поиск тайтлов и чтецов…", leading = Lucide.Search, imeAction = ImeAction.Search, onImeAction = { submit() })
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            val s = sug
            if (q.trim().length < 2) {
                Text("Введите не менее 2 символов", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            } else if (s == null) {
                Text("Ищем…", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            } else {
                val empty = s.titles.isEmpty() && s.narrators.isEmpty() && s.authors.isEmpty() && s.collections.isEmpty() && s.external.isEmpty()
                if (empty) Text("Ничего не найдено", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                if (s.titles.isNotEmpty()) {
                    Group("Тайтлы")
                    for (t in s.titles) SuggestRow(t.name, t.author?.name ?: "", { go(Routes.title(t.slug)) }) { ArImage(t.cover_thumb_url ?: t.cover_url, Modifier.size(34.dp, 46.dp), shape = RoundedCornerShape(6.dp)) }
                }
                if (s.narrators.isNotEmpty()) {
                    Group("Чтецы")
                    for (n in s.narrators) SuggestRow(n.name, "Чтец", { go(Routes.narrator(n.slug)) }) { NarratorAvatar(n.name, n.avatar_url, 34.dp) }
                }
                if (s.authors.isNotEmpty()) {
                    Group("Авторы")
                    for (a in s.authors) SuggestRow(a.name, Fmt.titlesPlural(a.titles_count), { go(Routes.author(a.id)) }) { Icon(Lucide.PenLine, null, tint = Ar.textMuted, modifier = Modifier.size(18.dp)) }
                }
                if (s.collections.isNotEmpty()) {
                    Group("Коллекции")
                    for (c in s.collections) SuggestRow(c.name, Fmt.titlesPlural(c.items_count), { go(Routes.collection(c.id)) }) { Icon(Lucide.Library, null, tint = Ar.textMuted, modifier = Modifier.size(18.dp)) }
                }
                if (s.external.isNotEmpty()) {
                    Group("Заказать озвучку")
                    for (e in s.external) SuggestRow(e.name, if (request.pending == e.ref) "Заказываем…" else "заказать ИИ-озвучку", { request.request(e.ref) { onClose() } }) { ArImage(e.cover_url, Modifier.size(34.dp, 46.dp), shape = RoundedCornerShape(6.dp)) }
                }
                Text("Все результаты по запросу «${q.trim()}»", color = Ar.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { submit() }.padding(12.dp))
            }
        }
    }
}

@Composable
private fun Group(label: String) {
    Eyebrow(label, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp))
}

@Composable
private fun SuggestRow(name: String, sub: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip8().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ar.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, color = Ar.textMuted, fontSize = 12.sp, maxLines = 1)
        }
    }
    Spacer(Modifier.height(0.dp))
}

private fun Modifier.clip8() = this.then(Modifier.clip(RoundedCornerShape(8.dp)))
private fun Modifier.clip(shape: androidx.compose.ui.graphics.Shape) = androidx.compose.ui.draw.clip(this, shape)
