package com.gibilator.gmg.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand palette.
val Ember = Color(0xFFFF6D00)
val EmberDim = Color(0xFFB34D00)
val Amber = Color(0xFFFFB300)
val ProbeBlue = Color(0xFF2196F3)
val ProbeBlueDim = Color(0xFF90CAF9)
val Charcoal = Color(0xFF14110F)
val CharcoalRaised = Color(0xFF221C18)
val CharcoalCard = Color(0xFF2B2420)
val Bone = Color(0xFFECE3DD)
val Muted = Color(0xFF9A8F87)
val GoodGreen = Color(0xFF4CAF50)
val WarnRed = Color(0xFFEF5350)

private val GmgColors = darkColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color(0xFF2A1A00),
    tertiary = ProbeBlue,
    background = Charcoal,
    onBackground = Bone,
    surface = CharcoalRaised,
    onSurface = Bone,
    surfaceVariant = CharcoalCard,
    onSurfaceVariant = Muted,
    error = WarnRed,
    outline = Color(0xFF4A403A),
)

private val GmgType = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
)

@Composable
fun GmgTheme(content: @Composable () -> Unit) {
    // BBQ app: always the warm dark theme regardless of system setting.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = GmgColors, typography = GmgType, content = content)
}
