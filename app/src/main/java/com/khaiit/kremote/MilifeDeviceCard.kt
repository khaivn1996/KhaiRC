package com.khaiit.kremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat

@Composable
fun MilifeDeviceCard(controller: MilifeController, modifier: Modifier = Modifier) {
    val state = controller.state
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) controller.refresh() else controller.permissionDenied()
    }
    val refresh: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_LOCAL_NETWORK
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            controller.refresh()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Milife 10A", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("ESP32 Hub · miIO LAN · Chỉ đọc", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showSettings = true }, enabled = !state.busy,
                    modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "Cấu hình ESP32 Hub")
                }
                IconButton(onClick = refresh, enabled = !state.busy && state.configured,
                    modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Đọc lại trạng thái Milife")
                }
            }
            Text(
                when (state.power) {
                    true -> "Đang bật"
                    false -> "Đang tắt"
                    null -> "Không kết nối"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.power == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.secondary
            )
            Text(state.message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Read-only release: these buttons have no executable action or POST path.
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Text("BẬT")
                }
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Text("TẮT")
                }
            }
            Text("Điều khiển nguồn tạm khóa để bảo vệ hệ thống đang chạy.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showSettings) {
        var address by remember { mutableStateOf(state.address) }
        // Secret is only held in this dialog's ephemeral state; never restored or displayed.
        var newKey by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text("Kết nối ESP32 Hub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Dùng chung cấu hình Hub đã mã hóa. Không nhập Xiaomi token.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = address, onValueChange = { address = it; error = null },
                        label = { Text("IP của ESP32 Hub") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newKey, onValueChange = { newKey = it; error = null },
                        label = { Text("HUB_API_KEY") },
                        supportingText = { Text(if (state.configured) "Để trống để giữ key đã lưu" else "Nhập key 64 ký tự") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (controller.saveConfiguration(address, newKey)) {
                        newKey = ""
                        showSettings = false
                    } else {
                        error = "Địa chỉ hoặc API key chưa hợp lệ."
                    }
                }) { Text("LƯU") }
            },
            dismissButton = {
                TextButton(onClick = { newKey = ""; showSettings = false }) { Text("HỦY") }
            }
        )
    }
}
