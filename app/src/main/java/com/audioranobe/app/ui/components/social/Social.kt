package com.audioranobe.app.ui.components.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.Fmt
import com.audioranobe.app.core.Labels
import com.audioranobe.app.core.Limits
import com.audioranobe.app.data.LibraryEntry
import com.audioranobe.app.data.LibraryEntryBrief
import com.audioranobe.app.data.LocalAuth
import com.audioranobe.app.ui.components.ArButton
import com.audioranobe.app.ui.components.ArModal
import com.audioranobe.app.ui.components.ArTextField
import com.audioranobe.app.ui.components.ButtonKind
import com.audioranobe.app.ui.components.Eyebrow
import com.audioranobe.app.ui.components.GlassPanel
import com.audioranobe.app.ui.components.IconBtn
import com.audioranobe.app.ui.components.SelectMenu
import com.audioranobe.app.ui.components.SelectOption
import com.audioranobe.app.ui.icons.Lucide
import com.audioranobe.app.ui.nav.LocalNav
import com.audioranobe.app.ui.nav.Routes
import com.audioranobe.app.ui.theme.Ar
import com.audioranobe.app.ui.toast.toast
import com.audioranobe.app.ui.toast.toastError
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------- components/RatingStars ----------

@Composable
fun RatingStars(value: Double?, count: Int, my: Int?, onRate: ((Int?) -> Unit)?, modifier: Modifier = Modifier) {
    val display = (my ?: value ?: 0.0).toDouble()
    Column(modifier) {
        Row {
            for (i in 1..10) {
                val frac = (display - (i - 1)).coerceIn(0.0, 1.0)
                Box(Modifier.size(24.dp).then(if (onRate != null) Modifier.clickable { onRate(if (i == my) null else i) } else Modifier), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Star, null, tint = Ar.borderStrong, modifier = Modifier.size(18.dp))
                    if (frac > 0) Box(Modifier.size(18.dp).clip(FractionShape(frac.toFloat()))) {
                        Icon(Lucide.Star, null, tint = if (my != null) Ar.accentHover else Ar.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            if (value != null) {
                Text(Fmt.rating(value), color = Ar.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(" · Оценок: $count", color = Ar.textMuted, fontSize = 12.sp)
            } else Text("Оценок пока нет", color = Ar.textMuted, fontSize = 12.sp)
            if (my != null) Text(" · ваша: $my", color = Ar.accent, fontSize = 12.sp)
        }
    }
}

/** Clips the left [fraction] of a box (used for fractional stars). */
private class FractionShape(private val fraction: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
        androidx.compose.ui.graphics.Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width * fraction, size.height))
}

/** components/RatingBars */
@Composable
fun RatingBars(distribution: Map<String, Int>, modifier: Modifier = Modifier) {
    val rows = (10 downTo 1).map { it to (distribution[it.toString()] ?: 0) }
    val max = maxOf(1, rows.maxOf { it.second })
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for ((v, n) in rows) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.toString(), color = Ar.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Ar.fill06)) {
                Box(Modifier.fillMaxWidth(n.toFloat() / max).height(6.dp).background(Brush.horizontalGradient(listOf(Ar.accent.copy(alpha = 0.55f), Ar.accent)), CircleShape))
            }
            Spacer(Modifier.width(9.dp))
            Text(n.toString(), color = Ar.textMuted, fontSize = 10.sp, modifier = Modifier.width(34.dp))
        }
    }
}

// ---------- components/FavoriteButton ----------

@Serializable
private data class FavRes(val my_favorite: Boolean = false, val favorites_count: Int = 0)

