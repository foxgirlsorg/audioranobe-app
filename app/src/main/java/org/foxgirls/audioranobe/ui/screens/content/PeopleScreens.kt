package org.foxgirls.audioranobe.ui.screens.content

import org.foxgirls.audioranobe.ui.components.ditheredBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.core.Support
import org.foxgirls.audioranobe.data.AuthorFull
import org.foxgirls.audioranobe.data.LocalAuth
import org.foxgirls.audioranobe.data.NarratorFull
import org.foxgirls.audioranobe.ui.components.AiBadge
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArImage
import org.foxgirls.audioranobe.ui.components.ArMarkdown
import org.foxgirls.audioranobe.ui.components.ArTabs
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CardGrid
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.ImageViewer
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.CardGridSkeleton
import org.foxgirls.audioranobe.ui.components.SectionHeaderSkeleton
import org.foxgirls.audioranobe.ui.components.Skeleton
import org.foxgirls.audioranobe.ui.components.TextSkeleton
import org.foxgirls.audioranobe.ui.components.gridColumns
import org.foxgirls.audioranobe.ui.components.Section
import org.foxgirls.audioranobe.ui.components.StatusBadge
import org.foxgirls.audioranobe.ui.components.TabItem
import org.foxgirls.audioranobe.ui.components.TabsVariant
import org.foxgirls.audioranobe.ui.components.VerifiedBadge
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.components.social.CommentSection
import org.foxgirls.audioranobe.ui.components.social.ReportButton
import org.foxgirls.audioranobe.ui.components.social.SocialLinks
import org.foxgirls.audioranobe.ui.components.social.SubscribeButton
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.pagePadding
import org.foxgirls.audioranobe.ui.swipeTabs
import org.foxgirls.audioranobe.ui.screens.NotFoundScreen
import org.foxgirls.audioranobe.ui.theme.Ar

// ---------- app/narrator/[slug] ----------

