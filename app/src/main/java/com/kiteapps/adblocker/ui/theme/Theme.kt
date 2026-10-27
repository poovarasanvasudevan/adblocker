package com.kiteapps.adblocker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = GoogleBluePrimaryDark,
    onPrimary = GoogleBlueOnPrimaryDark,
    primaryContainer = GoogleBluePrimaryContainerDark,
    onPrimaryContainer = GoogleBlueOnPrimaryContainerDark,
    secondary = GoogleTealDark,
    secondaryContainer = GoogleTealContainerDark,
    tertiary = GoogleCyanDark,
    tertiaryContainer = GoogleCyanContainerDark,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    error = BlockedCoralDark,
    errorContainer = BlockedCoralContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = GoogleBluePrimaryLight,
    onPrimary = GoogleBlueOnPrimaryLight,
    primaryContainer = GoogleBluePrimaryContainerLight,
    onPrimaryContainer = GoogleBlueOnPrimaryContainerLight,
    secondary = GoogleTealLight,
    secondaryContainer = GoogleTealContainerLight,
    tertiary = GoogleCyanLight,
    tertiaryContainer = GoogleCyanContainerLight,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    error = BlockedCoral,
    errorContainer = BlockedCoralContainerLight
)

@Composable
fun AdBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colorScheme.background.toArgb()))
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
