package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

private val SonicForgeDarkColorScheme =
  darkColorScheme(
    primary = StudioAmber,
    onPrimary = StudioObsidian,
    primaryContainer = StudioAmberDeep,
    onPrimaryContainer = StudioAmberBright,
    secondary = StudioCyan,
    onSecondary = StudioObsidian,
    secondaryContainer = StudioCyanDeep,
    onSecondaryContainer = StudioCyan,
    tertiary = StudioCoral,
    onTertiary = StudioObsidian,
    tertiaryContainer = StudioCoralDeep,
    onTertiaryContainer = StudioCoral,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
  )

private val SonicForgeLightColorScheme =
  lightColorScheme(
    primary = LightAmber,
    onPrimary = Color.White,
    primaryContainer = LightAmberContainer,
    onPrimaryContainer = LightOnAmberContainer,
    secondary = LightCyan,
    onSecondary = Color.White,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = LightOnCyanContainer,
    tertiary = LightCoral,
    onTertiary = Color.White,
    tertiaryContainer = LightCoralContainer,
    onTertiaryContainer = LightOnCoralContainer,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = LightSurfaceHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineSubtle,
  )

val SonicForgeShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun resolveDarkTheme(themeMode: ThemeMode): Boolean {
  return when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
  }
}

@Composable
fun SonicForgeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) SonicForgeDarkColorScheme else SonicForgeLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = SonicForgeShapes,
    content = content,
  )
}
