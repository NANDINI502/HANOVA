package com.nandini.hanova.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nandini.hanova.R
import com.nandini.hanova.lecture.CaptionLine
import com.nandini.hanova.lecture.LectureViewModel
import com.nandini.hanova.ui.components.CircleIconButton
import com.nandini.hanova.ui.components.HIcon
import com.nandini.hanova.ui.components.Hano
import com.nandini.hanova.ui.components.Mood
import com.nandini.hanova.ui.theme.H
import com.nandini.hanova.ui.theme.HType
import com.nandini.hanova.ui.theme.Nunito
import kotlinx.coroutines.launch

@Composable
fun LiveLectureScreen(
    course: String,
    onFinished: (lectureId: Long) -> Unit,
    onOpenHomework: () -> Unit,
    vm: LectureViewModel = viewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val voiceOn by vm.voiceOn.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    LaunchedEffect(course) { vm.start(course) }
    LaunchedEffect(s.lines.size, s.partial.isNotEmpty(), s.partialEn.isNotEmpty()) {
        val count = s.lines.size + 1
        if (count > 0) list.animateScrollToItem(count - 1)
    }
    val finish: () -> Unit = { scope.launch { onFinished(vm.stop()) } }
    BackHandler(onBack = finish)

    Column(Modifier.fillMaxSize().background(H.Ground)) {
        // Header
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClickLabel = "Stop and go back", onClick = finish),
                    contentAlignment = Alignment.Center) { HIcon(R.drawable.ic_back, description = "Back") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.course, style = HType.Headline, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (s.paused) H.Ink4 else H.Rec))
                        Text(Fmt.elapsed(s.elapsedMs) + if (s.paused) " · Paused" else " · Offline", style = HType.Sub)
                    }
                }
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClickLabel = if (voiceOn) "Mute English voice" else "Speak English in earbuds", onClick = vm::toggleVoice),
                    contentAlignment = Alignment.Center) {
                    HIcon(if (voiceOn) R.drawable.ic_volume else R.drawable.ic_volume_off,
                        tint = if (voiceOn) H.Teal else H.Ink4,
                        description = if (voiceOn) "English voice on" else "English voice off")
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(H.Hairline))
        }

        // Captions
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.Bottom),
        ) {
            if (s.lines.isEmpty() && s.partial.isEmpty()) {
                item {
                    Text(
                        "Hano is listening. Captions appear as the professor speaks." +
                            if (voiceOn && !vm.headsetConnected()) "\nConnect earbuds to hear them in English." else "",
                        style = HType.Callout,
                    )
                }
            }
            items(s.lines, key = { it.id }) { line ->
                val age = s.lines.size - 1 - s.lines.indexOf(line)
                CaptionBlock(line, alpha = when (age) { 0 -> 1f; 1 -> 0.85f; else -> 0.6f }, onOpenHomework)
            }
            item(key = "partial") {
                if (s.partial.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (s.partialEn.isNotEmpty()) Text(s.partialEn + "…", style = HType.CaptionEn.copy(color = H.Teal))
                        Text(s.partial + "…", style = HType.CaptionZh.copy(color = H.Teal, fontSize = 18.sp, lineHeight = 26.sp))
                        Text("Listening…", style = HType.Sub)
                    }
                }
            }
        }

        // Controls
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(H.Surface)
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Hano(if (s.paused) Mood.SLEEPY else Mood.LISTENING, 52.dp)
                LevelBars(if (s.paused) 0f else s.level, Modifier.weight(1f).height(36.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(R.drawable.ic_star, "Mark last sentence as important", onClick = vm::starLatest,
                    tint = if (s.lines.lastOrNull()?.starred == true) H.AmberInk else H.Ink)
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(38.dp)).background(H.Ink)
                        .clickable(role = Role.Button, onClickLabel = "Stop and save to Lecture Diary", onClick = finish),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(H.White)) }
                CircleIconButton(
                    if (s.paused) R.drawable.ic_mic else R.drawable.ic_pause,
                    if (s.paused) "Resume" else "Pause",
                    onClick = vm::togglePause,
                )
            }
        }
    }
}

@Composable
private fun CaptionBlock(line: CaptionLine, alpha: Float, onOpenHomework: () -> Unit) {
    Column(Modifier.alpha(alpha), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Fmt.elapsed(line.tMs), style = HType.Sub.copy(fontSize = 12.sp))
            if (line.starred) HIcon(R.drawable.ic_star, tint = H.AmberInk, size = 14.dp)
        }
        Text(
            if (line.translationPending) "(translation pending)" else line.en,
            style = if (line.translationPending) HType.Callout else HType.CaptionEn,
        )
        Text(line.zh, style = HType.CaptionZh)
        if (line.isHomework) {
            Row(
                Modifier.padding(top = 2.dp).clip(RoundedCornerShape(12.dp)).background(H.AmberBg)
                    .clickable(onClick = onOpenHomework).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                HIcon(R.drawable.ic_task, tint = H.AmberInk, size = 18.dp)
                Text("Added to Homework", color = H.AmberInk, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("· " + Fmt.due(line.homeworkDue?.toEpochDay()), color = H.AmberInk2, fontFamily = Nunito, fontSize = 14.sp)
            }
        }
    }
}

/** Simple animated-looking mic meter driven by the current level. */
@Composable
private fun LevelBars(level: Float, modifier: Modifier) {
    val shape = listOf(0.25f, 0.4f, 0.6f, 0.85f, 0.5f, 0.7f, 1f, 0.65f, 0.45f, 0.8f, 1f, 0.85f, 0.55f, 0.35f, 0.65f, 0.9f, 0.6f, 0.4f, 0.3f, 0.5f, 0.35f, 0.25f)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        shape.forEach { k ->
            val h = (k * level.coerceIn(0f, 1f)).coerceAtLeast(0.12f)
            Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(3.dp)).background(if (h > 0.7f) H.Teal else H.Mint))
        }
    }
}

