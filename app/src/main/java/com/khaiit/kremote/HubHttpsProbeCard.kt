package com.khaiit.kremote

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import com.khaiit.kremote.core.ui.R as CoreUiR
import com.khaiit.kremote.core.ui.components.DeviceCard
import com.khaiit.kremote.core.network.LOCAL_NETWORK_PERMISSION
import com.khaiit.kremote.core.network.hasLocalNetworkPermission
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import com.khaiit.kremote.core.network.HubHttpsEndpoint
import com.khaiit.kremote.core.network.HubHttpsProbe
import com.khaiit.kremote.core.network.HubHttpsProbeResult
import com.khaiit.kremote.core.network.HubEndpoint
import kotlinx.coroutines.launch

/** Pure: no saved field means "not configured"; a partial or invalid entry throws instead of looking empty. */
internal fun parseStoredHttpsEndpoint(host: String?, port: Int, pin: String?): HubHttpsEndpoint? {
    if (host == null && pin == null) return null
    return HubHttpsEndpoint(requireNotNull(host), port, requireNotNull(pin))
}

/** Contains no API key; fields are public host/port/certificate fingerprint only. */
internal class HttpsProbePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hub_https_probe_v1", Context.MODE_PRIVATE)

    fun read(): HubHttpsEndpoint? = runCatching {
        val host = prefs.getString("host", null) ?: return null
        val pin = prefs.getString("sha256", null) ?: return null
        HubHttpsEndpoint(host, prefs.getInt("port", 443), pin)
    }.getOrNull()

    /** For cloud backup: null only when nothing is saved; throws when a saved endpoint is unreadable. */
    fun readStored(): HubHttpsEndpoint? = parseStoredHttpsEndpoint(
        prefs.getString("host", null), prefs.getInt("port", 443), prefs.getString("sha256", null)
    )

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
    var message by remember { mutableStateOf<UiText>(uiText(R.string.hub_probe_initial)) }

    val runProbe: (HubHttpsEndpoint) -> Unit = { endpoint ->
        busy = true
        message = uiText(R.string.hub_probe_checking)
        scope.launch {
            try {
                message = when (val result = probe.check(endpoint)) {
                    is HubHttpsProbeResult.Verified ->
                        uiText(
                            R.string.hub_probe_verified,
                            result.uptimeSeconds.toString(),
                            result.freeHeap.toString()
                        )
                    is HubHttpsProbeResult.Failed -> when (result.code) {
                        "tls_validation_failed" -> uiText(R.string.hub_probe_tls_failed)
                        "dns_error" -> uiText(R.string.hub_probe_dns_error)
                        "timeout" -> uiText(R.string.hub_probe_timeout)
                        "redirect_blocked" -> uiText(R.string.hub_probe_redirect_blocked)
                        "invalid_response" -> uiText(R.string.hub_probe_invalid_response)
                        "response_too_large" -> uiText(R.string.hub_probe_response_too_large)
                        "network_permission" -> uiText(R.string.hub_probe_network_permission)
                        else -> uiText(R.string.hub_probe_unverified, result.code)
                    }
                }
            } finally { busy = false }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) configured?.let(runProbe)
        else message = uiText(R.string.hub_probe_lan_permission)
    }

    DeviceCard(modifier, verticalSpacing = 10.dp) {
        Text(stringResource(R.string.hub_probe_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.hub_probe_subtitle),
            style = MaterialTheme.typography.bodySmall)
        Text(configured?.let { stringResource(R.string.hub_probe_target, it.host, it.port.toString()) } ?: stringResource(R.string.hub_probe_not_configured),
            style = MaterialTheme.typography.bodySmall)
        Text(message.asString(), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showConfig = true }, enabled = !busy) {
                Text(stringResource(R.string.hub_probe_configure))
            }
            OutlinedButton(onClick = {
                val endpoint = configured ?: return@OutlinedButton
                val isPrivateIp = runCatching { HubEndpoint(endpoint.host) }.isSuccess
                if (isPrivateIp && !context.hasLocalNetworkPermission()) {
                    permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
                } else {
                    runProbe(endpoint)
                }
            }, enabled = !busy && configured != null) {
                Text(if (busy) stringResource(R.string.hub_probe_checking_button) else stringResource(R.string.hub_probe_check))
            }
        }
    }

    if (showConfig) {
        var host by remember { mutableStateOf(configured?.host ?: "") }
        var port by remember { mutableStateOf((configured?.port ?: 443).toString()) }
        // The saved pin is never pre-filled or displayed; blank input keeps it (same rule as HUB_API_KEY).
        var fingerprint by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<UiText?>(null) }
        AlertDialog(
            onDismissRequest = { showConfig = false },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.hub_probe_config_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.hub_probe_config_intro),
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = host, onValueChange = { host = it; error = null },
                        label = { Text(stringResource(R.string.hub_probe_host_label)) },
                        placeholder = { Text(stringResource(R.string.hub_probe_host_placeholder)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port, onValueChange = { port = it; error = null },
                        label = { Text(stringResource(R.string.hub_probe_port_label)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fingerprint, onValueChange = { fingerprint = it; error = null },
                        label = { Text(stringResource(R.string.hub_probe_pin_label)) },
                        supportingText = {
                            Text(if (configured != null) stringResource(R.string.hub_probe_keep_pin) else stringResource(R.string.hub_probe_enter_pin))
                        },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Report the exact input that failed; do not expose the fingerprint value.
                    val validation = runCatching {
                        HubHttpsEndpoint(
                            host.trim().lowercase(java.util.Locale.ROOT),
                            port.trim().toInt(),
                            fingerprint.trim().ifEmpty { configured?.fingerprintHex.orEmpty() }
                        )
                    }
                    val endpoint = validation.getOrNull()
                    if (endpoint == null) {
                        error = when (val cause = validation.exceptionOrNull()) {
                            is NumberFormatException -> uiText(R.string.hub_probe_invalid_port)
                            is IllegalArgumentException -> cause.message?.let(UiText::Raw) ?: uiText(R.string.hub_probe_invalid_config)
                            else -> uiText(R.string.hub_probe_validation_failed)
                        }
                    } else if (!preferences.save(endpoint)) {
                        error = uiText(R.string.hub_probe_save_failed)
                    } else {
                        configured = endpoint
                        message = uiText(R.string.hub_probe_saved)
                        showConfig = false
                    }
                }) { Text(stringResource(CoreUiR.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showConfig = false }) { Text(stringResource(CoreUiR.string.common_cancel)) } }
        )
    }
}
