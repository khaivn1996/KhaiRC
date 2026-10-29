package com.khaiit.kremote.feature.ac.toshiba

import androidx.compose.foundation.BorderStroke
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
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Toshiba AC",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (controller.isIrAvailable) "IR · Trạng thái lưu trên máy" else "Không có bộ phát IR",
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
                        contentDescription = if (state.power) "Gửi lệnh tắt Toshiba AC" else "Gửi lệnh bật Toshiba AC",
                        tint = if (state.power) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { execute(controller::decreaseTemperature) },
                    enabled = controller.isIrAvailable && state.power &&
                        state.temperatureC > ToshibaAcState.MIN_TEMPERATURE_C
                ) { Text("−", fontSize = 24.sp) }

                Text(
                    text = "${state.temperatureC} °C",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                OutlinedButton(
                    onClick = { execute(controller::increaseTemperature) },
                    enabled = controller.isIrAvailable && state.power &&
                        state.temperatureC < ToshibaAcState.MAX_TEMPERATURE_C
                ) { Text("+", fontSize = 24.sp) }
            }

            Text(
                text = "COOL • Fan ${state.fanSpeed.label} • Swing ${if (state.swing) "ON" else "OFF"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { execute(controller::chooseCool) },
                enabled = controller.isIrAvailable,
                modifier = Modifier.fillMaxWidth()
            ) { Text("COOL") }

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
                ) { Text("FIX") }

                OutlinedButton(
                    onClick = { execute(controller::toggleSwing) },
                    enabled = controller.isIrAvailable && state.power,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.swing) "SWING OFF" else "SWING ON") }
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
