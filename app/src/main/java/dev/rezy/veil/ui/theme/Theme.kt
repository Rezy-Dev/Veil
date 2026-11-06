package dev.rezy.veil.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rezy.veil.R

/* ---------------------------------------------------------------------------
 *  Nothing-inspired palette: pure black, paper white, one ember red.
 * ------------------------------------------------------------------------- */

val Ink = Color(0xFF000000)
val Paper = Color(0xFFFFFFFF)
val Ember = Color(0xFFD71921)
val Ash = Color(0xFF8A8A8A)
val Slate = Color(0xFF4A4A4A)
val Line = Color(0xFF232323)

/** Dot-matrix display face (Handjet, OFL). */
val Handjet = FontFamily(Font(R.font.handjet, weight = FontWeight.Normal))

/** Blocky pixel face for small technical labels (Silkscreen, OFL). */
val Silk = FontFamily(Font(R.font.silkscreen, weight = FontWeight.Normal))

private val scheme = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    secondary = Ember,
    onSecondary = Paper,
    tertiary = Ash,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Ink,
    onSurfaceVariant = Ash,
    outline = Line,
    error = Ember,
    onError = Paper
)

@Composable
fun VeilTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Display style helper for the dot-matrix face. */
fun dotStyle(
    size: Int,
    color: Color = Paper,
    spacing: Int = 0,
    weight: FontWeight = FontWeight.Normal
) = TextStyle(
    fontFamily = Handjet,
    fontSize = size.sp,
    lineHeight = (size * 1.02f).sp,
    letterSpacing = spacing.sp,
    fontWeight = weight,
    color = color
)

/**
 * The faint dot grid that runs under everything. Drawn once, never animated,
 * so it costs nothing on recomposition.
 */
fun Modifier.dotGrid(
    step: Dp = 16.dp,
    radius: Dp = 0.8.dp,
    alpha: Float = 0.055f
): Modifier = drawBehind {
    val s = step.toPx()
    val r = radius.toPx()
    if (s <= 0f) return@drawBehind
    var y = s
    while (y < size.height) {
        var x = s
        while (x < size.width) {
            drawCircle(Paper.copy(alpha = alpha), r, Offset(x, y))
            x += s
        }
        y += s
    }
}