@Composable
fun FavoriteButton(titleId: Int, favorited: Boolean, count: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val user by LocalAuth.current.user.collectAsStateWithLifecycle()
    var fav by remember(titleId, favorited) { mutableStateOf(favorited) }
    var n by remember(titleId, count) { mutableStateOf(count) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun toggle() {
        if (user == null) { toastError("Войдите, чтобы добавлять в избранное"); return }
        if (busy) return
        busy = true
        val next = !fav; val pf = fav; val pn = n
        fav = next; n = maxOf(0, n + if (next) 1 else -1)
        scope.launch {
            try {
                val r = if (next) Api.put<FavRes>("/me/favorites/$titleId") else Api.delete<FavRes>("/me/favorites/$titleId")
                fav = r.my_favorite; n = r.favorites_count
            } catch (e: Exception) { fav = pf; n = pn; toastError(e) } finally { busy = false }
        }
    }
    val color = if (fav) Ar.accent else Ar.textSecondary
    Row(
        modifier.clip(CircleShape).background(if (fav) Ar.accentSoft else Ar.fill04).border(1.dp, if (fav) Ar.accent.copy(alpha = 0.5f) else Ar.border, CircleShape)
            .clickable { toggle() }.height(44.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Heart, if (fav) "Убрать из избранного" else "Добавить в избранное", tint = color, modifier = Modifier.size(15.dp))
        if (!compact) { Spacer(Modifier.width(6.dp)); Text(if (fav) "В избранном" else "В избранное", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(6.dp))
        Text(n.toString(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- components/SubscribeButton ----------

@Serializable
private data class SubRes(val my_subscription: Boolean = false, val subscribers_count: Int = 0)

@Composable
fun SubscribeButton(narratorId: Int, subscribed: Boolean, count: Int, modifier: Modifier = Modifier) {
    val user by LocalAuth.current.user.collectAsStateWithLifecycle()
    var sub by remember(narratorId, subscribed) { mutableStateOf(subscribed) }
    var n by remember(narratorId, count) { mutableStateOf(count) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun toggle() {
        if (user == null) { toastError("Войдите, чтобы подписаться на чтецов"); return }
        if (busy) return
        busy = true
        val next = !sub; val ps = sub; val pn = n
        sub = next; n = maxOf(0, n + if (next) 1 else -1)
        scope.launch {
            try {
                val r = if (next) Api.put<SubRes>("/narrators/$narratorId/subscribe") else Api.delete<SubRes>("/narrators/$narratorId/subscribe")
                sub = r.my_subscription; n = r.subscribers_count
            } catch (e: Exception) { sub = ps; n = pn; toastError(e) } finally { busy = false }
        }
    }
    val color = if (sub) Ar.accent else Ar.text
    Row(
        modifier.clip(CircleShape).background(if (sub) Ar.accentSoft else Ar.fill04).border(1.dp, if (sub) Ar.accent.copy(alpha = 0.5f) else Ar.border, CircleShape)
            .clickable { toggle() }.height(44.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (sub) Lucide.BellRing else Lucide.Bell, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (sub) "Вы подписаны" else "Подписаться", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text(n.toString(), color = if (sub) Ar.accent else Ar.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- components/ReportButton ----------

@Composable
fun ReportButton(targetType: String, targetId: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val user by LocalAuth.current.user.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(
        modifier.clip(CircleShape).clickable {
            if (user == null) toastError("Войдите, чтобы пожаловаться на контент") else { reason = ""; open = true }
        }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Flag, "Пожаловаться модераторам", tint = Ar.textMuted, modifier = Modifier.size(13.dp))
        if (!compact) { Spacer(Modifier.width(5.dp)); Text("Жалоба", color = Ar.textMuted, fontSize = 12.sp) }
    }
    ArModal(open, { open = false }, "Жалоба на контент") {
        Text("Расскажите модераторам, что не так. Жалобы анонимны для других пользователей.", color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(10.dp))
        ArTextField(reason, { reason = it }, placeholder = "В чём проблема?", singleLine = false, minLines = 4, maxLength = 1000, showCounter = true)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ArButton("Отправить жалобу", {
                val r = reason.trim()
                if (r.isEmpty()) { toastError("Опишите проблему"); return@ArButton }
                busy = true
                scope.launch {
                    try {
                        Api.post<Unit>("/reports", buildJsonObject { put("target_type", targetType); put("target_id", targetId); put("reason", r) })
                        open = false
                        toast("Жалоба отправлена. Спасибо, что помогаете поддерживать чистоту AudioRanobe.")
                    } catch (e: Exception) { toastError(e) } finally { busy = false }
                }
            }, kind = ButtonKind.Primary, busy = busy)
        }
    }
}

// ---------- components/LibraryWidget ----------

@Composable
fun LibraryWidget(
    titleId: Int,
    entry: LibraryEntryBrief?,
    onChange: (LibraryEntryBrief?) -> Unit,
    modifier: Modifier = Modifier,
    userId: Int? = null,
) {
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val loading by auth.loading.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var note by remember(entry?.note) { mutableStateOf(entry?.note ?: "") }
    var busy by remember { mutableStateOf(false) }
    var savingNote by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (loading) return
    val foreign = userId != null && user != null && userId != user!!.id
    val path = if (foreign) "/mod/users/$userId/library/$titleId" else "/me/library/$titleId"

    GlassPanel(modifier) {
        Eyebrow(if (foreign) "Библиотека пользователя" else "Моя библиотека", icon = Lucide.BookmarkPlus)
        Spacer(Modifier.height(10.dp))
        if (user == null) {
            Text("Отслеживайте прослушивания — добавьте этот тайтл в свою библиотеку.", color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            ArButton("Войти", { nav.go(Routes.LOGIN) }, icon = Lucide.LogIn, small = true)
            return@GlassPanel
        }
        val options = listOf(SelectOption("", "Не в библиотеке")) + Labels.libraryValues.map { SelectOption(it, Labels.libraryStatus[it]!!) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectMenu(entry?.status ?: "", options, { next ->
                scope.launch {
                    busy = true
                    try {
                        if (next.isEmpty()) { Api.delete<Unit>(path); onChange(null); note = "" }
                        else if (next != entry?.status) { val r = Api.put<LibraryEntry>(path, buildJsonObject { put("status", next) }); onChange(LibraryEntryBrief(r.status, r.note)) }
                    } catch (e: Exception) { toastError(e) } finally { busy = false }
                }
            }, Modifier.weight(1f), enabled = !busy)
            if (entry != null) IconBtn(Lucide.X, "Удалить из библиотеки", {
                scope.launch { busy = true; try { Api.delete<Unit>(path); onChange(null); note = "" } catch (e: Exception) { toastError(e) } finally { busy = false } }
            }, enabled = !busy)
        }
        if (entry != null) {
            Spacer(Modifier.height(10.dp))
            ArTextField(note, { note = it }, label = "Заметка", placeholder = if (foreign) "Заметка в списке этого пользователя…" else "Видна всем в вашем профиле…", singleLine = false, minLines = 3, maxLength = Limits.libraryNote, showCounter = true)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ArButton("Сохранить заметку", {
                    scope.launch {
                        savingNote = true
                        try { val r = Api.put<LibraryEntry>(path, buildJsonObject { put("note", note) }); onChange(LibraryEntryBrief(r.status, r.note)); toast("Заметка сохранена") } catch (e: Exception) { toastError(e) } finally { savingNote = false }
                    }
                }, small = true, busy = savingNote, enabled = note != (entry.note))
            }
        }
    }
}

/** Auto-trigger something once per key (helper for LaunchedEffect-based optimistic updates). */
@Composable
fun OnceEffect(key: Any?, block: suspend () -> Unit) { LaunchedEffect(key) { block() } }
