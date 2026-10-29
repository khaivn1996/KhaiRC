package com.khaiit.kremote.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Dark Tech V2: a consistent brand palette, not the system wallpaper's dynamic colors. */
private val DarkTechColorScheme = darkColorScheme(
    primary = Color(0xFF399BFF),
    onPrimary = Color(0xFF061728),
    primaryContainer = Color(0xFF17385D),
    onPrimaryContainer = Color(0xFFD4EBFF),
    secondary = Color(0xFF2CD7CA),
    onSecondary = Color(0xFF062421),
    secondaryContainer = Color(0xFF124840),
    onSecondaryContainer = Color(0xFFC4FFF6),
    tertiary = Color(0xFF73DBEF),
    onTertiary = Color(0xFF09232A),
    background = Color(0xFF0A101C),
    onBackground = Color(0xFFEDF5FF),
    surface = Color(0xFF111D30),
    onSurface = Color(0xFFEDF5FF),
    surfaceVariant = Color(0xFF24354B),
    onSurfaceVariant = Color(0xFFA8BAD1),
    surfaceContainerLowest = Color(0xFF09111F),
    surfaceContainerLow = Color(0xFF122035),
    surfaceContainer = Color(0xFF162237),
    surfaceContainerHigh = Color(0xFF1B2D45),
    surfaceContainerHighest = Color(0xFF24364E),
    outline = Color(0xFF54708C),
    outlineVariant = Color(0xFF34475F),
    error = Color(0xFFF16C81),
    onError = Color(0xFF290C15),
    errorContainer = Color(0xFF662A3A),
    onErrorContainer = Color(0xFFFFD9E0)
)

/** Kept for explicit previews. The actual app uses Dark Tech by default. */
private val LightColorScheme = lightColorScheme()

@Composable
fun KhaiRCTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Explicit opt-in for previews/experiments only; default app branding is fixed.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkTechColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
