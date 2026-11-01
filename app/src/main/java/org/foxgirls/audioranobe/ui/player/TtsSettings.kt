package org.foxgirls.audioranobe.ui.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import org.foxgirls.audioranobe.tts.SystemSpeaker
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.ui.components.ArSheet
import org.foxgirls.audioranobe.ui.components.ArToggle
import org.foxgirls.audioranobe.ui.components.Pill
import org.foxgirls.audioranobe.ui.theme.Ar

private val VOICES = listOf("baya" to "Байя", "kseniya" to "Ксения", "xenia" to "Ксения 2", "aidar" to "Айдар", "eugene" to "Евгений")
private val PAUSES = listOf(0.1f, 0.25f, 0.35f, 0.5f, 0.75f, 1f)

/** Voice, pause between phrases and the chapter announcement of the on-device narrator. A change restarts the chapter from where it is. */
@Composable
fun TtsSettingsSheet(open: Boolean, onClose: () -> Unit) {
    val prefs = Stores.prefs
    var engine by remember(open) { mutableStateOf(prefs.ttsEngine) }
    var voice by remember(open) { mutableStateOf(prefs.ttsVoice) }
    var sysVoice by remember(open) { mutableStateOf(prefs.ttsSystemVoice) }
    val context = LocalContext.current
    val sysVoices by produceState(emptyList<String>(), open, engine) { if (open && engine == "system") SystemSpeaker.russianVoices(context) { value = it } }
    var pause by remember(open) { mutableStateOf(prefs.ttsPause.takeIf { it >= 0 }) }
    var announce by remember(open) { mutableStateOf(prefs.ttsAnnounce) }
    ArSheet(open, onClose, "Озвучка на устройстве") {
        Text("Движок", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Silero", active = engine == "silero", onClick = { engine = "silero"; prefs.ttsEngine = "silero"; PlayerController.reloadTts() })
            Pill("Голос Android", active = engine == "system", onClick = { engine = "system"; prefs.ttsEngine = "system"; PlayerController.reloadTts() })
        }
        Spacer(Modifier.height(8.dp))
        Text("Голос", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (engine == "silero") {
                Pill("Как в главе", active = voice == null, onClick = { voice = null; prefs.ttsVoice = null; PlayerController.reloadTts() })
                for ((id, label) in VOICES) Pill(label, active = voice == id, onClick = { voice = id; prefs.ttsVoice = id; PlayerController.reloadTts() })
            } else {
                Pill("Авто", active = sysVoice == null, onClick = { sysVoice = null; prefs.ttsSystemVoice = null; PlayerController.reloadTts() })
                for (name in sysVoices) Pill(name, active = sysVoice == name, onClick = { sysVoice = name; prefs.ttsSystemVoice = name; PlayerController.reloadTts() })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Пауза между фразами", color = Ar.textMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Стандартная", active = pause == null, onClick = { pause = null; prefs.ttsPause = -1f; PlayerController.reloadTts() })
            for (p in PAUSES) Pill("$p с", active = pause == p, onClick = { pause = p; prefs.ttsPause = p; PlayerController.reloadTts() })
        }
        Spacer(Modifier.height(8.dp))
        ArToggle(
            announce ?: true, { v -> announce = v; prefs.ttsAnnounce = v; PlayerController.reloadTts() },
            "Объявлять главу", "Перед текстом прозвучит «Том 2 глава 5 - Название».",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}
