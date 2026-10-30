package com.khaiit.kremote.feature.xiaomi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.launch

@Composable
fun XiaomiPlugDeviceCard(controller: XiaomiPlugController, modifier: Modifier = Modifier) {
    val state = controller.state
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showOffConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Xiaomi · Milife 10A", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !state.configured -> "Chưa cấu hình MIoT LAN"
                            state.busy -> "Đang xử lý..."
                            state.power != null -> "LAN · Đã xác minh · ${state.ip}"
                            else -> "LAN · Chưa xác minh · ${state.ip}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showSettings = true }, enabled = !state.busy,
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Filled.Settings, contentDescription = "Cấu hình Xiaomi") }
                IconButton(
                    onClick = { scope.launch { controller.refresh() } },
                    enabled = state.configured && !state.busy, modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Filled.Refresh, contentDescription = "Làm mới Xiaomi") }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nguồn ổ cắm", fontWeight = FontWeight.SemiBold)
                        Text(
                            when (state.power) {
                                true -> "Đang bật"
                                false -> "Đang tắt"
                                null -> "Chưa xác minh"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    val bg = when (state.power) {
                        true -> MaterialTheme.colorScheme.error
                        false -> MaterialTheme.colorScheme.primary
                        null -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    IconButton(
                        enabled = state.configured && !state.busy && state.power != null,
                        onClick = {
                            when (state.power) {
                                true -> showOffConfirm = true
                                false -> scope.launch { controller.setPower(true) }
                                null -> Unit
                            }
                        },
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(bg)
                    ) {
                        Icon(
                            Icons.Filled.PowerSettingsNew,
                            contentDescription = if (state.power == true) "Tắt ổ Xiaomi" else "Bật ổ Xiaomi",
                            tint = when (state.power) {
                                true -> MaterialTheme.colorScheme.onError
                                false -> MaterialTheme.colorScheme.onPrimary
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showOffConfirm) {
        AlertDialog(
            onDismissRequest = { showOffConfirm = false },
            title = { Text("Xác nhận tắt ổ Xiaomi") },
            text = { Text(
                "Nếu ổ này cấp nguồn cho router hoặc VPN, tắt ổ sẽ làm mất kết nối " +
                    "và bạn có thể không bật lại từ xa được. Chỉ xác nhận khi ở nhà " +
                    "hoặc đã có cách khôi phục nguồn."
            ) },
            confirmButton = {
                TextButton(
                    enabled = !state.busy && state.power == true,
                    onClick = {
                        showOffConfirm = false
                        scope.launch { controller.setPower(false) }
                    }
                ) { Text("TÔI HIỂU, TẮT") }
            },
            dismissButton = { TextButton(onClick = { showOffConfirm = false }) { Text("HỦY") } }
        )
    }

    if (showSettings) XiaomiPlugSettingsDialog(controller) { showSettings = false }
}

@Composable
private fun XiaomiPlugSettingsDialog(
    controller: XiaomiPlugController,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state = controller.state
    var ip by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Cấu hình Xiaomi MIoT LAN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Model sixwgh.plug.v7icw1. Nhập IP và token 32 ký tự hex. " +
                        "Cấu hình được mã hóa và không hiển thị lại. " +
                        "Để trống trường bất kỳ để giữ giá trị đã lưu.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("IP ổ Xiaomi (192.168.2.103)") }
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Device token") },
                    visualTransformation = PasswordVisualTransformation()
                )
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = {
                scope.launch { if (controller.saveConfig(ip, token)) onDismiss() }
            }) { Text("LƯU") }
        },
        dismissButton = { TextButton(enabled = !state.busy, onClick = onDismiss) { Text("HỦY") } }
    )
}