@Composable
fun NarratorScreen(slug: String, initialTab: String?) {
    val nav = LocalNav.current
    val auth = LocalAuth.current
    val user by auth.user.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val loader = rememberLoader(slug, keepOnReload = true) { Api.get<NarratorFull>("/narrators/${Routes.enc(slug)}") }
    // Re-fetch once the viewer signs in: can_edit / my_subscription depend on the viewer.
    LaunchedEffect(user?.id) { if (user != null && loader.data != null) loader.reload() }
    var tab by remember { mutableStateOf(initialTab ?: "info") }
    var viewer by remember { mutableStateOf<String?>(null) }

    when (val s = loader.state) {
        is Load.Loading -> { NarratorPageSkeleton(); return }
        is Load.Err -> {
            if (s.notFound) { NotFoundScreen("Такого чтеца не существует, или его страница ещё не опубликована."); return }
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) { ErrorState(s.message, { loader.reload() }, title = "Чтец не найден") }
            return
        }
        is Load.Ok -> {}
    }
    val n = loader.data ?: return
    val canEdit = n.can_edit

    Column(Modifier.fillMaxSize().swipeTabs(listOf("info", "titles", "comments"), tab) { tab = it }.verticalScroll(rememberScrollState()).padding(bottom = pagePadding().calculateBottomPadding())) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(170.dp).clickable(enabled = n.cover_url != null) { viewer = n.cover_url }) {
                if (n.cover_url != null) ArImage(n.cover_thumb_url ?: n.cover_url, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().ditheredBackground(Brush.linearGradient(listOf(Ar.accent.copy(alpha = 0.25f), Ar.surfaceSolid))))
                Box(Modifier.fillMaxSize().ditheredBackground(Brush.verticalGradient(listOf(Color.Transparent, Ar.bg.copy(alpha = 0.85f)))))
            }
            Row(Modifier.statusBarsPadding().padding(8.dp)) {
                IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text, background = Ar.bg.copy(alpha = 0.5f))
            }
            Column(Modifier.fillMaxWidth().padding(top = 120.dp).padding(horizontal = 16.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).border(3.dp, Ar.bg, CircleShape).background(Ar.surfaceRaised).clickable(enabled = n.avatar_url != null) { viewer = n.avatar_url }, contentAlignment = Alignment.Center) {
                    if (n.avatar_url != null) ArImage(n.avatar_thumb_url ?: n.avatar_url, Modifier.fillMaxSize(), shape = CircleShape)
                    else Icon(Lucide.Mic2, null, tint = Ar.textMuted, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.name, color = Ar.white, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    if (n.is_verified) VerifiedBadge(Modifier.padding(end = 4.dp))
                    if (n.is_ai) AiBadge(Modifier.padding(end = 4.dp))
                    if (n.mod_status != "approved") StatusBadge(n.mod_status)
                    if (canEdit) IconBtn(Lucide.Pencil, "Редактировать", { nav.go(Routes.narratorEdit(n.slug)) }, size = 32.dp, iconSize = 15.dp)
                }
                Text("Чтец · на сайте с ${Fmt.date(n.created_at)}", color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SubscribeButton(n.id, n.my_subscription, n.subscribers_count)
                    ReportButton("narrator", n.id, compact = true)
                }
            }
        }

        if (!n.is_self && !n.is_ai && !canEdit) GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), borderColor = Ar.accent.copy(alpha = 0.35f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Mic2, null, tint = Ar.accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Это ваша страница чтеца?", color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Эта страница создана без участия чтеца. Если вы озвучиваете под этим именем — напишите в поддержку, и мы передадим страницу вам.",
                        color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp),
                    )
                    ArButton("Связаться с поддержкой", { Links.external(context, Support.URL) }, kind = ButtonKind.Primary, small = true, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        ArTabs(
            listOf(TabItem("info", "Информация"), TabItem("titles", "Тайтлы", n.titles_count), TabItem("comments", "Комментарии")),
            tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), variant = TabsVariant.Underline,
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            when (tab) {
                "info" -> {
                    if (n.bio.isNotBlank() || n.socials.isNotEmpty()) GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Eyebrow(if (n.bio.isNotBlank()) "О себе" else "Ссылки")
                        if (n.bio.isNotBlank()) Box(Modifier.padding(top = 8.dp)) { ArMarkdown(n.bio, media = "image") }
                        if (n.socials.isNotEmpty()) SocialLinks(n.socials, Modifier.padding(top = 10.dp))
                    }
                    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        ProfileStat(Lucide.Library, Fmt.count(n.titles_count), Fmt.plural(n.titles_count, "тайтл", "тайтла", "тайтлов"))
                        ProfileStat(Lucide.Headphones, Fmt.trimNum(n.seconds_narrated / 3600.0), "часов озвучено", Modifier.padding(top = 10.dp))
                        ProfileStat(Lucide.Users, Fmt.count(n.subscribers_count), Fmt.plural(n.subscribers_count, "подписчик", "подписчика", "подписчиков"), Modifier.padding(top = 10.dp))
                    }
                    if (auth.isMod && !n.admin_contact.isNullOrBlank()) GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Eyebrow("Контакт для администрации")
                        Text(n.admin_contact, color = Ar.text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                        Text("Виден только модераторам и администраторам.", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    NarratorPosts(n.id, canEdit, Modifier.padding(top = 8.dp))
                }
                "titles" -> if (n.titles.isNotEmpty()) CardGrid(n.titles) else EmptyState("Пока нет тайтлов", "Этот чтец пока не опубликовал ни одной аудиокниги.", Lucide.BookOpen)
                else -> CommentSection("narrator", n.id, n.comments)
            }
        }
    }
    ImageViewer(viewer != null, listOfNotNull(viewer), onClose = { viewer = null })
}

@Composable
private fun ProfileStat(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Ar.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ar.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(value, color = Ar.white, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            Text(label, color = Ar.textMuted, fontSize = 12.sp)
        }
    }
}

