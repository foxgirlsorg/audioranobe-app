package org.foxgirls.audioranobe.ui.screens.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.core.Limits
import org.foxgirls.audioranobe.core.msg
import org.foxgirls.audioranobe.data.DonateConfig
import org.foxgirls.audioranobe.data.DonateGoal
import org.foxgirls.audioranobe.data.LegalDoc
import org.foxgirls.audioranobe.data.RecentDonation
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArMarkdown
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.CenterSpinner
import org.foxgirls.audioranobe.ui.components.EmptyState
import org.foxgirls.audioranobe.ui.components.ErrorState
import org.foxgirls.audioranobe.ui.components.FieldLabel
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.GoalBar
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.Load
import org.foxgirls.audioranobe.ui.components.OutlineChip
import org.foxgirls.audioranobe.ui.components.PageHeader
import org.foxgirls.audioranobe.ui.components.rememberLoader
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.pagePadding
import org.foxgirls.audioranobe.ui.screens.NotFoundScreen
import org.foxgirls.audioranobe.ui.theme.Ar
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ---------- app/donate ----------

private val PERIOD_LABEL = mapOf("once" to "сбор", "weekly" to "за неделю", "monthly" to "за месяц")
private val SERVICE_LABEL = mapOf("kofi" to "Ko-fi", "boosty" to "Boosty", "other" to "Другое")

private data class DonateService(val key: String, val name: String, val region: String, val hint: String)

private val SERVICES = listOf(
    DonateService("kofi", "Ko-fi", "Карты · PayPal", "Разовая поддержка или ежемесячная подписка."),
    DonateService("boosty", "Boosty", "Банковские карты", "Разовая поддержка или подписка."),
)

@Serializable
private data class RecentDonations(val items: List<RecentDonation> = emptyList())

