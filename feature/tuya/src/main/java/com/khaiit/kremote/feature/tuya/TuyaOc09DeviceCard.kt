package com.khaiit.kremote.feature.tuya

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.launch

@Composable
fun TuyaOc09DeviceCard(controller: TuyaOc09Controller, modifier: Modifier = Modifier) {
    val state = controller.state
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val labelStore = remember(context) { TuyaOc09LabelStore(context.applicationContext) }
    var names by remember(labelStore) { mutableStateOf(labelStore.load()) }
    var showSettings by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

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
            // Common actions live in the header; names are edited separately from secret settings.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rạng Đông OC09", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !state.configured -> "Chưa cấu hình Tuya"
                            state.busy -> "Đang xử lý..."
                            state.verified -> "Tuya Cloud · Đã đồng bộ"
                            else -> "Tuya Cloud · Chưa xác minh"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.verified) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showRename = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Đổi tên ổ cắm")
                }
                IconButton(
                    onClick = { showSettings = true },
                    enabled = !state.busy,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Cấu hình Tuya")
                }
                IconButton(
                    onClick = { scope.launch { controller.refresh() } },
                    enabled = state.configured && !state.busy,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Làm mới trạng thái")
                }
            }

            // Master power: one Tuya POST with six commands, confirmed by a fresh status GET.
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
                        Text("Nguồn tổng", fontWeight = FontWeight.SemiBold)
                        Text(
                            when (state.masterPower) {
                                true -> "Có ổ đang bật"
                                false -> "Tất cả đã tắt"
                                null -> "Chưa xác minh"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TuyaPowerButton(
                        power = state.masterPower,
                        enabled = !state.busy,
                        description = when (state.masterPower) {
                            true -> "Tắt toàn bộ 5 ổ cắm và USB"
                            false -> "Bật toàn bộ 5 ổ cắm và USB"
                            null -> "Nguồn tổng chưa xác minh"
                        },
                        onClick = { scope.launch { controller.toggleMaster() } }
                    )
                }
            }

            // Fixed physical order: outlet 1..5 and USB, independent of custom display names.
            TuyaChannel.ALL.chunked(2).forEach { group ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    group.forEach { channel ->
                        val power = state.channelPower(channel.code)
                        val name = names[channel.code] ?: channel.label
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    minLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when (power) {
                                            true -> "Đang bật"
                                            false -> "Đang tắt"
                                            null -> "Chưa rõ"
                                        },
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TuyaPowerButton(
                                        power = power,
                                        enabled = !state.busy,
                                        description = when (power) {
                                            true -> "Tắt $name"
                                            false -> "Bật $name"
                                            null -> "$name chưa xác minh"
                                        },
                                        onClick = { scope.launch { controller.toggle(channel.code) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showSettings) {
        TuyaSettingsDialog(controller = controller, onDismiss = { showSettings = false })
    }
    if (showRename) {
        TuyaRenameDialog(
            current = names,
            onDismiss = { showRename = false },
            onSave = { edited ->
                labelStore.save(edited)
                names = labelStore.load()
            }
        )
    }
}

/** Same visual language as the AC cards; never allow action on unknown status. */
@Composable
private fun TuyaPowerButton(
    power: Boolean?,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val background = when (power) {
        true -> MaterialTheme.colorScheme.error
        false -> MaterialTheme.colorScheme.primary
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when (power) {
        true -> MaterialTheme.colorScheme.onError
        false -> MaterialTheme.colorScheme.onPrimary
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(
        onClick = onClick,
        enabled = enabled && power != null,
        modifier = Modifier.size(46.dp).clip(CircleShape).background(background)
    ) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = description, tint = foreground)
    }
}

@Composable
private fun TuyaRenameDialog(
    current: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    var edited by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi tên ổ cắm") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Để trống để khôi phục tên mặc định. Tối đa 24 ký tự mỗi tên.",
                    style = MaterialTheme.typography.bodySmall)
                TuyaChannel.ALL.forEach { channel ->
                    OutlinedTextField(
                        value = edited[channel.code].orEmpty(),
                        onValueChange = { value ->
                            if (value.length <= TuyaOc09LabelRules.MAX_LENGTH) {
                                edited = edited + (channel.code to value)
                                error = null
                            }
                        },
                        label = { Text(channel.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { onSave(edited) }
                    .onSuccess { onDismiss() }
                    .onFailure { error = it.message ?: "Không lưu được tên ổ cắm" }
            }) { Text("LƯU") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("HỦY") } }
    )
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
