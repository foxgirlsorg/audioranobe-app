package org.foxgirls.audioranobe.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.foxgirls.audioranobe.core.ApiError
import org.foxgirls.audioranobe.core.msg
import org.foxgirls.audioranobe.data.Paginated
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Loading / data / error triple used by nearly every screen. */
sealed class Load<out T> {
    data object Loading : Load<Nothing>()
    data class Ok<T>(val data: T) : Load<T>()
    data class Err(val message: String, val notFound: Boolean = false) : Load<Nothing>()
}

class Loader<T>(initial: Load<T> = Load.Loading) {
    var state: Load<T> by mutableStateOf(initial)
    var nonce by mutableIntStateOf(0)
    var fetchedNonce = -1
    val data: T? get() = (state as? Load.Ok<T>)?.data

    fun reload() { nonce++ }
    fun set(v: T) { state = Load.Ok(v) }
    fun update(f: (T) -> T) { data?.let { state = Load.Ok(f(it)) } }
}

/** Per-back-stack-entry store: loaders/lists outlive the composition so popping back restores the screen as it was. */
class ScreenCache : ViewModel() {
    val map = HashMap<Any, Any>()
}

@Composable
fun <V : Any> rememberCached(vararg key: Any?, create: () -> V): V {
    val cache = viewModel<ScreenCache>()
    val id = listOf(currentCompositeKeyHash, *key)
    @Suppress("UNCHECKED_CAST")
    return cache.map.getOrPut(id) { create() } as V
}

/** Runs [fetch] when [key] changes (and on reload), storing the result. */
@Composable
fun <T> rememberLoader(vararg key: Any?, keepOnReload: Boolean = false, fetch: suspend () -> T): Loader<T> {
    val loader = rememberCached(*key) { Loader<T>() }
    LaunchedEffect(loader, loader.nonce) {
        val revisit = loader.data != null && loader.fetchedNonce == loader.nonce
        if (!revisit && (!keepOnReload || loader.data == null)) loader.state = Load.Loading
        try {
            loader.state = Load.Ok(fetch())
            loader.fetchedNonce = loader.nonce
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loader.state = Load.Err(e.msg(), (e as? ApiError)?.status == 404)
        }
    }
    return loader
}

/** Renders the usual three states. */
@Composable
fun <T> LoadBox(
    loader: Loader<T>,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { CenterSpinner() },
    notFound: (@Composable () -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (val s = loader.state) {
        is Load.Loading -> Box(modifier) { loading() }
        is Load.Err -> Box(modifier) {
            if (s.notFound && notFound != null) notFound() else ErrorState(s.message, onRetry = { loader.reload() })
        }
        is Load.Ok -> content(s.data)
    }
}

/** A paginated endpoint read as one growing list (lib/useInfiniteList.ts). */
class PagedList<T>(private val fetch: suspend (page: Int) -> Paginated<T>) {
    var items: List<T>? by mutableStateOf(null)
    var total by mutableIntStateOf(0)
    var page by mutableIntStateOf(1)
    var loading by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf("")
    var moreError by mutableStateOf("")
    private var run = 0
    val hasMore get() = items != null && (items!!.size < total)

    suspend fun load() {
        val id = ++run
        loading = true; error = ""; moreError = ""
        try {
            val res = fetch(1)
            if (id != run) return
            items = res.items; total = res.total; page = res.page
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (id != run) return
            items = null; error = e.msg()
        } finally {
            if (id == run) loading = false
        }
    }

    suspend fun loadMore() {
        if (loadingMore || !hasMore) return
        val id = run
        loadingMore = true; moreError = ""
        try {
            val res = fetch(page + 1)
            if (id != run) return
            val seen = (items ?: emptyList()).toMutableList()
            seen.addAll(res.items)
            items = seen; total = res.total; page = res.page
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (id == run) moreError = e.msg()
        } finally {
            if (id == run) loadingMore = false
        }
    }

    fun patch(match: (T) -> Boolean, next: (T) -> T) {
        items = items?.map { if (match(it)) next(it) else it }
    }

    fun remove(match: (T) -> Boolean) {
        val prev = items ?: return
        val n = prev.filter { !match(it) }
        total = maxOf(0, total - (prev.size - n.size))
        items = n
    }

    fun prepend(item: T) {
        items = listOf(item) + (items ?: emptyList()); total++
    }
}

@Composable
fun <T> rememberPagedList(vararg key: Any?, fetch: suspend (page: Int) -> Paginated<T>): PagedList<T> {
    val list = rememberCached(*key) { PagedList(fetch) }
    LaunchedEffect(list) { if (list.items == null) list.load() }
    return list
}

/** "Показать ещё" footer for a PagedList. */
@Composable
fun LoadMoreRow(list: PagedList<*>, label: String = "Показать ещё") {
    val scope = rememberCoroutineScope()
    if (!list.hasMore) return
    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        if (list.moreError.isNotEmpty()) {
            ArButton(list.moreError.take(40), { scope.launch { list.loadMore() } }, kind = ButtonKind.Ghost)
        } else {
            ArButton(label, { scope.launch { list.loadMore() } }, busy = list.loadingMore)
        }
    }
}

/** Auto-triggers [PagedList.loadMore] when composed (place at the end of a LazyColumn). */
@Composable
fun InfiniteScrollTrigger(list: PagedList<*>) {
    LaunchedEffect(list, list.items?.size) { if (list.hasMore && !list.loadingMore) list.loadMore() }
    if (list.loadingMore) CenterSpinner(minHeight = 48.dp)
    else if (list.moreError.isNotEmpty()) LoadMoreRow(list)
}

/** Simple busy flag helper for buttons that fire a suspend action. */
class Busy {
    var value by mutableStateOf(false)
}

@Composable
fun rememberBusy(): Busy = remember { Busy() }

@Composable
fun rememberSaveableString(initial: String = ""): MutableState<String> = remember { mutableStateOf(initial) }

fun <T> State<T>.read(): T = value
