package com.khaiit.kremote.feature.tuya

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TuyaOc09DeviceCard(controller: TuyaOc09Controller, modifier: Modifier = Modifier) {
    val state = controller.state
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rạng Đông OC09", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !state.configured -> "Chưa cấu hình Tuya"
                            state.busy -> "Đang xử lý..."
                            state.verified -> "Tuya Cloud · Đã đồng bộ"
                            else -> "Tuya Cloud · Chưa xác minh"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Enabled only after the OC09's six-command batch POST was validated
                // in Tuya API Explorer on the physical device.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { scope.launch { controller.toggleMaster() } },
                        enabled = state.masterPower != null && !state.busy
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PowerSettingsNew,
                            contentDescription = when (state.masterPower) {
                                true -> "Tắt toàn bộ 5 ổ cắm và USB"
                                false -> "Bật toàn bộ 5 ổ cắm và USB"
                                null -> "Nguồn tổng chưa xác minh"
                            },
                            tint = when (state.masterPower) {
                                true -> MaterialTheme.colorScheme.error
                                false -> MaterialTheme.colorScheme.primary
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        when (state.masterPower) { true -> "Tổng ON"; false -> "Tổng OFF"; null -> "Tổng ?" },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            TuyaChannel.ALL.chunked(2).forEach { group ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.forEach { channel ->
                        val power = state.channelPower(channel.code)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(channel.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (power) { true -> "Đang bật"; false -> "Đang tắt"; null -> "Chưa xác minh" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val allowed = power != null && !state.busy
                            if (power == true) {
                                Button(
                                    onClick = { scope.launch { controller.toggle(channel.code) } },
                                    enabled = allowed,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Text("TẮT") }
                            } else {
                                OutlinedButton(
                                    onClick = { scope.launch { controller.toggle(channel.code) } },
                                    enabled = allowed,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("BẬT") }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { showSettings = true }, enabled = !state.busy) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("CẤU HÌNH")
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { controller.refresh() } },
                    enabled = state.configured && !state.busy
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("REFRESH")
                }
            }

            state.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showSettings) {
        TuyaSettingsDialog(controller = controller, onDismiss = { showSettings = false })
    }
}

@Composable
private fun TuyaSettingsDialog(controller: TuyaOc09Controller, onDismiss: () -> Unit) {
    // Do not decrypt or pre-fill persisted credentials into a visible UI.
    var clientId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val state = controller.state

    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        // Protect the Dialog's *own* Android window, not just the Activity window.
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Cấu hình Tuya OC09") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Cấu hình được mã hóa trên điện thoại và không hiển thị lại. " +
                        "Để trống trường bất kỳ để giữ giá trị đã lưu; cấu hình lần đầu cần nhập đủ 3 trường.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Client Secret") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("Device ID OC09") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.busy,
                onClick = {
                    scope.launch {
                        if (controller.saveConfiguration(clientId, secret, deviceId)) onDismiss()
                    }
                }
            ) { Text("LƯU") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text("HỦY") }
        }
    )
}
