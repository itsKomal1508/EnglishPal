package com.englishpal.app.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Vibrant Primary Palette
val ElectricIndigo = Color(0xFF6C5CE7)
val IndigoLight = Color(0xFF8C7AE6)
val IndigoDark = Color(0xFF4834D4)

// Playful Accents
val CoralPink = Color(0xFFFF7675)
val CoralDark = Color(0xFFD63031)
val EmeraldMint = Color(0xFF00B894)
val TealBright = Color(0xFF00CEC9)
val FlameAmber = Color(0xFFFFA502)
val FlameOrange = Color(0xFFFF7675)

// Neutral Background & Surface Colors (Light)
val LightBackground = Color(0xFFF8F9FE)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F3F9)
val TextPrimaryLight = Color(0xFF2D3436)
val TextSecondaryLight = Color(0xFF636E72)

// Neutral Background & Surface Colors (Dark)
val DarkBackground = Color(0xFF12131C)
val DarkSurface = Color(0xFF1E1E2E)
val DarkSurfaceVariant = Color(0xFF2A2A3D)
val TextPrimaryDark = Color(0xFFF8F9FE)
val TextSecondaryDark = Color(0xFFA0A0B2)

// Signature Gradients
val PrimaryGradient = Brush.horizontalGradient(
    colors = listOf(ElectricIndigo, Color(0xFF8E44AD))
)

val StreakGradient = Brush.horizontalGradient(
    colors = listOf(FlameAmber, Color(0xFFFF5252))
)

val SuccessGradient = Brush.horizontalGradient(
    colors = listOf(EmeraldMint, TealBright)
)

val CardAccentGradient = Brush.horizontalGradient(
    colors = listOf(ElectricIndigo.copy(alpha = 0.15f), CoralPink.copy(alpha = 0.1f))
)
