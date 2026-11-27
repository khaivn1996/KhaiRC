package com.khaiit.kremote.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun KhaiRCTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkTechColorScheme,
        typography = KhaiRCTypography,
        shapes = KhaiRCShapes,
        content = content
    )
}
