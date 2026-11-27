package com.khaiit.kremote.feature.fan

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import com.khaiit.kremote.core.ui.R as CoreUiR
import com.khaiit.kremote.core.ui.components.DeviceCard
import com.khaiit.kremote.core.ui.components.PowerIconButton
import com.khaiit.kremote.core.ui.components.PowerTone
import com.khaiit.kremote.core.ui.components.PressFeedbackButton as Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var statusText by remember { mutableStateOf<UiText?>(null) }
    val execute: (() -> Unit) -> Unit = { action ->
        runCatching(action)
            .onSuccess { statusText = null }
            .onFailure { error ->
                statusText = error.message?.let(UiText::Raw)
                    ?: uiText(CoreUiR.string.common_ir_command_failed)
            }
    }

    DeviceCard(modifier, verticalSpacing = 14.dp) {

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
                    text = stringResource(R.string.fan_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (irAvailable) {
                        stringResource(R.string.fan_ready)
                    } else {
                        stringResource(CoreUiR.string.common_ir_unavailable)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // OFF
            PowerIconButton(
                onClick = { execute(onOff) },
                enabled = irAvailable,
                contentDescription = stringResource(R.string.fan_power_off),
                tone = PowerTone.Danger,
                iconSize = 24.dp
            )
        }

        // HIGH & SWING
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Button(
                onClick = { execute(onPowerHigh) },
                enabled = irAvailable,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.fan_high))
            }

            Button(
                onClick = { execute(onSwing) },
                enabled = irAvailable,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(CoreUiR.string.common_swing))
            }
        }
        statusText?.let { message ->
            Text(
                text = message.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}