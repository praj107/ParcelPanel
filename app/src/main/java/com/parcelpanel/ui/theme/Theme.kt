package com.parcelpanel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ParcelPanelColors = darkColorScheme(
    primary = TealPrimary,
    onPrimary = InkBlack,
    secondary = AmberWarning,
    tertiary = MintSuccess,
    error = RoseError,
    background = InkBlack,
    onBackground = CloudText,
    surface = NightSurface,
    onSurface = CloudText,
    surfaceVariant = NightSurfaceAlt,
    onSurfaceVariant = MistText,
    outline = BorderSoft,
)

@Composable
fun ParcelPanelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ParcelPanelColors,
        typography = ParcelPanelTypography,
        content = content,
    )
}

