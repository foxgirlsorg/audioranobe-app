package com.audioranobe.app.ui.screens.catalog

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.Fmt
import com.audioranobe.app.core.Labels
import com.audioranobe.app.core.msg
import com.audioranobe.app.data.Genre
import com.audioranobe.app.data.LocalAuth
import com.audioranobe.app.data.Me
import com.audioranobe.app.data.NarratorCard
import com.audioranobe.app.data.OrderStatus
import com.audioranobe.app.data.Paginated
import com.audioranobe.app.data.RequestableTitle
import com.audioranobe.app.data.TitleCard
import com.audioranobe.app.data.UserSearchHit
import com.audioranobe.app.ui.LocalBottomInset
import com.audioranobe.app.ui.dockScrollAware
import com.audioranobe.app.ui.swipeTabs
import com.audioranobe.app.ui.LocalShell
import com.audioranobe.app.ui.components.ArButton
import com.audioranobe.app.ui.components.ArImage
import com.audioranobe.app.ui.components.ArSheet
import com.audioranobe.app.ui.components.ArTabs
import com.audioranobe.app.ui.components.ArTextField
import com.audioranobe.app.ui.components.ArToggle
import com.audioranobe.app.ui.components.ButtonKind
import com.audioranobe.app.ui.components.CenterSpinner
import com.audioranobe.app.ui.components.CountBubble
import com.audioranobe.app.ui.components.EmptyState
import com.audioranobe.app.ui.components.ErrorState
import com.audioranobe.app.ui.components.Eyebrow
import com.audioranobe.app.ui.components.GlassPanel
import com.audioranobe.app.ui.components.IconBtn
import com.audioranobe.app.ui.components.NarratorPersonCard
import com.audioranobe.app.ui.components.SectionTitle
import com.audioranobe.app.ui.components.SelectMenu
import com.audioranobe.app.ui.components.SelectOption
import com.audioranobe.app.ui.components.Spinner
import com.audioranobe.app.ui.components.TabItem
import com.audioranobe.app.ui.components.TabsVariant
import com.audioranobe.app.ui.components.TitleCardC
import com.audioranobe.app.ui.components.UserAvatar
import com.audioranobe.app.ui.components.gridColumns
import com.audioranobe.app.ui.components.pickers.GenrePicker
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.nav.LocalNav
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.screens.TopBar
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.toast.toast
import com.audioranobe.app.ui.toast.toastError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val SORT_OPTIONS = listOf(
    SelectOption("popular", "По прослушиваниям"), SelectOption("rating", "По рейтингу"), SelectOption("new", "Сначала новые"),
    SelectOption("updated", "Недавно обновлённые"), SelectOption("az", "По алфавиту"), SelectOption("chapters", "По числу глав"),
)

@Serializable
private data class UserHits(val items: List<UserSearchHit> = emptyList())

@Serializable
private data class RequestRes(val slug: String, val already: Boolean = false)

/** lib/requestNarration.ts */
class RequestNarration(private val scope: kotlinx.coroutines.CoroutineScope, private val nav: com.audioranobe.app.ui.nav.AppNav, private val signedIn: () -> Boolean) {
    var pending by mutableStateOf<String?>(null)
    fun request(ref: String, onDone: (() -> Unit)? = null) {
        if (pending != null) return
        if (!signedIn()) { toastError("Войдите, чтобы заказать озвучку"); nav.go(Routes.LOGIN); return }
        pending = ref
        scope.launch {
            try {
                val r = Api.post<RequestRes>("/titles/request-narration", buildJsonObject { put("ref", ref) })
                toast(if (r.already) "Этот тайтл уже озвучивается" else "Озвучка заказана — скоро будет готова")
                onDone?.invoke()
                nav.go(Routes.title(r.slug))
            } catch (e: Exception) { toastError(e) } finally { pending = null }
        }
    }
}

@Composable
fun rememberRequestNarration(): RequestNarration {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    return remember { RequestNarration(scope, nav) { auth.user.value != null } }
}

