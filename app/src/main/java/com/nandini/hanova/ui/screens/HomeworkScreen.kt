package com.nandini.hanova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandini.hanova.R
import com.nandini.hanova.data.Homework
import com.nandini.hanova.ui.components.Card
import com.nandini.hanova.ui.components.Chip
import com.nandini.hanova.ui.components.HIcon
import com.nandini.hanova.ui.components.Hano
import com.nandini.hanova.ui.components.Mood
import com.nandini.hanova.ui.theme.H
import com.nandini.hanova.ui.theme.HType
import com.nandini.hanova.ui.theme.Nunito
import java.time.LocalDate

@Composable
fun HomeworkScreen(
    all: List<Homework>,
    courses: List<String>,
    onToggle: (Homework) -> Unit,
    onDelete: (Homework) -> Unit,
    onAdd: (task: String, course: String, dueEpochDay: Long?) -> Unit,
    onOpenLecture: (Long) -> Unit,
) {
    var showDone by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val todo = all.filter { !it.done }
    val done = all.filter { it.done }
    val shown = if (showDone) done else todo

    LazyColumn(
        Modifier.fillMaxSize().background(H.Ground).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Homework", style = HType.LargeTitle, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(H.Surface)
                        .clickable(role = Role.Button, onClickLabel = "Add homework") { adding = true },
                    contentAlignment = Alignment.Center,
                ) { HIcon(R.drawable.ic_plus, description = "Add homework") }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(H.Surface2).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Segment("To do · ${todo.size}", !showDone, Modifier.weight(1f)) { showDone = false }
                Segment("Done · ${done.size}", showDone, Modifier.weight(1f)) { showDone = true }
            }
        }
        item {
            Card(bg = H.TealSoft) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Hano(if (todo.isEmpty()) Mood.HAPPY else Mood.READING, 56.dp)
                    Text(
                        when {
                            todo.isEmpty() -> "All done! Hano is proud of you."
                            todo.size == 1 -> "1 task left. Hano caught it from your lectures."
                            else -> "${todo.size} tasks left. Hano caught them from your lectures."
                        },
                        style = HType.Callout.copy(color = H.TealDeep),
                    )
                }
            }
        }
        items(shown, key = { it.id }) { hw ->
            SwipeToDelete(onDelete = { onDelete(hw) }) {
                TaskCard(hw, onToggle = { onToggle(hw) }, onOpenLecture = onOpenLecture)
            }
        }
        if (shown.isNotEmpty()) {
            item { Text("Swipe left on a task to delete it.", style = HType.Sub, modifier = Modifier.padding(top = 4.dp)) }
        }
    }

    if (adding) {
        AddHomeworkDialog(
            courses = courses,
            onDismiss = { adding = false },
            onAdd = { t, c, d -> adding = false; onAdd(t, c, d) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(H.Rec).padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("Delete", color = H.White, fontFamily = Nunito, fontWeight = FontWeight.SemiBold) }
        },
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddHomeworkDialog(courses: List<String>, onDismiss: () -> Unit, onAdd: (String, String, Long?) -> Unit) {
    var task by remember { mutableStateOf("") }
    var course by remember { mutableStateOf(courses.firstOrNull() ?: "") }
    var dueIdx by remember { mutableStateOf(0) }
    val today = LocalDate.now()
    val dueOptions = listOf(
        "No date" to null,
        "Tomorrow" to today.plusDays(1),
        "In 3 days" to today.plusDays(3),
        "Next week" to today.plusWeeks(1),
    )
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = H.Teal, focusedLabelColor = H.Teal, cursorColor = H.Teal)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = H.Ground,
        title = { Text("Add homework", style = HType.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(task, { task = it }, label = { Text("Task") }, colors = fieldColors)
                OutlinedTextField(course, { course = it }, label = { Text("Course") }, singleLine = true, colors = fieldColors)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dueOptions.forEachIndexed { i, (label, _) ->
                        val on = i == dueIdx
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) H.Ink else H.Surface)
                                .clickable { dueIdx = i }.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(label, style = HType.Sub.copy(color = if (on) H.White else H.Ink)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (task.isNotBlank()) onAdd(task.trim(), course.trim().ifBlank { "General" }, dueOptions[dueIdx].second?.toEpochDay())
            }) { Text("Add", color = H.Teal, fontFamily = Nunito, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = H.Ink2, fontFamily = Nunito) } },
    )
}

@Composable
private fun Segment(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val m = if (active) modifier.shadow(2.dp, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).background(H.Ground)
    else modifier.clip(RoundedCornerShape(9.dp))
    Box(m.height(36.dp).clickable(role = Role.Tab, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (active) H.Ink else H.Ink2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(hw: Homework, onToggle: () -> Unit, onOpenLecture: (Long) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(
                checked = hw.done, onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = H.Teal, uncheckedColor = H.Ink2),
            )
            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    hw.en, style = HType.Body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (hw.done) H.Ink2 else H.Ink,
                        textDecoration = if (hw.done) TextDecoration.LineThrough else null,
                    ),
                )
                if (hw.zh.isNotBlank() && hw.zh != hw.en) Text(hw.zh, style = HType.CaptionZh.copy(fontSize = 14.sp))
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(hw.course)
                    when {
                        hw.done -> Chip("Done", bg = H.TealSoft, fg = H.Teal)
                        Fmt.isUrgent(hw.dueEpochDay) -> Chip(Fmt.due(hw.dueEpochDay), bg = H.AmberBg, fg = H.AmberInk)
                        else -> Chip(Fmt.due(hw.dueEpochDay))
                    }
                    if (hw.lectureId != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenLecture(hw.lectureId) }
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            HIcon(R.drawable.ic_play, tint = H.Teal, size = 12.dp)
                            Text("${Fmt.shortDay(Fmt.date(hw.createdAt))} · ${Fmt.elapsed(hw.tMs)}",
                                color = H.Teal, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

