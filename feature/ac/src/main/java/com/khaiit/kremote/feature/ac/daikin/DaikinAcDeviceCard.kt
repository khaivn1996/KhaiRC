package com.khaiit.kremote.feature.ac.daikin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Daikin AC card with the same visual structure as ToshibaAcDeviceCard:
 * header + power, centered temperature controls, state summary, and
 * grouped mode/fan/swing controls.
 *
 * The IR commands are unchanged; this file only rearranges the UI.
 */
@Composable
fun DaikinAcDeviceCard(
    controller: DaikinAcController,
    modifier: Modifier = Modifier
) {
    var state by remember(controller) { mutableStateOf(controller.state) }
    var statusText by remember(controller) { mutableStateOf<String?>(null) }

    val execute: (() -> DaikinAcState) -> Unit = { action ->
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
            // Same header and power-button semantics as Toshiba.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daikin AC",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (controller.isIrAvailable) "IR ready" else "IR unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { execute(controller::togglePower) },
                    enabled = controller.isIrAvailable,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.power) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = if (state.power) "Tắt Daikin AC" else "Bật Daikin AC",
                        tint = if (state.power) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                }
            }

            // Align the temperature +/- controls with the Toshiba card.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { execute(controller::decreaseTemperature) },
                    enabled = controller.isIrAvailable &&
                        state.temperatureC > DaikinAcState.MIN_TEMPERATURE_C
                ) {
                    Text("−", fontSize = 24.sp)
                }

                Text(
                    text = "${state.temperatureC} °C",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                OutlinedButton(
                    onClick = { execute(controller::increaseTemperature) },
                    enabled = controller.isIrAvailable &&
                        state.temperatureC < DaikinAcState.MAX_TEMPERATURE_C
                ) {
                    Text("+", fontSize = 24.sp)
                }
            }

            Text(
                text = "${state.mode.displayName()} • Fan ${state.fanSpeed.displayName()} • " +
                    "Swing ${if (state.swing) "ON" else "OFF"}",
                style = MaterialTheme.typography.bodyLarge
            )

            // Daikin cycles through modes instead of selecting only COOL.
            Button(
                onClick = { execute(controller::cycleMode) },
                enabled = controller.isIrAvailable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("MODE")
            }

            Text("Tốc độ quạt", fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = { execute(controller::cycleFanSpeed) },
                enabled = controller.isIrAvailable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SPEED")
            }

            Text("Hướng gió", fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = { execute(controller::toggleSwing) },
                enabled = controller.isIrAvailable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.swing) "SWING OFF" else "SWING ON")
            }

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

private fun DaikinMode.displayName(): String = when (this) {
    DaikinMode.AUTO -> "Auto"
    DaikinMode.COOL -> "Cool"
    DaikinMode.DRY -> "Dry"
    DaikinMode.FAN -> "Fan"
    DaikinMode.HEAT -> "Heat"
}

private fun DaikinFanSpeed.displayName(): String = when (this) {
    DaikinFanSpeed.LOW -> "Low"
    DaikinFanSpeed.HIGH -> "High"
}