/** Donation goal bar with figures (components/GoalBar). */
@Composable
fun DonateGoalBar(goal: DonateGoal, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (!goal.enabled) return
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.title.ifBlank { "Цель сбора" }, color = Ar.text, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (compact) Text("${Fmt.usd(goal.raised_cents)} / ${Fmt.usd(goal.target_cents)}", color = Ar.textMuted, fontSize = 11.sp)
            else Text(PERIOD_LABEL[goal.period] ?: goal.period, color = Ar.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        GoalBar(goal.pct)
        if (!compact) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.usd(goal.raised_cents), color = Ar.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(" / ${Fmt.usd(goal.target_cents)}", color = Ar.textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${goal.pct.toInt()}%", color = Ar.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DonateScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val loader = rememberLoader(Unit) { Api.get<DonateConfig>("/donations/config") }
    val recent = rememberLoader(Unit) { try { Api.get<RecentDonations>("/donations/recent").items } catch (_: Exception) { emptyList() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) { IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text) }
        PageHeader("Поддержка", "Поддержать", "проект")
        Text("AudioRanobe живёт на пожертвования. Любая сумма помогает оплачивать серверы и хранилище.", color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(16.dp))
        when (val s = loader.state) {
            is Load.Loading -> CenterSpinner()
            is Load.Err -> ErrorState(s.message, { loader.reload() })
            is Load.Ok -> {
                val cfg = s.data
                if (cfg.goal.enabled) GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) { DonateGoalBar(cfg.goal) }
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), borderColor = Ar.accent.copy(alpha = 0.35f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Lucide.AtSign, null, tint = Ar.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Укажите свой ник на сайте в комментарии к платежу — так пожертвование привяжется к вашему аккаунту.", color = Ar.text, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
                val available = SERVICES.filter { (if (it.key == "kofi") cfg.kofi else cfg.boosty).isNotBlank() }
                if (available.isEmpty()) EmptyState("Пока нет способов", "Способы пожертвования ещё не настроены.")
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 2) {
                    available.forEach { sv ->
                        val url = if (sv.key == "kofi") cfg.kofi else cfg.boosty
                        GlassPanel(Modifier.weight(1f), onClick = { Links.external(context, url) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Lucide.Heart, null, tint = Ar.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(sv.name, color = Ar.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Icon(Lucide.ExternalLink, null, tint = Ar.textMuted, modifier = Modifier.size(14.dp))
                            }
                            Text(sv.region, color = Ar.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            Text(sv.hint, color = Ar.textMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Award, null, tint = Ar.amber, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Пожертвование от $${cfg.badge_min} даёт бейдж «Донатер» на вашем профиле.", color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                }
                val items = recent.data.orEmpty()
                if (items.isNotEmpty()) {
                    Text("Последние пожертвования", color = Ar.white, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
                    GlassPanel(Modifier.fillMaxWidth()) {
                        items.forEachIndexed { i, r ->
                            if (i > 0) HairlineDivider(Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.name, color = Ar.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(SERVICE_LABEL[r.service] ?: r.service, color = Ar.textMuted, fontSize = 12.sp)
                                Spacer(Modifier.width(12.dp))
                                Text("${Fmt.trimNum(r.amount)} ${r.currency}", color = Ar.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- app/dmca ----------

private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

@Composable
fun DmcaScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var contentUrls by remember { mutableStateOf(listOf("")) }
    var originalUrls by remember { mutableStateOf(listOf("")) }
    var proofUrl by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var formError by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    fun submit() {
        val errs = mutableMapOf<String, String>()
        if (name.isBlank()) errs["name"] = "Введите ваше имя"
        if (!EMAIL_RE.matches(email.trim())) errs["email"] = "Введите корректный email"
        if (country.isBlank()) errs["country"] = "Укажите страну"
        if (contentUrls.none { it.isNotBlank() }) errs["content_url"] = "Укажите хотя бы одну ссылку на контент"
        if (originalUrls.none { it.isNotBlank() }) errs["original_url"] = "Укажите хотя бы одну ссылку на оригинал"
        if (proofUrl.isBlank()) errs["proof_url"] = "Приложите подтверждение авторства"
        if (description.isBlank()) errs["description"] = "Опишите, в чём состоит нарушение"
        errors = errs; formError = ""
        if (errs.isNotEmpty()) return
        submitting = true
        scope.launch {
            try {
                Api.post<Unit>("/dmca", buildJsonObject {
                    put("name", name.trim()); put("email", email.trim()); put("country", country.trim())
                    put("content_urls", buildJsonArray { contentUrls.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                    put("original_urls", buildJsonArray { originalUrls.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                    put("proof_url", proofUrl.trim()); put("description", description.trim())
                })
                success = true
            } catch (e: Exception) { formError = e.msg(); submitting = false }
        }
    }

    @Composable
    fun UrlList(label: String, urls: List<String>, onChange: (List<String>) -> Unit, placeholder: String, error: String?) {
        FieldLabel(label)
        urls.forEachIndexed { i, url ->
            Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ArTextField(url, { v -> onChange(urls.mapIndexed { j, u -> if (j == i) v else u }) }, Modifier.weight(1f), placeholder = placeholder, maxLength = Limits.dmcaUrl, keyboardType = KeyboardType.Uri)
                IconBtn(Lucide.Trash2, "Удалить ссылку", { onChange(if (urls.size <= 1) listOf("") else urls.filterIndexed { j, _ -> j != i }) }, size = 36.dp, iconSize = 15.dp)
            }
        }
        ArButton("Добавить ссылку", { onChange(urls + "") }, kind = ButtonKind.Ghost, icon = Lucide.Plus, small = true)
        if (error != null) Text(error, color = Ar.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) { IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text) }
        PageHeader("Правообладателям", "Политика", "DMCA")
        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Text("Защита авторских прав", color = Ar.white, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "AudioRanobe уважает права интеллектуальной собственности и ожидает того же от пользователей. Если вы являетесь правообладателем или его представителем и считаете, что материалы на платформе нарушают ваши авторские права, вы можете подать жалобу через форму ниже.",
                color = Ar.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (success) GlassPanel(Modifier.fillMaxWidth(), borderColor = Ar.ok.copy(alpha = 0.5f)) {
            Text("Ваша жалоба DMCA успешно отправлена и будет рассмотрена в ближайшее время.", color = Ar.ok, fontSize = 14.sp, lineHeight = 20.sp)
        } else GlassPanel(Modifier.fillMaxWidth()) {
            Text("Подать жалобу DMCA", color = Ar.white, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            if (formError.isNotEmpty()) Text(formError, color = Ar.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            ArTextField(name, { name = it }, label = "Ваше имя", placeholder = "Иван Иванов", maxLength = Limits.dmcaName, error = errors["name"])
            Spacer(Modifier.height(10.dp))
            ArTextField(email, { email = it }, label = "Email для связи", placeholder = "you@example.com", maxLength = Limits.dmcaEmail, error = errors["email"], keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(10.dp))
            ArTextField(country, { country = it }, label = "Страна", placeholder = "Россия", maxLength = Limits.dmcaCountry, error = errors["country"])
            Spacer(Modifier.height(10.dp))
            UrlList("Ссылки на материалы, которые нужно удалить", contentUrls, { contentUrls = it }, "https://audioranobe.com/title/example", errors["content_url"])
            Spacer(Modifier.height(10.dp))
            UrlList("Ссылки на оригинальное размещение этих материалов", originalUrls, { originalUrls = it }, "https://…", errors["original_url"])
            Spacer(Modifier.height(10.dp))
            ArTextField(proofUrl, { proofUrl = it }, label = "Подтверждение того, что вы автор", placeholder = "Ссылка на страницу автора, договор, скан документа или иное подтверждение…", maxLength = Limits.dmcaUrl, error = errors["proof_url"], singleLine = false, minLines = 2)
            Spacer(Modifier.height(10.dp))
            ArTextField(description, { description = it }, label = "Описание проблемы", placeholder = "Опишите, каким образом нарушены ваши авторские права…", maxLength = Limits.dmcaDescription, error = errors["description"], singleLine = false, minLines = 4)
            Spacer(Modifier.height(14.dp))
            ArButton(if (submitting) "Отправляем…" else "Отправить жалобу", { submit() }, kind = ButtonKind.Primary, icon = Lucide.Send, busy = submitting, fullWidth = true)
        }
    }
}

// ---------- app/legal/{rules,privacy,terms} ----------

private data class LegalMeta(val title: String, val accent: String, val subtitle: String?, val errorTitle: String)

private val LEGAL_META = mapOf(
    "rules" to LegalMeta("Правила", "сервиса", null, "Не удалось загрузить правила"),
    "privacy" to LegalMeta("Политика", "конфиденциальности", "Как мы обращаемся с вашими данными.", "Не удалось загрузить политику конфиденциальности"),
    "terms" to LegalMeta("Условия", "использования", "Что вы принимаете, пользуясь сервисом.", "Не удалось загрузить условия"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegalScreen(doc: String) {
    val meta = LEGAL_META[doc] ?: run { NotFoundScreen(); return }
    val nav = LocalNav.current
    val loader = rememberLoader(doc) { Api.get<LegalDoc>("/legal/$doc") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) { IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text) }
        PageHeader("Документы", meta.title, meta.accent)
        if (meta.subtitle != null) Text(meta.subtitle, color = Ar.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
        when (val s = loader.state) {
            is Load.Loading -> CenterSpinner()
            is Load.Err -> ErrorState(s.message, { loader.reload() }, title = meta.errorTitle)
            is Load.Ok -> {
                val body = s.data.body
                val sections = remember(body) { body.lines().filter { it.startsWith("## ") }.map { it.substring(3).trim() } }
                if (doc == "rules" && sections.isNotEmpty()) FlowRow(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEach { OutlineChip(it) }
                }
                Box(Modifier.fillMaxWidth()) { ArMarkdown(body) }
            }
        }
    }
}
