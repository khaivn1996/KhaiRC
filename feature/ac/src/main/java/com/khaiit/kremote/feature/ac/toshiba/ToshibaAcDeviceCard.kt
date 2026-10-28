package com.khaiit.kremote.feature.ac.toshiba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToshibaAcDeviceCard(
    controller: ToshibaAcController,
    modifier: Modifier = Modifier
) {
    var state by remember(controller) { mutableStateOf(controller.state) }
    var statusText by remember(controller) { mutableStateOf<String?>(null) }

    val execute: (() -> ToshibaAcState) -> Unit = { action ->
        runCatching(action)
            .onSuccess { updated ->
                state = updated
                statusText = null
            }
            .onFailure { error ->
                statusText = error.message ?: "IR command failed"
            }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Toshiba AC",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (controller.isIrAvailable) "IR ready" else "IR unavailable",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (state.power) "ON" else "OFF",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = "${ToshibaAcState.TEMPERATURE_C} °C",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = "COOL • Fan ${state.fanSpeed.label} • Swing ${if (state.swing) "ON" else "OFF"}")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { execute(controller::chooseCool) },
                    enabled = controller.isIrAvailable,
                    modifier = Modifier.weight(1f)
                ) { Text("COOL 24°") }
                OutlinedButton(
                    onClick = { execute(controller::togglePower) },
                    enabled = controller.isIrAvailable,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.power) "POWER OFF" else "POWER ON") }
            }

            Text(text = "Tốc độ quạt", fontWeight = FontWeight.SemiBold)
            ToshibaFanSpeed.entries.chunked(3).forEach { group ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.forEach { speed ->
                        ToshibaFanButton(
                            speed = speed,
                            selected = state.fanSpeed == speed,
                            enabled = controller.isIrAvailable && state.power,
                            modifier = Modifier.weight(1f),
                            onClick = { execute { controller.setFanSpeed(speed) } }
                        )
                    }
                }
            }

            Text(text = "Hướng gió", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { execute(controller::fix) },
                    enabled = controller.isIrAvailable && state.power,
                    modifier = Modifier.weight(1f)
                ) { Text("FIX +1 bước") }

                OutlinedButton(
                    onClick = { execute(controller::toggleSwing) },
                    enabled = controller.isIrAvailable && state.power,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.swing) "SWING OFF" else "SWING ON") }
            }

            OutlinedButton(
                onClick = { execute(controller::resendCurrentState) },
                enabled = controller.isIrAvailable,
                modifier = Modifier.fillMaxWidth()
            ) { Text("SEND trạng thái") }

            statusText?.let { message ->
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ToshibaFanButton(
    speed: ToshibaFanSpeed,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(speed.label)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(speed.label)
        }
    }
}
