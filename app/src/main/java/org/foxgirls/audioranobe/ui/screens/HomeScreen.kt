package org.foxgirls.audioranobe.ui.screens

import org.foxgirls.audioranobe.ui.components.ditheredBackground
import androidx.compose.foundation.lazy.rememberLazyListState
import org.foxgirls.audioranobe.ui.components.edgeFade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import org.foxgirls.audioranobe.ui.components.SelectMenu
import org.foxgirls.audioranobe.ui.components.SelectOption
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.data.ContinueItem
import org.foxgirls.audioranobe.data.ContinueChapter
import org.foxgirls.audioranobe.data.ContinueTitle
import kotlinx.serialization.builtins.ListSerializer
import org.foxgirls.audioranobe.core.AppJson
import org.foxgirls.audioranobe.offline.OfflineStore
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.Banner
import org.foxgirls.audioranobe.data.HomeData
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.Paginated
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.data.TitleCard
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.dockScrollAware
import org.foxgirls.audioranobe.ui.LocalShell
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CardGrid
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.ProgressTrack
import org.foxgirls.audioranobe.ui.components.Section
import org.foxgirls.audioranobe.ui.components.SectionTitle
import org.foxgirls.audioranobe.ui.components.Spinner
import org.foxgirls.audioranobe.ui.components.TitleRail
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar
import kotlinx.coroutines.delay

private data class HomeSort(val key: String, val param: String, val label: String)

