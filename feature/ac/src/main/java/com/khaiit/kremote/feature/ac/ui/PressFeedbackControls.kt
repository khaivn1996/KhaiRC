package com.khaiit.kremote.feature.ac.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared IR button press feedback. This is *not* an IR transmission confirmation.
 * The same onClick executes immediately once; the animation is visual only.
 * Keep motion subtle and respect Material3 enabled/disabled semantics.
 */
@Composable
private fun rememberPressScale(
    source: MutableInteractionSource,
    enabled: Boolean
): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "ac_press_feedback"
    )
    return scale
}

private fun Modifier.withPressScale(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}

@Composable
internal fun PressFeedbackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled)
    Button(
        onClick = onClick,
        modifier = modifier.withPressScale(scale),
        enabled = enabled,
        interactionSource = source,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
internal fun PressFeedbackOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.withPressScale(scale),
        enabled = enabled,
        interactionSource = source,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
internal fun PressFeedbackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled)
    IconButton(
        onClick = onClick,
        modifier = modifier.withPressScale(scale),
        enabled = enabled,
        interactionSource = source,
        content = content
    )
}
