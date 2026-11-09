package com.khaiit.kremote.feature.fan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FanDeviceCard(
    irAvailable: Boolean,
    onPowerHigh: () -> Unit,
    onSwing: () -> Unit,
    onOff: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Press feedback is visual only: IR onClick callbacks execute immediately.
    val powerInteraction = remember { MutableInteractionSource() }
    val highInteraction = remember { MutableInteractionSource() }
    val swingInteraction = remember { MutableInteractionSource() }
    val powerPressed by powerInteraction.collectIsPressedAsState()
    val highPressed by highInteraction.collectIsPressedAsState()
    val swingPressed by swingInteraction.collectIsPressedAsState()
    val powerScale by animateFloatAsState(
        targetValue = if (powerPressed && irAvailable) 0.96f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "fan_power_press"
    )
    val highScale by animateFloatAsState(
        targetValue = if (highPressed && irAvailable) 0.96f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "fan_high_press"
    )
    val swingScale by animateFloatAsState(
        targetValue = if (swingPressed && irAvailable) 0.96f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "fan_swing_press"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        text = "Quạt trần",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (irAvailable) {
                            "IR sẵn sàng · Chỉ gửi lệnh"
                        } else {
                            "Không có bộ phát IR"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // OFF
                IconButton(
                    onClick = onOff,
                    enabled = irAvailable,
                    interactionSource = powerInteraction,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = powerScale
                            scaleY = powerScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = "Gửi lệnh tắt quạt qua IR",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // HIGH & SWING
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Button(
                    onClick = onPowerHigh,
                    enabled = irAvailable,
                    interactionSource = highInteraction,
                    modifier = Modifier.weight(1f).graphicsLayer {
                        scaleX = highScale
                        scaleY = highScale
                    }
                ) {
                    Text("HIGH")
                }

                OutlinedButton(
                    onClick = onSwing,
                    enabled = irAvailable,
                    interactionSource = swingInteraction,
                    modifier = Modifier.weight(1f).graphicsLayer {
                        scaleX = swingScale
                        scaleY = swingScale
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text("SWING")
                }
            }
        }
    }
}