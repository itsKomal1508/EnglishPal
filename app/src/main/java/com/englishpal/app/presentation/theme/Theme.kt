package com.englishpal.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val DarkColorScheme = darkColorScheme(
    primary = IndigoLight,
    onPrimary = LightSurface,
    primaryContainer = ElectricIndigo,
    onPrimaryContainer = TextPrimaryDark,
    secondary = CoralPink,
    onSecondary = LightSurface,
    tertiary = EmeraldMint,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    error = CoralDark,
    onError = LightSurface
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricIndigo,
    onPrimary = LightSurface,
    primaryContainer = Color(0xFFEEEBFF),
    onPrimaryContainer = Color(0xFF2C1C8C),
    secondary = CoralPink,
    onSecondary = LightSurface,
    tertiary = EmeraldMint,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    error = CoralDark,
    onError = LightSurface
)
@Composable
fun EnglishPalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled Monet by default so EnglishPal brand colors take priority
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
