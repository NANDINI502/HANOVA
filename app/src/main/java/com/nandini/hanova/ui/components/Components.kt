package com.nandini.hanova.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandini.hanova.R
import com.nandini.hanova.ui.theme.H
import com.nandini.hanova.ui.theme.HType
import com.nandini.hanova.ui.theme.Nunito

enum class Mood(@DrawableRes val res: Int) {
    HAPPY(R.drawable.hano_happy),
    READING(R.drawable.hano_reading),
    LISTENING(R.drawable.hano_listening),
    SLEEPY(R.drawable.hano_sleepy),
}

/** Hano, the Hanova dragon. */
@Composable
fun Hano(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(mood.res),
        contentDescription = "Hano the dragon",
        modifier = modifier.size(size),
    )
}

@Composable
fun HIcon(@DrawableRes res: Int, tint: Color = H.Ink, size: Dp = 24.dp, description: String? = null) {
    Icon(painterResource(res), contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

/** Small rounded label, e.g. "1 h 48 m" or "Due Wed, 14 Oct". */
@Composable
fun Chip(text: String, bg: Color = H.Ground, fg: Color = H.Ink3) {
    Text(
        text, style = HType.Caption.copy(color = fg),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Rounded-square course letter. */
@Composable
fun CourseMonogram(course: String) {
    val (bg, fg) = H.courseTint(course)
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(course.trim().take(1).uppercase(), color = fg, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/** Big pill button (black-plum by default). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    bg: Color = H.Ink,
    fg: Color = H.White,
) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(bg)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) HIcon(icon, tint = fg, size = 22.dp)
        Text(text, color = fg, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

/** Round icon button, 56dp white circle by default. */
@Composable
fun CircleIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    bg: Color = H.Ground,
    tint: Color = H.Ink,
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(bg)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { HIcon(icon, tint = tint, description = description) }
}

enum class Tab(val route: String, val label: String, @DrawableRes val icon: Int) {
    HOME("home", "Home", R.drawable.ic_home),
    DIARY("diary", "Diary", R.drawable.ic_book),
    HOMEWORK("homework", "Homework", R.drawable.ic_task),
    TALK("talk", "Talk", R.drawable.ic_talk),
}

@Composable
fun TabBar(current: String?, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(H.Ground)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(H.Hairline))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Tab.entries.forEach { tab ->
                val active = tab.route == current
                val color = if (active) H.Teal else H.Ink2
                Column(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    HIcon(tab.icon, tint = color)
                    Text(tab.label, color = color, fontFamily = Nunito, fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

/** Soft card container. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    bg: Color = H.Surface,
    radius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var m = modifier.clip(RoundedCornerShape(radius)).background(bg)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m) { content() }
}

@Suppress("unused")
fun Modifier.hairlineBorder(radius: Dp) = this.border(1.dp, H.Hairline, RoundedCornerShape(radius))
