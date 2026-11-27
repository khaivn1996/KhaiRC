package com.khaiit.kremote.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaultTypography = Typography()

internal val KhaiRCTypography = Typography(
    displaySmall = defaultTypography.displaySmall.copy(fontSize = 42.sp, fontWeight = FontWeight.Bold)
)
