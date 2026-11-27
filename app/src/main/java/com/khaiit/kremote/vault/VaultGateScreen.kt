package com.khaiit.kremote.vault

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import com.khaiit.kremote.core.ui.R as CoreUiR
import com.khaiit.kremote.R
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
            Text(stringResource(R.string.vault_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.vault_intro))
            when (val state = controller.state) {
                VaultUiState.Ready -> Unit
                VaultUiState.Checking -> CircularProgressIndicator()
                VaultUiState.NeedSignIn -> {
                    Text(stringResource(R.string.vault_sign_in_hint))
                    Button(onClick = { controller.signIn(activity) }) { Text(stringResource(R.string.vault_sign_in)) }
                    SkipButton(controller)
                }
                is VaultUiState.NeedPassword -> {
                    var password by remember { mutableStateOf("") }
                    Text(stringResource(R.string.vault_found))
                    VaultInput(stringResource(R.string.vault_password), password, { password = it }, secret = true)
                    state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        val chars = password.toCharArray()
                        password = ""
                        controller.unlock(chars)
                    }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.vault_open)) }
                    TextButton(onClick = controller::forgotPassword) { Text(stringResource(R.string.vault_forgot)) }
                    SkipButton(controller)
                }
                is VaultUiState.NeedSetup -> VaultSetupForm(controller, state.localGroups, state.error, fields)
                is VaultUiState.Working -> {
                    CircularProgressIndicator()
                    Text(state.msg.asString())
                }
                is VaultUiState.Error -> {
                    Text(state.msg.asString(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = controller::start) { Text(stringResource(R.string.vault_retry)) }
                    if (state.canSkip) SkipButton(controller)
                }
            }
        }
    }
}

@Composable
private fun SkipButton(controller: VaultController) {
    TextButton(onClick = controller::skipThisSession) { Text(stringResource(R.string.vault_skip)) }
}

@Composable
private fun VaultSetupForm(
    controller: VaultController,
    localGroups: Set<Group>,
    setupError: UiText?,
    fields: SnapshotStateMap<String, String>
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showOverwrite by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
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
    Text(if (controller.replacingCloud) stringResource(R.string.vault_replace) else stringResource(R.string.vault_missing))
    if (controller.replacingCloud) {
        Text(stringResource(R.string.vault_replace_hint))
    }
    fun validate(): Boolean {
        error = if (password.length < VaultCrypto.MIN_PASSWORD || password != confirm)
            uiText(R.string.vault_error_weak_password) else null
        return error == null
    }
    val groups = listOf(
        Triple(Group.TUYA, stringResource(R.string.vault_group_tuya), listOf("clientId" to stringResource(CoreUiR.string.common_client_id), "clientSecret" to stringResource(CoreUiR.string.common_client_secret), "deviceId" to stringResource(R.string.vault_device_id))),
        Triple(Group.HUB, stringResource(R.string.vault_group_hub), listOf("ipv4" to stringResource(R.string.vault_ipv4), "apiKey" to stringResource(R.string.vault_api_key))),
        Triple(Group.HTTPS, stringResource(R.string.vault_group_https), listOf("host" to stringResource(R.string.vault_host), "port" to stringResource(R.string.vault_port), "pin" to stringResource(R.string.vault_fingerprint))),
        Triple(Group.WOL, stringResource(R.string.vault_group_wol), listOf("mac" to stringResource(R.string.vault_mac), "ip" to stringResource(R.string.vault_target_ip))),
        Triple(Group.LABELS, stringResource(R.string.vault_labels_title), TuyaChannel.ALL.map { it.code to it.label })
    )
    groups.forEach { (group, title, inputs) ->
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (group in localGroups) Text(stringResource(R.string.vault_existing_group))
        else Text(stringResource(R.string.vault_missing_group))
        inputs.forEach { (key, label) ->
            VaultInput(label, value(key), { fields[key] = it },
                secret = group == Group.TUYA || key == "apiKey" || key == "pin")
        }
    }
    VaultInput(stringResource(R.string.vault_new_password), password, { password = it }, secret = true)
    VaultInput(stringResource(R.string.vault_confirm_password), confirm, { confirm = it }, secret = true)
    (error ?: setupError)?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        if (validate()) {
            if (controller.replacingCloud) showOverwrite = true else submit()
        }
    }) { Text(stringResource(R.string.vault_save_encrypted)) }
    SkipButton(controller)
    if (showOverwrite) AlertDialog(
        onDismissRequest = { showOverwrite = false },
        title = { Text(stringResource(R.string.vault_overwrite_title)) },
        text = { Text(stringResource(R.string.vault_overwrite_warning)) },
        confirmButton = { TextButton(onClick = { showOverwrite = false; submit() }) { Text(stringResource(R.string.vault_overwrite)) } },
        dismissButton = { TextButton(onClick = { showOverwrite = false }) { Text(stringResource(R.string.vault_cancel)) } }
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
