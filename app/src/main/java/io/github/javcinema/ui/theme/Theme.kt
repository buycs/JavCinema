package io.github.javcinema.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations

private val LightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = SurfaceLight,
    primaryContainer = OrangePrimaryLight,
    onPrimaryContainer = OrangePrimaryDark,
    secondary = AccentBlue,
    onSecondary = SurfaceLight,
    secondaryContainer = AccentBlueLight,
    onSecondaryContainer = AccentBlue,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = CardLight,
    onSurfaceVariant = NeutralVariantLight,
    surfaceContainerLowest = BackgroundLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = CardHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    error = GoogleRed,
    onError = SurfaceLight
)

private val DarkColorScheme = darkColorScheme(
    primary = OrangePrimary,
    onPrimary = SurfaceDark,
    primaryContainer = OrangePrimaryDark,
    onPrimaryContainer = OrangePrimaryLight,
    secondary = AccentBlueLight,
    onSecondary = SurfaceDark,
    secondaryContainer = AccentBlue,
    onSecondaryContainer = AccentBlueLight,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = NeutralVariantDark,
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardHighestDark,
    surfaceContainerHighest = CardHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    error = GoogleRed,
    onError = SurfaceDark
)

@Composable
fun JavCinemaTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val prefsVersion by JavCinema.uiPrefsVersionFlow.collectAsState()
    val themeMode = if (prefsVersion >= 0) Configurations.themeMode else "system"
    val resolvedDark = darkTheme ?: when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (resolvedDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !resolvedDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JavCinemaTypography,
        content = content
    )
}
