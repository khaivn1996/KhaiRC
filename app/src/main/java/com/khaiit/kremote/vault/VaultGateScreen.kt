package com.khaiit.kremote.vault

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.khaiit.kremote.core.vault.*
import com.khaiit.kremote.feature.tuya.TuyaChannel

@Composable
fun VaultGateScreen(controller: VaultController) {
    val activity = LocalContext.current as Activity
    // Hoisted above the state switch: Working/NeedSetup round-trips keep what the user typed.
    // The gate leaves composition on Ready, and a successful restore/setup recreates the Activity.
    val fields = remember { mutableStateMapOf<String, String>() }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sao lưu cloud", style = MaterialTheme.typography.headlineMedium)
            Text("Bản sao được mã hóa bằng mật khẩu chủ. Hãy giữ mật khẩu ở nơi an toàn.")
            when (val state = controller.state) {
                VaultUiState.Ready -> Unit
                VaultUiState.Checking -> CircularProgressIndicator()
                VaultUiState.NeedSignIn -> {
                    Text("Đăng nhập Google để tìm bản sao của bạn.")
                    Button(onClick = { controller.signIn(activity) }) { Text("Đăng nhập Google") }
                    SkipButton(controller)
                }
                is VaultUiState.NeedPassword -> {
                    var password by remember { mutableStateOf("") }
                    Text("Đã tìm thấy bản sao. Nhập mật khẩu chủ để khôi phục các nhóm còn trống.")
                    VaultInput("Mật khẩu chủ", password, { password = it }, secret = true)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        val chars = password.toCharArray()
                        password = ""
                        controller.unlock(chars)
                    }, enabled = password.isNotEmpty()) { Text("Mở bản sao") }
                    TextButton(onClick = controller::forgotPassword) { Text("Quên mật khẩu") }
                    SkipButton(controller)
                }
                is VaultUiState.NeedSetup -> VaultSetupForm(controller, state.localGroups, state.error, fields)
                is VaultUiState.Working -> {
                    CircularProgressIndicator()
                    Text(state.msg)
                }
                is VaultUiState.Error -> {
                    Text(state.msg, color = MaterialTheme.colorScheme.error)
                    Button(onClick = controller::start) { Text("Thử lại") }
                    if (state.canSkip) SkipButton(controller)
                }
            }
        }
    }
}

@Composable
private fun SkipButton(controller: VaultController) {
    TextButton(onClick = controller::skipThisSession) { Text("Bỏ qua lần này") }
}

@Composable
private fun VaultSetupForm(
    controller: VaultController,
    localGroups: Set<Group>,
    setupError: String?,
    fields: SnapshotStateMap<String, String>
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showOverwrite by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun value(key: String): String = fields[key].orEmpty()
    fun submit() {
        val form = VaultBundle(
            tuya = if (listOf("clientId", "clientSecret", "deviceId").any { value(it).isNotBlank() })
                TuyaPart(value("clientId"), value("clientSecret"), value("deviceId")) else null,
            hub = if (value("ipv4").isNotBlank() || value("apiKey").isNotBlank())
                HubPart(value("ipv4").trim(), value("apiKey")) else null,
            https = if (listOf("host", "port", "pin").any { value(it).isNotBlank() })
                HttpsPart(value("host").trim().lowercase(java.util.Locale.ROOT),
                    if (value("port").isBlank()) 0 else value("port").trim().toIntOrNull() ?: -1, value("pin").trim()) else null,
            wol = if (value("mac").isNotBlank() || value("ip").isNotBlank())
                WolPart(value("mac"), value("ip")) else null,
            labels = TuyaChannel.ALL.mapNotNull { channel ->
                value(channel.code).takeIf { it.isNotBlank() }?.let { channel.code to it }
            }.toMap().takeIf { it.isNotEmpty() }
        )
        // Only the master password is cleared; the other fields stay so a validation error needs one fix.
        val chars = password.toCharArray()
        val confirmed = confirm.toCharArray()
        password = ""
        confirm = ""
        error = null
        controller.setup(form, chars, confirmed)
    }
    Text(if (controller.replacingCloud) "Tạo bản sao thay thế" else "Chưa có bản sao")
    if (controller.replacingCloud) {
        Text("Không thể lấy lại dữ liệu cũ nếu quên mật khẩu chủ. Nhập lại cấu hình cần giữ trong bản sao mới.")
    }
    fun validate(): Boolean {
        error = if (password.length < VaultCrypto.MIN_PASSWORD || password != confirm)
            "Mật khẩu chủ tối thiểu 12 ký tự và hai lần nhập phải giống nhau" else null
        return error == null
    }
    val groups = listOf(
        Triple(Group.TUYA, "Tuya", listOf("clientId" to "Client ID", "clientSecret" to "Client Secret", "deviceId" to "Device ID")),
        Triple(Group.HUB, "Hub", listOf("ipv4" to "IPv4 Hub", "apiKey" to "API key")),
        Triple(Group.HTTPS, "HTTPS", listOf("host" to "Hostname hoặc IPv4", "port" to "Cổng HTTPS (mặc định 443)", "pin" to "SHA-256 fingerprint")),
        Triple(Group.WOL, "WOL", listOf("mac" to "MAC", "ip" to "IP đích")),
        Triple(Group.LABELS, "Tên ổ cắm", TuyaChannel.ALL.map { it.code to it.label })
    )
    groups.forEach { (group, title, inputs) ->
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (group in localGroups) Text("Đã có trên máy – để trống để giữ")
        else Text("Chưa có trên máy – có thể cấu hình sau")
        inputs.forEach { (key, label) ->
            VaultInput(label, value(key), { fields[key] = it },
                secret = group == Group.TUYA || key == "apiKey" || key == "pin")
        }
    }
    VaultInput("Mật khẩu chủ mới", password, { password = it }, secret = true)
    VaultInput("Xác nhận mật khẩu chủ", confirm, { confirm = it }, secret = true)
    (error ?: setupError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        if (validate()) {
            if (controller.replacingCloud) showOverwrite = true else submit()
        }
    }) { Text("Lưu bản sao mã hóa") }
    SkipButton(controller)
    if (showOverwrite) AlertDialog(
        onDismissRequest = { showOverwrite = false },
        title = { Text("Ghi đè bản sao cloud?") },
        text = { Text("Bản sao cũ sẽ được thay thế bằng cấu hình trên máy và dữ liệu vừa nhập. Dữ liệu chỉ có trong bản sao cũ sẽ mất.") },
        confirmButton = { TextButton(onClick = { showOverwrite = false; submit() }) { Text("Xác nhận ghi đè") } },
        dismissButton = { TextButton(onClick = { showOverwrite = false }) { Text("Hủy") } }
    )
}

@Composable
private fun VaultInput(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None
    )
}
