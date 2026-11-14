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

val AccentMint = Color(0xFF34D399)
val AccentCyan = Color(0xFF38BDF8)
val AccentAmber = Color(0xFFFBBF24)
val AccentRed = Color(0xFFF43F5E)

/** Reference lap in a comparison (charts and track map); also muted text. */
val GhostSlate = Color(0xFF94A3B8)
val CrispWhite = Color(0xFFF8FAFC)
val Ink = Color(0xFF0D1114)
val Panel = Color(0xFF12181C)
val PanelRaised = Color(0xFF1A2226)

/** Library feed hairline: visible on Ink without reading as a border. */
val Hairline = Color(0xFF2C3944)

/** Pressed lap row, one step above the background. */
val RowPressed = Color(0xFF12191E)

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
    primary = AccentMint,
    onPrimary = Color(0xFF04130D),
    primaryContainer = Color(0xFF0B3B2C),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = AccentCyan,
    onSecondary = Color(0xFF04131C),
    secondaryContainer = Color(0xFF082F49),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AccentAmber,
    onTertiary = Color(0xFF241800),
    background = Ink,
    onBackground = CrispWhite,
    surface = Panel,
    onSurface = CrispWhite,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = GhostSlate,
    outline = Color(0xFF2B3540),
    outlineVariant = Color(0xFF1E262B),
    error = AccentRed,
    onError = Color(0xFF240505),
    errorContainer = Color(0xFF3B0E1B),
    onErrorContainer = Color(0xFFFFD7E0),
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
