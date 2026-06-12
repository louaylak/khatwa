package com.khatwa.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ----- Night asphalt + electric violet palette -----
val NightBg = Color(0xFF0C1118)
val Surface1 = Color(0xFF151D27)
val Surface2 = Color(0xFF1C2735)
val Ember = Color(0xFF8B5CF6)
val EmberDeep = Color(0xFF6D28D9)
val EmberGlow = Color(0xFFC4B5FD)
val Teal = Color(0xFF2FD6A8)
val Sand = Color(0xFFF4EDE4)
val Muted = Color(0xFF9AA7B5)
val Amber = Color(0xFFFFC24B)
val Danger = Color(0xFFFF5A5A)
val Outline = Color(0xFF2B3645)

private val scheme = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF160B30),
    primaryContainer = EmberDeep,
    onPrimaryContainer = Sand,
    secondary = Teal,
    onSecondary = Color(0xFF062019),
    background = NightBg,
    onBackground = Sand,
    surface = Surface1,
    onSurface = Sand,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Color(0xFF2A0606),
    outline = Outline
)

private val type = Typography(
    displayLarge = TextStyle(fontSize = 54.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
)

@Composable
fun KhatwaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
