package org.foxgirls.audioranobe.ui.screens.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.AppVersion
import org.foxgirls.audioranobe.core.Updater
import org.foxgirls.audioranobe.ui.toast.ToastKind
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.tts.TtsModels
import org.foxgirls.audioranobe.ui.components.ConfirmDialog
import org.foxgirls.audioranobe.ui.components.TtsDownloadDialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.foxgirls.audioranobe.core.Support
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.GlassPanel
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.MenuRow
import org.foxgirls.audioranobe.ui.components.SectionTitle
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.Links
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.pagePadding
import org.foxgirls.audioranobe.ui.theme.Ar

/** The site's footer links, gathered on one screen behind the user menu's «Другое». */
@Composable
fun OtherScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(pagePadding())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) { IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, tint = Ar.text) }
        Eyebrow("AudioRanobe")
        SectionTitle("Другое", null, Modifier.padding(top = 4.dp, bottom = 14.dp), size = 24)

        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
            Eyebrow("Проект", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
            MenuRow(Lucide.Heart, "Поддержать проект", { nav.go(Routes.DONATE) }, tint = Ar.accent)
        }
        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
            Eyebrow("Документы", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
            MenuRow(Lucide.ScrollText, "Правила", { nav.go(Routes.legal("rules")) })
            MenuRow(Lucide.FileText, "Условия использования", { nav.go(Routes.legal("terms")) })
            MenuRow(Lucide.Lock, "Политика конфиденциальности", { nav.go(Routes.legal("privacy")) })
            MenuRow(Lucide.Shield, "DMCA", { nav.go(Routes.DMCA) })
        }
        GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
            Eyebrow("Связь", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
            MenuRow(Lucide.Mail, Support.EMAIL, { Links.external(context, "mailto:${Support.EMAIL}") })
            MenuRow(Lucide.MessageCircle, "Бот поддержки в Telegram", { Links.external(context, Support.BOT) })
            MenuRow(Lucide.Radio, "Канал в Telegram", { Links.external(context, Support.CHANNEL) })
            MenuRow(Lucide.Globe, "foxgirls.org", { Links.external(context, "https://foxgirls.org") })
        }
        if (org.foxgirls.audioranobe.BuildConfig.DEBUG) GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
            Eyebrow("Отладка", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
            MenuRow(Lucide.Download, "Показать тестовое обновление", { Updater.simulate() })
        }
        TtsModelsPanel()
        if (Updater.enabled) {
            val checking by Updater.checking.collectAsStateWithLifecycle()
            GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
                Eyebrow("Приложение", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
                MenuRow(Lucide.RefreshCw, if (checking) "Проверяем…" else "Проверить обновления", {
                    if (!checking) Updater.check(manual = true) { release, error ->
                        when {
                            error != null -> toastError(error)
                            release == null || !Updater.isNewer(release.version, AppVersion.VERSION) -> toast("У вас последняя версия", ToastKind.INFO)
                        }
                    }
                })
                MenuRow(Lucide.Code, "Исходный код и релизы", { Links.external(context, "https://github.com/${Updater.repo}") })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("AudioRanobe для Android · ${AppVersion.VERSION} «${AppVersion.NAME}»", color = Ar.textMuted, fontSize = 12.sp)
        Text("© 2026 foxgirls.org", color = Ar.textMuted, fontSize = 12.sp)
    }
}

/** The on-device narrator's files: size, download and delete. They are fetched on the first read-aloud, or here. */
@Composable
private fun TtsModelsPanel() {
    val installed by TtsModels.installed.collectAsStateWithLifecycle()
    val bytes by TtsModels.bytes.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp), padding = PaddingValues(4.dp)) {
        Eyebrow("ИИ Озвучка", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
        if (installed) Text(
            "Модель Silero на устройстве (${Fmt.bytes(bytes)}).",
            color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (installed) MenuRow(Lucide.Trash2, "Удалить модели озвучки", { confirm = true }, tint = Ar.danger)
        else MenuRow(Lucide.Download, "Скачать модели озвучки", { download = true })
    }
    ConfirmDialog(
        confirm, { confirm = false }, { TtsModels.remove(); toast("Модели озвучки удалены") },
        "Удалить модели озвучки?",
        "Освободится ${Fmt.bytes(bytes)}. Тексты скачанных книг останутся, но чтобы читать их вслух, модели придётся скачать снова — приложение сделает это само при запуске такой книги.",
        danger = true, confirmLabel = "Удалить",
    )
    TtsDownloadDialog(download, { download = false }, { toast("Модели озвучки скачаны") })
}
