package com.khaiit.kremote.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khaiit.kremote.core.ui.theme.KhaiRCDimens

enum class PowerTone { Danger, Primary, Neutral }

@Composable
fun PowerIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    tone: PowerTone,
    enabled: Boolean = true,
    size: Dp = KhaiRCDimens.PowerButtonSize,
    iconSize: Dp? = null,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val (background, tint) = when (tone) {
        PowerTone.Danger -> colors.error to colors.onError
        PowerTone.Primary -> colors.primary to colors.onPrimary
        PowerTone.Neutral -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    if (showLabel) {
        PressFeedbackOutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = if (enabled) background else LocalContentColor.current,
                modifier = Modifier.size(iconSize ?: 18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(contentDescription)
        }
        return
    }
    PressFeedbackIconButton(
        onClick = onClick,
        modifier = modifier.size(size).clip(CircleShape).background(background),
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = contentDescription,
            tint = tint,
            modifier = if (iconSize == null) Modifier else Modifier.size(iconSize)
        )
    }
}