/** app/catalog: titles / narrators / people search with filters. */
@Composable
fun CatalogScreen(args: Bundle?) {
    val nav = LocalNav.current
    val shell = LocalShell.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val bottom = LocalBottomInset.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(args?.getString("tab")?.takeIf { it == "narrators" || it == "users" } ?: "titles") }
    var q by remember { mutableStateOf(args?.getString("q") ?: "") }
    var genreSlugs by remember { mutableStateOf(args?.getString("genre")?.split(',')?.filter { it.isNotBlank() } ?: emptyList()) }
    var author by remember { mutableStateOf(args?.getString("author") ?: "") }
    var yearFrom by remember { mutableStateOf("") }
    var yearTo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var showAi by remember { mutableStateOf(true) }
    var nsfw by remember { mutableStateOf<Boolean?>(null) }
    var sort by remember { mutableStateOf(args?.getString("sort") ?: "popular") }
    var order by remember { mutableStateOf(if (args?.getString("order") == "asc") "asc" else "desc") }
    var finished by remember { mutableStateOf(args?.getString("finished") == "1") }
    var page by remember { mutableIntStateOf(1) }
    var sheet by remember { mutableStateOf(false) }
    var userHideNsfw by remember { mutableStateOf(true) }

    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var data by remember { mutableStateOf<Paginated<TitleCard>?>(null) }
    var narrators by remember { mutableStateOf<Paginated<NarratorCard>?>(null) }
    var users by remember { mutableStateOf<List<UserSearchHit>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    var debouncedQ by remember { mutableStateOf(q) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) { genres = runCatching { Api.get<Paginated<Genre>>("/genres", mapOf("per_page" to 500)).items }.getOrDefault(emptyList()) }
    LaunchedEffect(user?.id) { if (user != null) userHideNsfw = runCatching { Api.get<Me>("/me").content_prefs?.hide_nsfw ?: true }.getOrDefault(true) else userHideNsfw = true }
    LaunchedEffect(q) { delay(450); if (debouncedQ != q) { debouncedQ = q; page = 1 } }
    val show18 = nsfw ?: !userHideNsfw

    LaunchedEffect(tab, debouncedQ, genreSlugs, author, yearFrom, yearTo, status, country, showAi, nsfw, sort, order, finished, page, nonce) {
        loading = true; error = null
        try {
            when (tab) {
                "narrators" -> narrators = Api.get("/narrators", mapOf("q" to debouncedQ, "page" to page))
                "users" -> users = Api.get<UserHits>("/users/search", mapOf("q" to debouncedQ)).items
                else -> data = Api.get("/titles", mapOf(
                    "q" to debouncedQ, "genre" to genreSlugs.joinToString(","), "author" to author, "year_from" to yearFrom, "year_to" to yearTo,
                    "release_status" to status, "country" to country, "hide_ai" to if (showAi) "" else "1",
                    "nsfw" to nsfw?.let { if (it) "1" else "0" }, "sort" to sort, "order" to order, "page" to page, "finished" to if (finished) "1" else "",
                ))
            }
        } catch (e: Exception) { error = e.msg() } finally { loading = false }
    }

    val activeFilters = listOf(genreSlugs.isNotEmpty(), author.isNotBlank(), yearFrom.isNotBlank(), yearTo.isNotBlank(), status.isNotBlank(), country.isNotBlank(), !showAi, nsfw != null, finished).count { it }
    val cols = gridColumns(LocalConfiguration.current.screenWidthDp)

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols), state = gridState, modifier = Modifier.fillMaxSize().dockScrollAware().swipeTabs(listOfNotNull("titles", "narrators", if (user != null) "users" else null), tab) { tab = it; page = 1 },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TopBar(onSearch = { shell.searchOpen = true }, modifier = Modifier.padding(horizontal = 0.dp))
                Eyebrow("Вся библиотека")
                SectionTitle("Исследуйте", "каталог", Modifier.padding(top = 4.dp, bottom = 12.dp), size = 24)
                ArTextField(q, { q = it }, placeholder = when (tab) { "narrators" -> "Имя чтеца …"; "users" -> "Имя пользователя …"; else -> "Название тайтла …" }, leading = Lucide.Search, imeAction = ImeAction.Search)
                Spacer(Modifier.height(10.dp))
                ArTabs(listOfNotNull(TabItem("titles", "Тайтлы"), TabItem("narrators", "Чтецы"), if (user != null) TabItem("users", "Люди") else null), tab, { tab = it; page = 1 }, variant = TabsVariant.Underline, scrollable = false)
                Spacer(Modifier.height(10.dp))
                if (tab == "titles") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(data?.let { "Тайтлов: ${Fmt.count(it.total)}" } ?: "", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (loading && data != null) Spinner(Modifier.padding(end = 8.dp), 14.dp)
                    Row(Modifier.clip(CircleShape).background(if (activeFilters > 0) Ar.accentSoft else Ar.fill04).clickable { sheet = true }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.SlidersHorizontal, null, tint = if (activeFilters > 0) Ar.accent else Ar.textSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Фильтры", color = if (activeFilters > 0) Ar.accent else Ar.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (activeFilters > 0) { Spacer(Modifier.width(6.dp)); CountBubble(activeFilters) }
                    }
                    Spacer(Modifier.width(6.dp))
                    SelectMenu(sort, SORT_OPTIONS, { sort = it; page = 1 }, Modifier.width(150.dp), small = true)
                    IconBtn(Lucide.ArrowDownWideNarrow, if (order == "asc") "По возрастанию" else "По убыванию", { order = if (order == "asc") "desc" else "asc"; page = 1 }, size = 34.dp, iconSize = 15.dp, tint = if (order == "asc") Ar.accent else Ar.textSecondary)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (tab) { "narrators" -> narrators?.let { "Чтецов: ${Fmt.count(it.total)}" } ?: ""; else -> users?.let { "Найдено: ${Fmt.count(it.size)}" } ?: "" }, color = Ar.textMuted, fontSize = 12.sp)
                    if (loading && (narrators != null || users != null)) Spinner(Modifier.padding(start = 8.dp), 14.dp)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        val firstLoad = loading && data == null && narrators == null && users == null
        if (firstLoad) item(span = { GridItemSpan(maxLineSpan) }) { CenterSpinner() }
        else if (error != null) item(span = { GridItemSpan(maxLineSpan) }) { ErrorState(error!!, { nonce++ }) }
        else when (tab) {
            "titles" -> {
                val d = data
                if (d == null || d.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Тайтлы не найдены", "Попробуйте смягчить или сбросить фильтры.", Lucide.SearchX) }
                else {
                    items(d.items, key = { it.id }) { t -> TitleCardC(t) }
                    item(span = { GridItemSpan(maxLineSpan) }) { Pagination(d.page, d.total, d.per_page) { page = it; scope.launch { gridState.scrollToItem(0) } } }
                    d.external?.takeIf { it.isNotEmpty() }?.let { ext -> item(span = { GridItemSpan(maxLineSpan) }) { RequestableTitles(ext) } }
                }
            }
            "narrators" -> {
                val n = narrators
                if (n == null || n.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Чтецы не найдены", "Попробуйте другой запрос.", Lucide.SearchX) }
                else {
                    items(n.items, key = { it.id }) { NarratorPersonCard(it) }
                    item(span = { GridItemSpan(maxLineSpan) }) { Pagination(n.page, n.total, n.per_page) { page = it; scope.launch { gridState.scrollToItem(0) } } }
                }
            }
            else -> {
                val u = users
                if (u == null || u.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Пользователи не найдены", if (debouncedQ.isNotBlank()) "Попробуйте другой запрос." else "Начните вводить имя пользователя.", Lucide.SearchX) }
                else items(u, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { h ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { nav.go(Routes.user(h.id)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(h.username, h.avatar_url, 40.dp, thumbUrl = h.avatar_thumb_url)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(h.display_name.ifBlank { h.username }, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("@${h.username}", color = Ar.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    ArSheet(sheet, { sheet = false }, "Фильтры") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GenrePicker(genres.filter { it.slug in genreSlugs }.map { it.id }, { ids -> genreSlugs = ids.mapNotNull { id -> genres.firstOrNull { it.id == id }?.slug }; page = 1 }, allowCreate = false, genres = genres, placeholder = "Найти тег…", label = "Теги")
            ArTextField(author, { author = it; page = 1 }, label = "Автор", placeholder = "Например: Duichidak")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArTextField(yearFrom, { yearFrom = it.filter { c -> c.isDigit() }.take(4); page = 1 }, Modifier.weight(1f), label = "Год от", placeholder = "От", keyboardType = KeyboardType.Number)
                ArTextField(yearTo, { yearTo = it.filter { c -> c.isDigit() }.take(4); page = 1 }, Modifier.weight(1f), label = "Год до", placeholder = "До", keyboardType = KeyboardType.Number)
            }
            SelectMenu(status, listOf(SelectOption("", "Любой статус")) + Labels.statusValues.map { SelectOption(it, Labels.releaseStatus[it]!!) }, { status = it; page = 1 }, label = "Статус")
            SelectMenu(country, listOf(SelectOption("", "Любая страна")) + Labels.countryValues.map { SelectOption(it, Labels.country[it]!!) }, { country = it; page = 1 }, label = "Страна")
            ArToggle(showAi, { showAi = it; page = 1 }, "Показывать ИИ-озвучку", "тайтлы, озвученные синтезированным голосом")
            ArToggle(show18, { on -> nsfw = if (on == !userHideNsfw) null else on; page = 1 }, "Показывать 18+", "по умолчанию — как в настройках вашего профиля")
            ArToggle(finished, { finished = it; page = 1 }, "Только завершённые", "тайтл завершён и хотя бы один чтец завершил озвучку")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeFilters > 0 || q.isNotBlank() || sort != "popular" || order != "desc") ArButton("Сбросить", {
                    q = ""; genreSlugs = emptyList(); author = ""; yearFrom = ""; yearTo = ""; status = ""; country = ""; showAi = true; nsfw = null; sort = "popular"; order = "desc"; finished = false; page = 1
                }, kind = ButtonKind.Ghost, icon = Lucide.RotateCcw, modifier = Modifier.weight(1f))
                ArButton(data?.let { "Показать ${Fmt.count(it.total)}" } ?: "Показать", { sheet = false }, kind = ButtonKind.Primary, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** components/Pagination */
@Composable
fun Pagination(page: Int, total: Int, perPage: Int, onPage: (Int) -> Unit) {
    val pages = maxOf(1, (total + maxOf(1, perPage) - 1) / maxOf(1, perPage))
    if (pages <= 1) return
    val cur = page.coerceIn(1, pages)
    val wanted = listOf(1, 2, pages - 1, pages, cur - 1, cur, cur + 1).filter { it in 1..pages }.distinct().sorted()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Lucide.ChevronLeft, "Предыдущая страница", { onPage(cur - 1) }, enabled = cur > 1, size = 34.dp, iconSize = 16.dp)
        var prev = 0
        for (p in wanted) {
            if (prev != 0 && p - prev > 1) Text("…", color = Ar.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (p == cur) Ar.accentSoft else androidx.compose.ui.graphics.Color.Transparent).clickable { onPage(p) }, contentAlignment = Alignment.Center) {
                Text(p.toString(), color = if (p == cur) Ar.accentHover else Ar.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            prev = p
        }
        IconBtn(Lucide.ChevronRight, "Следующая страница", { onPage(cur + 1) }, enabled = cur < pages, size = 34.dp, iconSize = 16.dp)
    }
}

/** components/RequestableTitles: not-yet-in-catalog books that can be AI-narrated on request. */
@Composable
fun RequestableTitles(items: List<RequestableTitle>) {
    val rq = rememberRequestNarration()
    var status by remember { mutableStateOf<OrderStatus?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rq.pending) { status = runCatching { Api.get<OrderStatus>("/titles/request-narration") }.getOrNull() }
    val nextMs = status?.next_at?.let { Fmt.parseIso(it)?.time } ?: 0L
    val blocked = status?.authenticated == true && status?.can_order == false && nextMs > now
    LaunchedEffect(blocked) { while (blocked) { delay(1000); now = System.currentTimeMillis() } }
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Eyebrow("Ещё нет в библиотеке", icon = Lucide.Sparkles)
        Text("Можно заказать озвучку", color = Ar.white, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
        Text("Этих книг пока нет в каталоге. Закажите ИИ-озвучку — и главы начнут появляться.", color = Ar.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
        if (blocked) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Clock, null, tint = Ar.amber, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            val total = maxOf(0L, (nextMs - now) / 1000); val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
            Text("Следующий заказ будет доступен через " + if (h > 0) "$h ч ${m.toString().padStart(2, '0')} мин" else "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}", color = Ar.amber, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        for (it in items) GlassPanel(Modifier.padding(bottom = 8.dp), padding = PaddingValues(10.dp)) {
            Row {
                ArImage(it.cover_url, Modifier.size(64.dp, 92.dp), fallbackIcon = Lucide.BookHeadphones, shape = RoundedCornerShape(8.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(it.year?.toString(), it.status.takeIf { s -> s.isNotBlank() }).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, color = Ar.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    if (!blocked) ArButton(if (rq.pending == it.ref) "Заказываем…" else "Заказать", { rq.request(it.ref) }, kind = ButtonKind.Primary, small = true, icon = Lucide.BookHeadphones, busy = rq.pending == it.ref, enabled = rq.pending == null)
                }
            }
        }
    }
}
