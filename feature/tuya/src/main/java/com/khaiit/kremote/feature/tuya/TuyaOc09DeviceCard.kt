package com.khaiit.kremote.feature.tuya

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import com.khaiit.kremote.core.ui.R as CoreUiR
import com.khaiit.kremote.core.ui.components.DeviceCard
import com.khaiit.kremote.core.ui.components.PowerIconButton
import com.khaiit.kremote.core.ui.components.PowerTone
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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

    DeviceCard(modifier) {
        // Common actions live in the header; names are edited separately from secret settings.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.tuya_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !state.configured -> stringResource(R.string.tuya_not_configured)
                        state.busy -> stringResource(R.string.tuya_busy)
                        state.verified -> stringResource(R.string.tuya_verified)
                        else -> stringResource(R.string.tuya_unverified)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.configured && state.verified && !state.busy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showRename = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.tuya_rename))
            }
            IconButton(
                onClick = { showSettings = true },
                enabled = !state.busy,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tuya_settings))
            }
            IconButton(
                onClick = { scope.launch { controller.refresh() } },
                enabled = state.configured && !state.busy,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.tuya_refresh))
            }
        }

        // Master power: one Tuya POST with six commands, confirmed by a fresh status GET.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.tuya_master), fontWeight = FontWeight.SemiBold)
                    Text(
                        when (state.masterPower) {
                            true -> stringResource(R.string.tuya_master_any_on)
                            false -> stringResource(R.string.tuya_master_all_off)
                            null -> stringResource(R.string.tuya_master_unverified)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PowerIconButton(
                    tone = state.masterPower.powerTone(),
                    size = 46.dp,
                    enabled = !state.busy && state.masterPower != null,
                    contentDescription = when (state.masterPower) {
                        true -> stringResource(R.string.tuya_master_power_off)
                        false -> stringResource(R.string.tuya_master_power_on)
                        null -> stringResource(R.string.tuya_master_power_unknown)
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
                        shape = MaterialTheme.shapes.large,
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
                                        true -> stringResource(CoreUiR.string.common_state_on)
                                        false -> stringResource(CoreUiR.string.common_state_off)
                                        null -> stringResource(R.string.tuya_channel_unknown)
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                PowerIconButton(
                                    tone = power.powerTone(),
                                    size = 46.dp,
                                    enabled = !state.busy && power != null,
                                    contentDescription = when (power) {
                                        true -> stringResource(R.string.tuya_channel_power_off, name)
                                        false -> stringResource(R.string.tuya_channel_power_on, name)
                                        null -> stringResource(R.string.tuya_channel_power_unknown, name)
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
            Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

private fun Boolean?.powerTone(): PowerTone = when (this) {
    true -> PowerTone.Danger
    false -> PowerTone.Primary
    null -> PowerTone.Neutral
}

@Composable
private fun TuyaRenameDialog(
    current: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    var edited by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<UiText?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tuya_rename)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.tuya_rename_intro),
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
                    Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { onSave(edited) }
                    .onSuccess { onDismiss() }
                    .onFailure { error = it.message?.let(UiText::Raw) ?: uiText(R.string.tuya_rename_failed) }
            }) { Text(stringResource(CoreUiR.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiR.string.common_cancel)) } }
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
        title = { Text(stringResource(R.string.tuya_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.tuya_settings_intro),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text(stringResource(CoreUiR.string.common_client_id)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(stringResource(CoreUiR.string.common_client_secret)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text(stringResource(R.string.tuya_device_id)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
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
            ) { Text(stringResource(CoreUiR.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(CoreUiR.string.common_cancel)) }
        }
    )
}
