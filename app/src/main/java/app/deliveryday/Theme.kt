package app.deliveryday

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** App colors; red and amber are kept for what needs attention. */
data class Palette(
    val bg: Color, val card: Color, val cardHigh: Color, val line: Color,
    val text: Color, val muted: Color,
    val red: Color, val green: Color, val amber: Color,
    val heroTop: Color, val heroBottom: Color, val welcomeTop: Color,
)

private val Dark = Palette(
    bg = Color(0xFF0E0F11), card = Color(0xFF1A1C20), cardHigh = Color(0xFF24272C), line = Color(0xFF33373D),
    text = Color(0xFFF2F3F5), muted = Color(0xFF9AA0A8),
    red = Color(0xFFE82127), green = Color(0xFF3DDC84), amber = Color(0xFFFFB547),
    heroTop = Color(0xFF2A2D33), heroBottom = Color(0xFF15171A), welcomeTop = Color(0xFF3A0D10),
)

private val Light = Palette(
    bg = Color(0xFFF3F4F6), card = Color(0xFFFFFFFF), cardHigh = Color(0xFFECEEF1), line = Color(0xFFDDE0E5),
    text = Color(0xFF16181C), muted = Color(0xFF656B74),
    red = Color(0xFFD81F26), green = Color(0xFF1B8F4E), amber = Color(0xFFB86E00),
    heroTop = Color(0xFFFFFFFF), heroBottom = Color(0xFFF0F1F4), welcomeTop = Color(0xFFFBE3E4),
)

/**
 * Simple access to the current theme's colors (including from non-@Composable drawing code).
 * The palette is chosen by [TeslaTheme]; the activity is recreated when the phone's theme changes.
 */
object TeslaColors {
    var palette: Palette = Dark
    val Bg get() = palette.bg
    val Card get() = palette.card
    val CardHigh get() = palette.cardHigh
    val Line get() = palette.line
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Red get() = palette.red
    val Green get() = palette.green
    val Amber get() = palette.amber
}

/** Follows the phone's light/dark theme. */
@Composable
fun TeslaTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    TeslaColors.palette = p
    val scheme = if (p == Dark) darkColorScheme(
        primary = p.red, onPrimary = Color.White, background = p.bg, onBackground = p.text,
        surface = p.bg, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.muted, outline = p.line,
        surfaceContainer = p.card,
    ) else lightColorScheme(
        primary = p.red, onPrimary = Color.White, background = p.bg, onBackground = p.text,
        surface = p.bg, onSurface = p.text, surfaceVariant = p.card, onSurfaceVariant = p.muted, outline = p.line,
        surfaceContainer = p.card,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
