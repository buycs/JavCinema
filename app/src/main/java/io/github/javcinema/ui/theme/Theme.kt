package io.github.javcinema.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    error = GoogleRed,
    onError = SurfaceDark
)

@Composable
fun JAViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JAViewerTypography,
        content = content
    )
}
