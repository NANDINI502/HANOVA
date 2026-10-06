package com.nandini.hanova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandini.hanova.R
import com.nandini.hanova.conversation.ConversationViewModel
import com.nandini.hanova.conversation.Speaker
import com.nandini.hanova.ui.components.HIcon
import com.nandini.hanova.ui.components.Hano
import com.nandini.hanova.ui.components.Mood
import com.nandini.hanova.ui.theme.H
import com.nandini.hanova.ui.theme.HType
import com.nandini.hanova.ui.theme.Nunito

/**
 * Split screen for talking face to face. The top half is upside down so the
 * person across the table can read it; the bottom half is yours.
 */
@Composable
fun ConversationScreen(onClose: () -> Unit, vm: ConversationViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(H.Ground)) {
        // THEIR side (rotated 180°)
        // Drawn upside down, so the status-bar gap goes on the layout's BOTTOM edge.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(Modifier.weight(1f).fillMaxWidth().background(H.Surface).rotate(180f)) {
            Column(
                Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 24.dp + statusTop),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("給你的翻譯 · 中文", style = HType.Sub)
                    Text(
                        when {
                            s.listening == Speaker.THEM -> s.partial.ifEmpty { "請說話⋯" }
                            s.forThemZh.isNotEmpty() -> s.forThemZh
                            else -> "按住下面的按鈕說中文。"
                        },
                        style = HType.CaptionZh.copy(fontSize = 28.sp, lineHeight = 38.sp, color = H.Ink),
                    )
                    if (s.forThemEn.isNotEmpty()) Text(s.forThemEn, style = HType.Callout)
                }
                HoldToTalk(
                    label = if (s.listening == Speaker.THEM) "正在聽⋯" else "按住說話",
                    bg = H.Teal, active = s.listening == Speaker.THEM, enabled = !s.busy,
                    onDown = { vm.pressStart(Speaker.THEM) }, onUp = { vm.pressEnd(Speaker.THEM) },
                )
            }
        }

        // Divider with Hano and close
        Box(Modifier.fillMaxWidth().height(0.dp).zIndex(1f)) {
            Box(
                Modifier.align(Alignment.Center).offset(y = (-34).dp).size(68.dp).shadow(6.dp, RoundedCornerShape(34.dp))
                    .clip(RoundedCornerShape(34.dp)).background(H.Ground),
                contentAlignment = Alignment.Center,
            ) { Hano(if (s.busy) Mood.READING else Mood.LISTENING, 56.dp) }
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 16.dp).offset(y = (-22).dp).size(44.dp)
                    .shadow(4.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(H.Ground)
                    .clickable(onClickLabel = "End conversation", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { HIcon(R.drawable.ic_close, size = 20.dp, description = "End conversation") }
        }

        // MY side
        Column(
            Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 44.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("They said · English", style = HType.Sub)
                Text(
                    when {
                        s.listening == Speaker.ME -> s.partial.ifEmpty { "Speak now…" }
                        s.busy -> "Hano is translating…"
                        s.forMeEn.isNotEmpty() -> s.forMeEn
                        else -> "Hand the phone across. They hold the top button to speak Chinese."
                    },
                    style = HType.CaptionEn.copy(fontSize = 26.sp, lineHeight = 34.sp),
                )
                if (s.forMeZh.isNotEmpty()) Text(s.forMeZh, style = HType.CaptionZh)
            }
            HoldToTalk(
                label = if (s.listening == Speaker.ME) "Listening…" else "Hold to speak",
                bg = H.Ink, active = s.listening == Speaker.ME, enabled = !s.busy,
                onDown = { vm.pressStart(Speaker.ME) }, onUp = { vm.pressEnd(Speaker.ME) },
            )
        }
    }
}

@Composable
private fun HoldToTalk(label: String, bg: Color, active: Boolean, enabled: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (!enabled) H.Ink4 else if (active) H.Rec else bg)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    onDown()
                    tryAwaitRelease()
                    onUp()
                })
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(R.drawable.ic_mic, tint = H.White, size = 22.dp)
        Text(label, color = H.White, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

