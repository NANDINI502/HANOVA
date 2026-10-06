package com.nandini.hanova.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nandini.hanova.R

/** Hanova palette — warm paper, plum ink, teal accent (matches the design canvas). */
object H {
    val Ground = Color(0xFFFBFAF3)      // app background
    val Surface = Color(0xFFF2EEE2)     // cards
    val Surface2 = Color(0xFFEAE5D6)    // segmented track
    val Hairline = Color(0xFFE6E0CF)
    val Ink = Color(0xFF3E2F38)         // text + primary buttons
    val Ink2 = Color(0xFF6E6268)        // secondary text
    val Ink3 = Color(0xFF4E4249)
    val Ink4 = Color(0xFFA89CA2)        // chevrons
    val Teal = Color(0xFF0F7A64)        // accent, live partial text
    val TealSoft = Color(0xFFDDF1EA)
    val TealDeep = Color(0xFF0B5E4D)
    val Mint = Color(0xFF9ED9C6)
    val AmberBg = Color(0xFFF6E6D3)     // homework
    val AmberInk = Color(0xFF7A4519)
    val AmberInk2 = Color(0xFF6E4420)
    val Rec = Color(0xFFD93A2B)
    val White = Color(0xFFFFFFFF)

    /** Course monogram tints, picked by course name. */
    private val tints = listOf(
        TealSoft to Teal,
        Color(0xFFE6EEFB) to Color(0xFF1F5FBF),
        Color(0xFFFBE9EC) to Color(0xFFB2324B),
        AmberBg to AmberInk,
    )
    fun courseTint(course: String): Pair<Color, Color> = tints[Math.floorMod(course.hashCode(), tints.size)]
}

@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
val Nunito = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800))

object HType {
    val LargeTitle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp, color = H.Ink)
    val Title = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = H.Ink)
    val Section = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = H.Ink)
    val Headline = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = H.Ink)
    val Body = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = H.Ink)
    val Callout = TextStyle(fontFamily = Nunito, fontSize = 15.sp, lineHeight = 21.sp, color = H.Ink2)
    val Sub = TextStyle(fontFamily = Nunito, fontSize = 13.sp, color = H.Ink2)
    val Caption = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = H.Ink3)
    val Overline = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.5.sp, color = H.Ink2)
    val CaptionEn = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 27.sp, color = H.Ink)
    // Chinese uses the system CJK font (Noto Sans CJK on most Android phones)
    val CaptionZh = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = H.Ink2)
}

@Composable
fun HanovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = H.Teal, onPrimary = H.White,
            background = H.Ground, onBackground = H.Ink,
            surface = H.Ground, onSurface = H.Ink,
            surfaceVariant = H.Surface, onSurfaceVariant = H.Ink2,
        ),
        content = content,
    )
}
