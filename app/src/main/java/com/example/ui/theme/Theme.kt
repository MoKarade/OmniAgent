package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SoniaGoogleDarkColorScheme = darkColorScheme(
    primary = GoogleBlue,
    onPrimary = OnGoogleBlue,
    primaryContainer = GoogleBlueContainer,
    onPrimaryContainer = GoogleBlue,
    secondary = GoogleYellow,
    onSecondary = Color(0xFF261900),
    secondaryContainer = GoogleYellowContainer,
    onSecondaryContainer = GoogleYellow,
    tertiary = GoogleGreen,
    onTertiary = Color(0xFF00210B),
    tertiaryContainer = GoogleGreenContainer,
    onTertiaryContainer = GoogleGreen,
    error = GoogleRed,
    onError = Color(0xFF370B08),
    errorContainer = GoogleRedContainer,
    onErrorContainer = GoogleRed,
    background = GoogleBackground,
    onBackground = GoogleTextPrimary,
    surface = GoogleSurface,
    onSurface = GoogleTextPrimary,
    surfaceVariant = GoogleSurfaceVariant,
    onSurfaceVariant = GoogleTextSecondary,
    outline = GoogleBorderSubtle
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SoniaGoogleDarkColorScheme,
        typography = Typography,
        content = content
    )
}
