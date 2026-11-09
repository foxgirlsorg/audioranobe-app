package org.foxgirls.audioranobe.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.core.Fmt
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.tts.SystemSpeaker
import org.foxgirls.audioranobe.tts.TtsModels
import org.foxgirls.audioranobe.ui.components.TtsDownloadDialog
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.FieldLabel
import org.foxgirls.audioranobe.ui.components.Pill
import org.foxgirls.audioranobe.ui.components.SelectMenu
import org.foxgirls.audioranobe.ui.components.SelectOption
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.theme.Ar

private val VOICES = listOf("baya" to "Байя", "kseniya" to "Ксения", "xenia" to "Ксения 2", "aidar" to "Айдар", "eugene" to "Евгений")
private const val DEFAULT_PAUSE = 0.35f

/** Text style, engine, voice, pause between phrases and the chapter announcement. A sound change restarts the chapter from where it is. */
@Composable
fun TtsSettingsSheet(open: Boolean, onClose: () -> Unit, prompter: Boolean, onPrompter: (Boolean) -> Unit) {
    val prefs = Stores.prefs
    var engine by remember(open) { mutableStateOf(prefs.ttsEngine) }
    var voice by remember(open) { mutableStateOf(prefs.ttsVoice) }
    var sysVoice by remember(open) { mutableStateOf(prefs.ttsSystemVoice) }
    val context = LocalContext.current
    val sysVoices by produceState(emptyList<String>()) { SystemSpeaker.russianVoices(context) { value = it } }
    var pause by remember(open) { mutableStateOf(prefs.ttsPause.takeIf { it >= 0 }) }
    var announce by remember(open) { mutableStateOf(prefs.ttsAnnounce) }
    var askModels by remember { mutableStateOf(false) }
    fun useSilero() { engine = "silero"; prefs.ttsEngine = "silero"; PlayerController.reloadTts() }
    TtsDownloadDialog(askModels, { askModels = false }, ::useSilero, lead = "Голос Silero работает прямо на телефоне.")
    ArSheet(open, onClose) {
        Row(Modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ar.accentSoft), contentAlignment = Alignment.Center) {
                Icon(Lucide.SlidersHorizontal, null, tint = Ar.accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Настройки озвучки", color = Ar.white, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("Глава продолжится с того же места", color = Ar.textMuted, fontSize = 12.sp)
            }
        }

        FieldLabel("Вид текста")
        Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Фокус", active = !prompter, onClick = { onPrompter(false) })
            Pill("Суфлёр", active = prompter, onClick = { onPrompter(true) })
        }

        FieldLabel("Движок")
        Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Silero", active = engine == "silero", onClick = { if (engine != "silero") { if (TtsModels.installed.value) useSilero() else askModels = true } })
            Pill("Встроенный", active = engine == "system", onClick = { if (engine != "system") { engine = "system"; prefs.ttsEngine = "system"; PlayerController.reloadTts() } })
        }

        if (engine == "silero") SelectMenu(
            voice, listOf(SelectOption<String?>(null, "Как в главе")) + VOICES.map { (id, label) -> SelectOption<String?>(id, label) },
            { v -> if (v != voice) { voice = v; prefs.ttsVoice = v; PlayerController.reloadTts() } },
            Modifier.fillMaxWidth().padding(bottom = 16.dp), label = "Голос",
        ) else SelectMenu(
            sysVoice, listOf(SelectOption<String?>(null, "Авто")) + sysVoices.map { SelectOption<String?>(it, it) },
            { v -> if (v != sysVoice) { sysVoice = v; prefs.ttsSystemVoice = v; PlayerController.reloadTts() } },
            Modifier.fillMaxWidth().padding(bottom = 16.dp), label = "Голос",
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Пауза между фразами", modifier = Modifier.weight(1f))
            val p = pause
            Text(if (p == null) "Стандартная" else "${Fmt.trimNum(p.toDouble())} с", color = Ar.text, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            if (p != null) Text(
                "Сбросить", color = Ar.accent, fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp).clickable { pause = null; prefs.ttsPause = -1f; PlayerController.reloadTts() },
            )
        }
        Slider(
            value = pause ?: DEFAULT_PAUSE,
            onValueChange = { pause = (Math.round(it * 20) / 20f) },
            onValueChangeFinished = { pause?.let { prefs.ttsPause = it }; PlayerController.reloadTts() },
            valueRange = 0f..1.5f,
            colors = SliderDefaults.colors(thumbColor = Ar.accent, activeTrackColor = Ar.accent, inactiveTrackColor = Ar.fill08),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        ArToggle(
            announce ?: true, { v -> announce = v; prefs.ttsAnnounce = v; PlayerController.reloadTts() },
            "Объявлять главу", "Перед текстом прозвучит «Том 2 глава 5 - Название».",
        )
        Spacer(Modifier.height(8.dp))
    }
}
