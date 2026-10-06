package com.nandini.hanova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.nandini.hanova.R
import com.nandini.hanova.data.Lecture
import com.nandini.hanova.data.LectureSummary
import com.nandini.hanova.data.Line
import com.nandini.hanova.ui.components.Card
import com.nandini.hanova.ui.components.Chip
import com.nandini.hanova.ui.components.CourseMonogram
import com.nandini.hanova.ui.components.HIcon
import com.nandini.hanova.ui.components.Hano
import com.nandini.hanova.ui.components.Mood
import com.nandini.hanova.ui.theme.H
import com.nandini.hanova.ui.theme.HType
import com.nandini.hanova.ui.theme.Nunito
import androidx.compose.ui.text.font.FontWeight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun LectureDiaryScreen(lectures: List<LectureSummary>, onOpen: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }

    val filtered = lectures.filter { s ->
        (pickedDay == null || Fmt.date(s.lecture.startedAt) == pickedDay) &&
            (query.isBlank() || s.lecture.course.contains(query, true) || (s.firstLine ?: "").contains(query, true))
    }
    val byDay = filtered.groupBy { Fmt.date(it.lecture.startedAt) }
    val lectureDays = lectures.map { Fmt.date(it.lecture.startedAt) }.toSet()

    LazyColumn(
        Modifier.fillMaxSize().background(H.Ground).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Lecture Diary", style = HType.LargeTitle) }
        item {
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(H.Surface).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HIcon(R.drawable.ic_search, tint = H.Ink2, size = 18.dp)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search words, courses", style = HType.Body.copy(color = H.Ink2))
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = HType.Body,
                        cursorBrush = SolidColor(H.Teal), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item { WeekStrip(picked = pickedDay, hasLecture = lectureDays) { pickedDay = if (pickedDay == it) null else it } }

        if (filtered.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hano(Mood.SLEEPY, 120.dp)
                    Text(if (lectures.isEmpty()) "No lectures yet." else "Nothing here.", style = HType.Title)
                    Text("Start a lecture from Home and it lands here.", style = HType.Callout)
                }
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "h$day") { Text(Fmt.dayHeader(day).uppercase(), style = HType.Overline) }
            items(list, key = { it.lecture.id }) { s -> DiaryRow(s) { onOpen(s.lecture.id) } }
        }
    }
}

@Composable
private fun WeekStrip(picked: LocalDate?, hasLecture: Set<LocalDate>, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong())
            val selected = d == (picked ?: today)
            Column(
                Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (selected) H.Ink else H.Ground).clickable { onPick(d) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Text(d.dayOfWeek.name.take(1), style = HType.Sub.copy(fontSize = 12.sp, color = if (selected) H.White else H.Ink2))
                Text("${d.dayOfMonth}", style = HType.Headline.copy(color = if (selected) H.White else H.Ink))
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(
                    when { d !in hasLecture -> H.Ground.copy(alpha = 0f); selected -> H.Mint; else -> H.Teal }
                ))
            }
        }
    }
}

@Composable
private fun DiaryRow(s: LectureSummary, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CourseMonogram(s.lecture.course)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(s.lecture.course, style = HType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.clock(s.lecture.startedAt), style = HType.Sub)
                }
                if (!s.firstLine.isNullOrBlank()) {
                    Text(s.firstLine, style = HType.Callout.copy(fontSize = 14.sp, color = H.Ink3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Chip(Fmt.duration(s.lecture.durationMs))
                    if (s.hwCount > 0) Chip("${s.hwCount} homework", bg = H.AmberBg, fg = H.AmberInk)
                    if (s.starCount > 0) Chip("${s.starCount} starred")
                }
            }
        }
    }
}

/** A saved lecture: full bilingual transcript. */
@Composable
fun LectureDetailScreen(lecture: Lecture?, lines: List<Line>, onBack: () -> Unit) {
    val context = LocalContext.current
    val share: () -> Unit = {
        if (lecture != null) {
            val text = buildString {
                appendLine("${lecture.course} — ${Fmt.dayHeader(Fmt.date(lecture.startedAt))} (${Fmt.duration(lecture.durationMs)})")
                appendLine()
                lines.forEach { l ->
                    appendLine("[${Fmt.elapsed(l.tMs)}]${if (l.starred) " ★" else ""} ${l.en}")
                    appendLine(l.zh)
                    appendLine()
                }
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${lecture.course} transcript")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Share transcript"))
        }
    }
    Column(Modifier.fillMaxSize().background(H.Ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                HIcon(R.drawable.ic_back, description = "Back")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(lecture?.course ?: "", style = HType.Headline)
                if (lecture != null) {
                    Text("${Fmt.dayHeader(Fmt.date(lecture.startedAt))} · ${Fmt.duration(lecture.durationMs)}", style = HType.Sub)
                }
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClickLabel = "Share transcript", onClick = share),
                contentAlignment = Alignment.Center,
            ) { HIcon(R.drawable.ic_share, description = "Share transcript") }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(H.Hairline))
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(lines, key = { it.id }) { l ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.elapsed(l.tMs), style = HType.Sub.copy(fontSize = 12.sp))
                        if (l.starred) HIcon(R.drawable.ic_star, tint = H.AmberInk, size = 14.dp)
                    }
                    Text(if (l.translationPending) "(translation pending)" else l.en,
                        style = HType.CaptionEn.copy(fontSize = 18.sp, lineHeight = 25.sp, fontFamily = Nunito))
                    Text(l.zh, style = HType.CaptionZh)
                }
            }
        }
    }
}
