package com.khaiit.kremote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.khaiit.kremote.core.network.HubEndpoint

@Composable
fun HubSettingsDialog(
    address: String,
    keyConfigured: Boolean,
    fanTransport: HubTransport,
    busy: Boolean = false,
    error: String? = null,
    onSave: (address: String, replacementKey: String?, fanTransport: HubTransport) -> Unit,
    onDismiss: () -> Unit
) {
    var addressInput by remember(address) { mutableStateOf(address) }
    // Never pre-fill a persisted key, or save an entered key into instance state.
    var keyInput by remember { mutableStateOf("") }
    var transportInput by remember(fanTransport) { mutableStateOf(fanTransport) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Cấu hình ESP32 Hub") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Dùng địa chỉ IPv4 trong mạng nội bộ. API key được mã hóa trên điện thoại và không hiển thị lại.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it; validationError = null },
                    label = { Text("Địa chỉ IPv4 của Hub") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it; validationError = null },
                    label = { Text("API key") },
                    supportingText = {
                        Text(if (keyConfigured) "Để trống để giữ API key đã lưu." else "Nhập API key gồm 64 ký tự.")
                    },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Điều khiển quạt bằng", style = MaterialTheme.typography.titleSmall)
                HubTransport.entries.forEach { transport ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = transportInput == transport,
                                enabled = !busy,
                                role = Role.RadioButton,
                                onClick = { transportInput = transport }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = transportInput == transport, onClick = null, enabled = !busy)
                        Text(
                            if (transport == HubTransport.PHONE) "IR điện thoại" else "ESP32 Hub",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                (validationError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val normalizedAddress = addressInput.trim()
                    validationError = when {
                        runCatching { HubEndpoint(normalizedAddress) }.isFailure ->
                            "Nhập địa chỉ IPv4 hợp lệ trong mạng nội bộ, không kèm http:// hoặc cổng."
                        keyInput.isEmpty() && !keyConfigured -> "Cần nhập API key cho Hub."
                        keyInput.isNotEmpty() && (keyInput.length != 64 || keyInput.any { it.code !in 32..126 }) ->
                            "API key phải có đúng 64 ký tự ASCII in được, không có xuống dòng."
                        else -> null
                    }
                    if (validationError == null) {
                        onSave(normalizedAddress, keyInput.ifEmpty { null }, transportInput)
                    }
                }
            ) { Text(if (busy) "ĐANG LƯU" else "LƯU") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("HỦY") }
        }
    )
}
