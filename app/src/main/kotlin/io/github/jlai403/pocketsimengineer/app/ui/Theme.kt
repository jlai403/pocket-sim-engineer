package io.github.jlai403.pocketsimengineer.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AccentGreen = Color(0xFF3DDC84)
val AccentBlue = Color(0xFF5AC8FA)
val AccentAmber = Color(0xFFFFC857)
val Ink = Color(0xFF080C11)
val Panel = Color(0xFF111821)
val PanelRaised = Color(0xFF1A2431)
val MutedText = Color(0xFF93A4B5)

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
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 25.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun PocketSimEngineerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SportColors,
        shapes = SportShapes,
        typography = SportTypography,
        content = content,
    )
}
