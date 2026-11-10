package com.khaiit.kremote

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.khaiit.kremote.core.network.HubHttpsEndpoint
import com.khaiit.kremote.core.network.HubHttpsProbe
import com.khaiit.kremote.core.network.HubHttpsProbeResult
import com.khaiit.kremote.core.network.HubEndpoint
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/** Contains no API key; fields are public host/port/certificate fingerprint only. */
internal class HttpsProbePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hub_https_probe_v1", Context.MODE_PRIVATE)

    fun read(): HubHttpsEndpoint? = runCatching {
        val host = prefs.getString("host", null) ?: return null
        val pin = prefs.getString("sha256", null) ?: return null
        HubHttpsEndpoint(host, prefs.getInt("port", 443), pin)
    }.getOrNull()

    fun save(endpoint: HubHttpsEndpoint): Boolean = prefs.edit()
        .putString("host", endpoint.host)
        .putInt("port", endpoint.port)
        .putString("sha256", endpoint.fingerprintHex)
        .commit()
}

@Composable
fun HubHttpsProbeCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context) { HttpsProbePreferences(context) }
    val probe = remember { HubHttpsProbe() }
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(preferences.read()) }
    var busy by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Chưa kiểm tra HTTPS") }

    val runProbe: (HubHttpsEndpoint) -> Unit = { endpoint ->
        busy = true
        message = "Đang kiểm tra TLS…"
        scope.launch {
            try {
                message = when (val result = probe.check(endpoint)) {
                    is HubHttpsProbeResult.Verified ->
                        "TLS hợp lệ · Hub phản hồi · Uptime ${result.uptimeSeconds}s · Heap ${result.freeHeap} B"
                    is HubHttpsProbeResult.Failed -> when (result.code) {
                        "tls_validation_failed" -> "TLS không hợp lệ: chứng chỉ, tên máy chủ hoặc ngày hết hạn"
                        "dns_error" -> "Không phân giải được tên miền"
                        "timeout" -> "Không thể kết nối trong thời gian cho phép"
                        "redirect_blocked" -> "Máy chủ chuyển hướng: đã chặn"
                        "invalid_response" -> "Phản hồi HTTPS không đúng Stage 1"
                        "response_too_large" -> "Phản hồi HTTPS quá lớn"
                        "network_permission" -> "Không có quyền kết nối mạng"
                        else -> "Chưa xác minh được Hub qua HTTPS (${result.code})"
                    }
                }
            } finally { busy = false }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) configured?.let(runProbe)
        else message = "Cần quyền mạng LAN để kiểm tra địa chỉ IP nội bộ"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("HTTPS Hub · Stage 2A", style = MaterialTheme.typography.titleLarge)
            Text("Probe TLS/health không gửi key; cấu hình này còn dùng đọc MiLife qua HTTPS.",
                style = MaterialTheme.typography.bodySmall)
            Text(configured?.let { "Đích: ${it.host}:${it.port}" } ?: "Chưa cấu hình HTTPS",
                style = MaterialTheme.typography.bodySmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showConfig = true }, enabled = !busy) {
                    Text("CẤU HÌNH")
                }
                OutlinedButton(onClick = {
                    val endpoint = configured ?: return@OutlinedButton
                    val isPrivateIp = runCatching { HubEndpoint(endpoint.host) }.isSuccess
                    if (isPrivateIp && Build.VERSION.SDK_INT >= 37 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    } else {
                        runProbe(endpoint)
                    }
                }, enabled = !busy && configured != null) {
                    Text(if (busy) "ĐANG KIỂM TRA" else "KIỂM TRA")
                }
            }
        }
    }

    if (showConfig) {
        var host by remember { mutableStateOf(configured?.host ?: "openwrt96.ddns.net") }
        var port by remember { mutableStateOf((configured?.port ?: 443).toString()) }
        var fingerprint by remember { mutableStateOf(configured?.fingerprintHex.orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showConfig = false },
            title = { Text("Cấu hình HTTPS Hub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Nhập tên DNS hoặc IPv4 LAN/VPN, không kèm https://. Dùng fingerprint SHA-256 của chứng chỉ Hub; không nhập private key.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = host, onValueChange = { host = it; error = null },
                        label = { Text("Hostname hoặc IP") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port, onValueChange = { port = it; error = null },
                        label = { Text("Cổng HTTPS") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fingerprint, onValueChange = { fingerprint = it; error = null },
                        label = { Text("SHA-256 certificate fingerprint (hex)") },
                        singleLine = false, maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Report the exact input that failed; do not expose the fingerprint value.
                    val validation = runCatching {
                        HubHttpsEndpoint(
                            host.trim().lowercase(java.util.Locale.ROOT),
                            port.trim().toInt(),
                            fingerprint.trim()
                        )
                    }
                    val endpoint = validation.getOrNull()
                    if (endpoint == null) {
                        error = when (val cause = validation.exceptionOrNull()) {
                            is NumberFormatException -> "Cổng HTTPS phải là số từ 1 đến 65535."
                            is IllegalArgumentException -> cause.message ?: "Cấu hình HTTPS không hợp lệ."
                            else -> "Không kiểm tra được cấu hình HTTPS."
                        }
                    } else if (!preferences.save(endpoint)) {
                        error = "Không thể lưu cấu hình HTTPS."
                    } else {
                        configured = endpoint
                        message = "Đã lưu · Chưa kiểm tra chứng chỉ"
                        showConfig = false
                    }
                }) { Text("LƯU") }
            },
            dismissButton = { TextButton(onClick = { showConfig = false }) { Text("HỦY") } }
        )
    }
}