private val SORTS = listOf(
    HomeSort("updated", "updated", "Обновлению"), HomeSort("rating", "rating", "Рейтингу"),
    HomeSort("listens", "popular", "Прослушиваниям"), HomeSort("chapters", "chapters", "Главам"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val shell = LocalShell.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val authLoading by auth.loading.collectAsStateWithLifecycle()
    val loader = rememberLoader(user?.id, keepOnReload = true) { Api.get<HomeData>("/home") }
    var seen by remember { mutableStateOf(Stores.prefs.seenAnnouncements) }
    val bottom = LocalBottomInset.current

    // Catalog block state (components/CatalogGrid)
    var sort by remember { mutableStateOf("updated") }
    var asc by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<Paginated<TitleCard>?>(null) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf("") }
    var catalogNonce by remember { mutableStateOf(0) }
    LaunchedEffect(sort, asc, finished, catalogNonce, loader.state) {
        val home = (loader.state as? Load.Ok)?.data ?: return@LaunchedEffect
        if (sort == "updated" && !asc && !finished && catalogNonce == 0) {
            catalog = Paginated(home.catalog.items, 1, 50, home.catalog.total); return@LaunchedEffect
        }
        catalogLoading = true; catalogError = ""
        try {
            catalog = Api.get<Paginated<TitleCard>>("/titles", mapOf(
                "per_page" to 50, "page" to 1, "sort" to SORTS.first { it.key == sort }.param, "hide_ai" to "1",
                "order" to if (asc) "asc" else null, "finished" to if (finished) "1" else null,
            ))
        } catch (e: Exception) { catalogError = e.message ?: "Ошибка" } finally { catalogLoading = false }
    }

    val online by OfflineStore.online.collectAsStateWithLifecycle()
    val offlineTitles by OfflineStore.titles.collectAsStateWithLifecycle()
    val pending by OfflineStore.pending.collectAsStateWithLifecycle()
    val continueKey = "home_continue_${user?.id ?: 0}"
    val cachedContinue = remember(continueKey, loader.state) {
        Stores.prefs.getString(continueKey)?.let { runCatching { AppJson.decodeFromString(ListSerializer(ContinueItem.serializer()), it) }.getOrNull() } ?: emptyList()
    }
    LaunchedEffect(loader.state) {
        (loader.state as? Load.Ok)?.data?.let { Stores.prefs.putString(continueKey, AppJson.encodeToString(ListSerializer(ContinueItem.serializer()), it.continueItems)) }
    }
    LaunchedEffect(online) { if (online && loader.state is Load.Err) loader.reload() }

    PullToRefreshBox(isRefreshing = loader.state is Load.Loading && loader.data != null, onRefresh = { loader.reload() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().dockScrollAware(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
            item { TopBar(onSearch = { shell.searchOpen = true }) }
            when (val s = loader.state) {
                is Load.Loading -> item { CenterSpinner(minHeight = 300.dp) }
                is Load.Err -> {
                    val local = offlineTitles.sortedByDescending { m -> pending.filter { it.titleId == m.titleId }.maxOfOrNull { it.updatedAt } ?: 0L }.mapNotNull { m ->
                        val t = OfflineStore.offlineTitle(m)
                        val ch = t.volumes.flatMap { it.liveChapters }.filter { m.chapters.containsKey(it.id) && (it.my_position ?: 0.0) > 0 }.maxByOrNull { it.number } ?: return@mapNotNull null
                        ContinueItem(ContinueTitle(t.id, t.slug, t.name, t.cover_url), ContinueChapter(ch.id, ch.name, ch.number, ch.duration_seconds), ch.my_position ?: 0.0)
                    }
                    val cont = local + cachedContinue.filter { c -> local.none { it.title.id == c.title.id } }
                        .map { c -> pending.firstOrNull { it.chapterId == c.chapter.id }?.let { c.copy(position_seconds = it.position) } ?: c }
                    if (cont.isEmpty() && offlineTitles.isEmpty()) item { ErrorState(s.message, { loader.reload() }, "Не удалось загрузить главную") }
                    else {
                        item {
                            GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), padding = PaddingValues(12.dp), borderColor = Ar.amber.copy(alpha = 0.4f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (online) Lucide.TriangleAlert else Lucide.WifiOff, null, tint = Ar.amber, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (online) "Сервер недоступен" else "Нет подключения", color = Ar.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    ArButton("Повторить", { loader.reload() }, kind = ButtonKind.Ghost, small = true)
                                }
                            }
                        }
                        if (cont.isNotEmpty()) item { ContinueSection(cont) }
                        if (offlineTitles.isNotEmpty()) item {
                            Section("Скачано", "на устройство", "Доступно без сети", Modifier.padding(start = 16.dp, top = 22.dp)) {
                                TitleRail(offlineTitles.map { m -> TitleCard(id = m.titleId, slug = m.slug, name = m.name, cover_url = m.title.cover_url, cover_thumb_url = m.title.cover_thumb_url) }, contentPadding = PaddingValues(end = 16.dp))
                            }
                        }
                    }
                }
                is Load.Ok -> {
                    val data = s.data
                    if (!authLoading && user == null) item { Hero() }
                    val ann = data.announcements.filter { it.id !in seen }
                    if (ann.isNotEmpty() && user != null) items(ann, key = { "ann${it.id}" }) { a ->
                        GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), padding = PaddingValues(12.dp), onClick = { nav.go(Routes.newsItem(a.slug)) }) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(30.dp).background(Ar.accentSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(Lucide.Megaphone, null, tint = Ar.accent, modifier = Modifier.size(15.dp)) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(Fmt.date(a.created_at), color = Ar.textMuted, fontSize = 11.sp)
                                    Text(a.title, color = Ar.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (a.body.isNotBlank()) Text(Fmt.plainSummary(a.body, 160), color = Ar.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                IconBtn(Lucide.X, "Скрыть объявление", { seen = seen + a.id; Stores.prefs.seenAnnouncements = seen }, size = 28.dp, iconSize = 14.dp)
                            }
                        }
                    }
                    if (data.continueItems.isNotEmpty()) item { ContinueSection(data.continueItems) }
                    item {
                        Section("Новые", "тайтлы", "Свежее на полке", Modifier.padding(start = 16.dp, top = 22.dp)) {
                            TitleRail(data.new_titles, contentPadding = PaddingValues(end = 16.dp))
                        }
                    }
                    if (data.banners.isNotEmpty()) item { BannerCarousel(data.banners, Modifier.padding(horizontal = 16.dp, vertical = 22.dp)) }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Eyebrow("библиотека", bar = true)
                            SectionTitle("Весь", "каталог", Modifier.padding(top = 6.dp, bottom = 12.dp), size = 22)
                            CatalogControls(sort, { sort = it }, asc, { asc = !asc }, finished, { finished = !finished })
                        }
                    }
                    item {
                        val c = catalog
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            when {
                                catalogError.isNotEmpty() -> ErrorState(catalogError, { catalogNonce++ }, "Не удалось загрузить каталог")
                                c == null -> CenterSpinner()
                                c.items.isEmpty() -> org.foxgirls.audioranobe.ui.components.EmptyState(if (finished) "Завершённых тайтлов пока нет" else "Каталог пока пуст", if (finished) "Снимите фильтр, чтобы увидеть остальные." else "Здесь появятся аудиокниги, как только их добавят.", Lucide.ListMusic)
                                else -> Column {
                                    if (catalogLoading) Row(Modifier.padding(bottom = 8.dp)) { Spinner(size = 14.dp) }
                                    CardGrid(c.items)
                                    if (c.total > c.items.size) {
                                        Text("Показаны ${Fmt.count(c.items.size)} из ${Fmt.count(c.total)} — откройте каталог, чтобы увидеть остальные.", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp).clickable {
                                            nav.go(Routes.catalog(sort = SORTS.first { it.key == sort }.param.takeIf { it != "popular" }, order = if (asc) "asc" else null, finished = if (finished) "1" else null))
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact top bar with the logo and search (replaces the site navbar on phones). */
@Composable
fun TopBar(onSearch: () -> Unit, modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp) {
    val nav = LocalNav.current
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = horizontalPadding, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clickable { nav.tab(Routes.HOME) }) {
            Text("AUDIO", color = Ar.white, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("RANOBE", color = Ar.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.weight(1f))
        IconBtn(Lucide.Search, "Поиск", onSearch, tint = Ar.text)
    }
}

@Composable
private fun Hero() {
    val nav = LocalNav.current
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).ditheredBackground(Brush.linearGradient(listOf(Ar.accent.copy(alpha = 0.16f), Ar.surfaceSolid))).padding(22.dp)) {
        Column {
            Eyebrow("audioranobe.com")
            Spacer(Modifier.height(8.dp))
            Text("Ранобэ в формате", color = Ar.white, fontSize = 26.sp, fontWeight = FontWeight.Light, lineHeight = 30.sp)
            Text("аудиокниг", color = Ar.accent, fontSize = 26.sp, fontWeight = FontWeight.Normal, lineHeight = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text("Подписывайтесь на любимых чтецов и собирайте свою библиотеку.", color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            Row {
                ArButton("Создать аккаунт", { nav.go(Routes.REGISTER) }, icon = Lucide.UserPlus)
                Spacer(Modifier.width(8.dp))
                ArButton("Войти", { nav.go(Routes.LOGIN) }, kind = ButtonKind.Ghost, icon = Lucide.LogIn)
            }
        }
    }
}

/** components/BannerCarousel: auto-advancing 3:1 banners. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerCarousel(banners: List<Banner>, modifier: Modifier = Modifier) {
    if (banners.isEmpty()) return
    val nav = LocalNav.current
    val pager = rememberPagerState(pageCount = { banners.size })
    LaunchedEffect(pager, banners.size) {
        if (banners.size < 2) return@LaunchedEffect
        while (true) {
            delay(10_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % banners.size)
        }
    }
    Column(modifier) {
        HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(16.dp))) { i ->
            val b = banners[i]
            ArImage(b.image_url, Modifier.fillMaxSize().clickable(enabled = b.url.isNotBlank()) { Links.open(nav, b.url) })
        }
        if (banners.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            for (i in banners.indices) Box(Modifier.padding(3.dp).size(6.dp).background(if (i == pager.currentPage) Ar.accent else Ar.borderStrong, CircleShape))
        }
    }
}

/** components/CatalogGrid .controls at phone width: sort dropdown, direction toggle, finished-only toggle. */
@Composable
private fun CatalogControls(sort: String, onSort: (String) -> Unit, asc: Boolean, onFlip: () -> Unit, finished: Boolean, onFinished: () -> Unit) {
    val arrow by animateFloatAsState(if (asc) 0f else 180f, tween(200), label = "sortArrow")
    val green = Color(0xFF6FAE86)
    Text("СОРТИРОВАТЬ ПО", color = Ar.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
    Spacer(Modifier.height(5.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectMenu(sort, SORTS.map { SelectOption(it.key, it.label) }, onSort, Modifier.weight(1f))
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(9.dp)).background(Ar.fill04).border(1.dp, Ar.border, RoundedCornerShape(9.dp)).clickable(onClick = onFlip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.ArrowUpNarrowWide, if (asc) "Сортировать по убыванию" else "Сортировать по возрастанию", tint = Ar.textSecondary, modifier = Modifier.size(15.dp).rotate(arrow))
        }
        Row(
            Modifier.fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(if (finished) green.copy(alpha = 0.12f) else Ar.fill04)
                .border(1.dp, if (finished) green.copy(alpha = 0.5f) else Ar.border, RoundedCornerShape(9.dp)).clickable(onClick = onFinished).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.CheckCheck, null, tint = if (finished) green else Ar.textSecondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("ЗАВЕРШЁННЫЕ", color = if (finished) Ar.white else Ar.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
        }
    }
}

@Composable
private fun ContinueSection(list: List<ContinueItem>) {
    val nav = LocalNav.current
    Section("Продолжить", "слушать", "Вернитесь к тому, на чём остановились", Modifier.padding(start = 16.dp, top = 18.dp)) {
        val continueState = rememberLazyListState()
        LazyRow(Modifier.fillMaxWidth().edgeFade(continueState), state = continueState, contentPadding = PaddingValues(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.chapter.id }) { c ->
                val pct = if (c.chapter.duration_seconds > 0) (c.position_seconds / c.chapter.duration_seconds).toFloat().coerceAtMost(1f) else 0f
                GlassPanel(Modifier.width(300.dp), padding = PaddingValues(10.dp)) {
                    Row {
                        ArImage(c.title.cover_url, Modifier.size(64.dp, 88.dp).clickable { nav.go(Routes.title(c.title.slug)) }, fallbackIcon = Lucide.Play, shape = RoundedCornerShape(8.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { nav.go(Routes.title(c.title.slug)) })
                            Text("Гл. ${Fmt.trimNum(c.chapter.number)}" + (if (c.chapter.name.isNotBlank()) " — ${c.chapter.name}" else ""), color = Ar.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            ProgressTrack(pct)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${Fmt.duration(c.position_seconds)} / ${Fmt.duration(c.chapter.duration_seconds)}", color = Ar.textMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Row(Modifier.clip(CircleShape).background(Ar.accent).clickable { PlayerController.play(c.chapter.id) }.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Lucide.Play, null, tint = Ar.accentOn, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Продолжить", color = Ar.accentOn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
