package com.nandini.hanova.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nandini.hanova.R
import com.nandini.hanova.VoiceOut
import com.nandini.hanova.ui.components.Card
import com.nandini.hanova.ui.components.HIcon
import com.nandini.hanova.ui.theme.H
import com.nandini.hanova.ui.theme.HType

/**
 * Pick the voices by ear. Tapping a row selects it and plays a sample.
 * Only offline voices are listed, so whatever is picked works in airplane mode.
 */
@Composable
fun VoiceSettingsScreen(voice: VoiceOut, onBack: () -> Unit) {
    var en by remember { mutableStateOf(voice.enVoice) }
    var zh by remember { mutableStateOf(voice.zhVoice) }
    var rate by remember { mutableFloatStateOf(voice.rate) }
    val enChoices = remember { voice.choices(VoiceOut.Lang.EN) }
    val zhChoices = remember { voice.choices(VoiceOut.Lang.ZH) }
    val leave = { voice.clear(); onBack() }
    BackHandler(onBack = leave)

    Column(Modifier.fillMaxSize().background(H.Ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClickLabel = "Back", onClick = leave),
                contentAlignment = Alignment.Center) { HIcon(R.drawable.ic_back, description = "Back") }
            Text("Voice", style = HType.Headline, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tap a voice to hear it. All of these work offline.", style = HType.Callout)

            Text("Speed", style = HType.Section, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Slow" to 0.9f, "Natural" to 1.0f, "Fast" to 1.2f).forEach { (label, value) ->
                    val on = rate == value
                    Text(
                        label,
                        style = HType.Body.copy(color = if (on) H.White else H.Ink),
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) H.Ink else H.Surface)
                            .clickable(role = Role.RadioButton) {
                                rate = value; voice.rate = value
                                en?.let { voice.preview(VoiceOut.Lang.EN, it) }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }

            VoiceList("English voice · what you hear", enChoices, en) {
                en = it; voice.enVoice = it; voice.preview(VoiceOut.Lang.EN, it)
            }
            VoiceList("Chinese voice · what they hear", zhChoices, zh) {
                zh = it; voice.zhVoice = it; voice.preview(VoiceOut.Lang.ZH, it)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VoiceList(title: String, choices: List<VoiceOut.Choice>, selected: String?, onPick: (String) -> Unit) {
    Text(title, style = HType.Section, modifier = Modifier.padding(top = 12.dp))
    if (choices.isEmpty()) {
        Text("No offline voices found yet. Hano is still waking up, or the voice needs downloading.", style = HType.Callout)
        return
    }
    Card(radius = 20.dp) {
        Column {
            choices.forEachIndexed { i, ch ->
                val on = ch.name == selected
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClickLabel = "Use and play ${ch.label}") { onPick(ch.name) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(11.dp))
                            .border(2.dp, if (on) H.Teal else H.Ink4, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(H.Teal)) }
                    Text(ch.label, style = HType.Body, modifier = Modifier.weight(1f))
                    HIcon(R.drawable.ic_play, tint = if (on) H.Teal else H.Ink4, size = 20.dp, description = null)
                }
                if (i < choices.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 52.dp).background(H.Hairline))
            }
        }
    }
}