/** Placeholder for [NarratorScreen]: cover banner, overlapping avatar, name, actions, bio panel, stats, then a card grid. */
@Composable
private fun NarratorPageSkeleton() {
    val columns = gridColumns(LocalConfiguration.current.screenWidthDp)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth()) {
            Skeleton(Modifier.fillMaxWidth().height(170.dp), RoundedCornerShape(0.dp))
            Skeleton(Modifier.statusBarsPadding().padding(8.dp).size(40.dp), CircleShape)
            Column(Modifier.fillMaxWidth().padding(top = 120.dp).padding(horizontal = 16.dp)) {
                Skeleton(Modifier.size(96.dp), CircleShape)
                Spacer(Modifier.height(8.dp))
                Skeleton(Modifier.width(180.dp).height(22.dp))
                Skeleton(Modifier.padding(top = 6.dp).width(140.dp).height(12.dp))
                Skeleton(Modifier.padding(top = 10.dp).width(200.dp).height(34.dp), CircleShape)
            }
        }
        Row(Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            repeat(3) { Skeleton(Modifier.width(72.dp).height(14.dp)) }
        }
        GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { TextSkeleton(lines = 3) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(Modifier.size(38.dp), CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Skeleton(Modifier.width(40.dp).height(18.dp))
                        Skeleton(Modifier.padding(top = 4.dp).width(60.dp).height(11.dp))
                    }
                }
            }
        }
        SectionHeaderSkeleton(Modifier.padding(start = 16.dp, top = 22.dp), eyebrowWidth = 80.dp, titleWidth = 150.dp)
        CardGridSkeleton(count = columns * 3, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** Placeholder for [AuthorScreen]: back button, eyebrow and name, bio panel, then a titles section of cards. */
@Composable
private fun AuthorPageSkeleton() {
    val columns = gridColumns(LocalConfiguration.current.screenWidthDp)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(40.dp), CircleShape)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Skeleton(Modifier.width(60.dp).height(11.dp))
        Skeleton(Modifier.padding(top = 4.dp).fillMaxWidth(0.7f).height(26.dp))
        Skeleton(Modifier.padding(top = 4.dp).width(100.dp).height(13.dp))
        GlassPanel(Modifier.fillMaxWidth().padding(top = 16.dp)) { TextSkeleton(lines = 3) }
        Spacer(Modifier.height(24.dp))
        SectionHeaderSkeleton(eyebrowWidth = 70.dp, titleWidth = 160.dp)
        CardGridSkeleton(count = columns * 3)
    }
}

// ---------- app/author/[id] ----------

@Composable
fun AuthorScreen(ref: String) {
    val nav = LocalNav.current
    val loader = rememberLoader(ref) { Api.get<AuthorFull>("/authors/${Routes.enc(ref)}") }
    when (val s = loader.state) {
        is Load.Loading -> AuthorPageSkeleton()
        is Load.Err -> if (s.notFound) NotFoundScreen("Такого автора не существует, или его страница ещё не опубликована.")
        else Column(Modifier.fillMaxSize().statusBarsPadding().padding(pagePadding())) { ErrorState(s.message, { loader.reload() }, title = "Автор не найден") }
        is Load.Ok -> {
            val a = s.data
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text)
                    Spacer(Modifier.weight(1f))
                    if (a.can_edit) IconBtn(Lucide.Pencil, "Редактировать", { nav.go(Routes.authorEdit(a.id)) }, size = 36.dp, iconSize = 16.dp)
                }
                Spacer(Modifier.height(6.dp))
                Eyebrow("Автор")
                Text(a.name, color = Ar.white, fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Тайтлов: ${a.titles_count}", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                if (a.links.isNotEmpty()) SocialLinks(a.links, Modifier.padding(top = 8.dp))
                if (a.bio.isNotBlank()) GlassPanel(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Eyebrow("Об авторе")
                    Box(Modifier.padding(top = 8.dp)) { ArMarkdown(a.bio) }
                }
                Spacer(Modifier.height(24.dp))
                Section("Произведения", "автора", eyebrow = "Каталог") {
                    if (a.titles.isNotEmpty()) CardGrid(a.titles)
                    else EmptyState("Пока нет тайтлов", "К этому автору пока не привязано ни одной аудиокниги.", Lucide.BookOpen)
                }
            }
        }
    }
}
