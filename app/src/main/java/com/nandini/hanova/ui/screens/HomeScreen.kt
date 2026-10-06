package com.nandini.hanova.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandini.hanova.AppContainer
import com.nandini.hanova.R
import com.nandini.hanova.data.LectureSummary
import com.nandini.hanova.ui.components.Card
import com.nandini.hanova.ui.components.CircleIconButton
import com.nandini.hanova.ui.components.CourseMonogram
import com.nandini.hanova.ui.components.HIcon
import com.nandini.hanova.ui.components.Hano
import com.nandini.hanova.ui.components.Mood
import com.nandini.hanova.ui.components.PillButton
import com.nandini.hanova.ui.theme.H
import com.nandini.hanova.ui.theme.HType
import com.nandini.hanova.ui.theme.Nunito

@Composable
fun HomeScreen(
    userName: String,
    status: AppContainer.Status,
    openHomework: Int,
    recent: List<LectureSummary>,
    recentCourses: List<String>,
    onStartLecture: (course: String) -> Unit,
    onOpenTalk: () -> Unit,
    onOpenHomework: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenLecture: (Long) -> Unit,
    voiceProblem: String? = null,
    onOpenVoice: () -> Unit = {},
    onFixVoice: () -> Unit = {},
) {
    var askCourse by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(H.Ground).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.todayLong(), style = HType.Callout.copy(fontWeight = FontWeight.Medium))
                Text("Hi, $userName", style = HType.LargeTitle)
            }
            CircleIconButton(R.drawable.ic_settings, "Voice settings", onClick = onOpenVoice, size = 44.dp, bg = H.Surface)
        }

        // Hero
        Card(radius = 28.dp) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ready = status is AppContainer.Status.Ready
                Hano(if (ready) Mood.HAPPY else Mood.SLEEPY, 104.dp)
                Text(
                    when (status) {
                        is AppContainer.Status.Ready -> "Ready when class is."
                        is AppContainer.Status.Loading -> "Hano is waking up…"
                        is AppContainer.Status.Failed -> "Hano couldn't wake up."
                    },
                    style = HType.Title, textAlign = TextAlign.Center,
                )
                Text(
                    when (status) {
                        is AppContainer.Status.Failed -> status.message
                        else -> "Hano listens offline and writes it all down."
                    },
                    style = HType.Callout, textAlign = TextAlign.Center,
                )
                if (voiceProblem != null && ready) {
                    Text(
                        "$voiceProblem · tap to download (one time)",
                        style = HType.Callout.copy(color = H.AmberInk), textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(onClick = onFixVoice),
                    )
                }
                PillButton(
                    "Start lecture", onClick = { if (ready) askCourse = true },
                    icon = R.drawable.ic_mic, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    bg = if (ready) H.Ink else H.Ink4,
                )
            }
        }

        // Tiles
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f).height(96.dp), radius = 22.dp, onClick = onOpenTalk) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    HIcon(R.drawable.ic_talk, tint = H.Teal)
                    Column {
                        Text("Conversation", style = HType.Body.copy(fontWeight = FontWeight.SemiBold))
                        Text("Talk face to face", style = HType.Sub)
                    }
                }
            }
            Card(Modifier.weight(1f).height(96.dp), bg = H.AmberBg, radius = 22.dp, onClick = onOpenHomework) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("$openHomework", color = H.AmberInk, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    Column {
                        Text("Homework", style = HType.Body.copy(fontWeight = FontWeight.SemiBold))
                        Text(if (openHomework == 1) "Task to do" else "Tasks to do", style = HType.Sub.copy(color = H.AmberInk2))
                    }
                }
            }
        }

        // Recent lectures
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent lectures", style = HType.Section, modifier = Modifier.weight(1f))
                Text("See all", color = H.Teal, fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenDiary).padding(8.dp))
            }
            if (recent.isEmpty()) {
                Text("Your lectures will appear here.", style = HType.Callout, modifier = Modifier.padding(vertical = 12.dp))
            }
            recent.take(3).forEach { s -> RecentRow(s) { onOpenLecture(s.lecture.id) } }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (askCourse) {
        CourseDialog(
            suggestions = recentCourses,
            onDismiss = { askCourse = false },
            onStart = { askCourse = false; onStartLecture(it) },
        )
    }
}

@Composable
private fun RecentRow(s: LectureSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CourseMonogram(s.lecture.course)
        Column(Modifier.weight(1f)) {
            Text(s.lecture.course, style = HType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val hw = if (s.hwCount > 0) " · ${s.hwCount} homework" else ""
            Text("${Fmt.dayHeader(Fmt.date(s.lecture.startedAt))} · ${Fmt.duration(s.lecture.durationMs)}$hw", style = HType.Sub)
        }
        HIcon(R.drawable.ic_chevron, tint = H.Ink4, size = 18.dp)
    }
}

@Composable
private fun CourseDialog(suggestions: List<String>, onDismiss: () -> Unit, onStart: (String) -> Unit) {
    var name by remember { mutableStateOf(suggestions.firstOrNull() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = H.Ground,
        title = { Text("Which class?", style = HType.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Course name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (name.isNotBlank()) onStart(name.trim()) }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = H.Teal, focusedLabelColor = H.Teal, cursorColor = H.Teal),
                )
                if (suggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { c ->
                            Box(
                                Modifier.clip(RoundedCornerShape(16.dp)).background(H.Surface)
                                    .clickable { name = c }.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text(c, style = HType.Sub.copy(color = H.Ink)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onStart(name.trim()) }) {
                Text("Start", color = H.Teal, fontFamily = Nunito, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = H.Ink2, fontFamily = Nunito) }
        },
    )
}
