package io.github.jlai403.lapcraft.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jlai403.lapcraft.R

val AccentGreen = Color(0xFF3DDC84)
val AccentBlue = Color(0xFF5AC8FA)
val AccentAmber = Color(0xFFFFC857)

/** Reference lap in a comparison (charts and track map). */
val AccentYellow = Color(0xFFFFD54F)
val Ink = Color(0xFF080C11)
val Panel = Color(0xFF111821)
val PanelRaised = Color(0xFF1A2431)
val MutedText = Color(0xFF93A4B5)

/** Library feed hairline: visible on Ink without reading as a border. */
val Hairline = Color(0xFF33414F)

/** Pressed lap row, one step above the background. */
val RowPressed = Color(0xFF10171F)

/** Geist Sans for interface text. OFL-1.1; see licenses/Geist-OFL.txt. */
val GeistSansFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
    Font(R.font.geist_black, FontWeight.Black),
)

/** Geist Mono for lap times and telemetry values, so digits do not jitter. */
val GeistMonoFamily = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    Font(R.font.geist_mono_semibold, FontWeight.SemiBold),
)

private val SportColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF03130A),
    primaryContainer = Color(0xFF123A24),
    onPrimaryContainer = Color(0xFFB9F6D3),
    secondary = AccentBlue,
    onSecondary = Color(0xFF00131C),
    secondaryContainer = Color(0xFF102B36),
    onSecondaryContainer = Color(0xFFC3ECFA),
    tertiary = AccentAmber,
    onTertiary = Color(0xFF241800),
    background = Ink,
    onBackground = Color(0xFFF4F7FA),
    surface = Panel,
    onSurface = Color(0xFFEAF0F5),
    surfaceVariant = PanelRaised,
    onSurfaceVariant = MutedText,
    outline = Color(0xFF2B3846),
    outlineVariant = Color(0xFF1F2A35),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF240505),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFD5D5),
)

private val SportShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val SportTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.Black,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun LapCraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SportColors,
        shapes = SportShapes,
        typography = SportTypography,
        content = content,
    )
}
