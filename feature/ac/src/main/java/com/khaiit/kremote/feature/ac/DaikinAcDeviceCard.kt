package com.khaiit.kremote.feature.ac

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
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DaikinAcDeviceCard(
    controller: DaikinAcController,
    modifier: Modifier = Modifier
) {
    var state by remember {
        mutableStateOf(controller.state)
    }

    var statusText by remember {
        mutableStateOf<String?>(null)
    }

    val execute: (() -> DaikinAcState) -> Unit = { action ->
        runCatching(action)
            .onSuccess { newState ->
                state = newState
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
            modifier = Modifier.padding(20.dp)
        ) {
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
                        text = if (controller.isIrAvailable) {
                            "IR ready"
                        } else {
                            "IR unavailable"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        execute(controller::togglePower)
                    },
                    enabled = controller.isIrAvailable,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.power) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = "Power",
                        tint = if (state.power) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onError
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.size(18.dp))

            Text(
                text = "${state.temperatureC} C",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.size(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = state.mode.displayName(),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "Fan ${state.fanSpeed.displayName()}",
                    style = MaterialTheme.typography.bodyLarge
                )

                if (state.swing) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription = "Swing on",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.size(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        execute(controller::decreaseTemperature)
                    },
                    enabled = controller.isIrAvailable
                ) {
                    Text("TEMP -")
                }

                OutlinedButton(
                    onClick = {
                        execute(controller::increaseTemperature)
                    },
                    enabled = controller.isIrAvailable
                ) {
                    Text("TEMP +")
                }
            }

            Spacer(modifier = Modifier.size(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        execute(controller::cycleFanSpeed)
                    },
                    enabled = controller.isIrAvailable
                ) {
                    Text("SPEED")
                }

                OutlinedButton(
                    onClick = {
                        execute(controller::toggleSwing)
                    },
                    enabled = controller.isIrAvailable
                ) {
                    Text("SWING")
                }
            }

            Spacer(modifier = Modifier.size(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        execute(controller::cycleMode)
                    },
                    enabled = controller.isIrAvailable
                ) {
                    Text("MODE")
                }

                OutlinedButton(
                    onClick = {
                        execute(controller::resendCurrentState)
                    },
                    enabled = controller.isIrAvailable
                ) {
                    Text("SEND")
                }
            }

            statusText?.let { message ->
                Spacer(modifier = Modifier.size(10.dp))
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
