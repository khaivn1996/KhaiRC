package com.khaiit.kremote

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import com.khaiit.kremote.core.ui.R as CoreUiR
import com.khaiit.kremote.core.ui.components.DeviceCard
import com.khaiit.kremote.core.ui.components.PowerIconButton
import com.khaiit.kremote.core.ui.components.PowerTone
import com.khaiit.kremote.core.ui.theme.KhaiRCDimens
import com.khaiit.kremote.core.network.LOCAL_NETWORK_PERMISSION
import com.khaiit.kremote.core.network.hasLocalNetworkPermission
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
        if (!context.hasLocalNetworkPermission()) {
            permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        } else {
            controller.refresh()
        }
    }

    DeviceCard(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.milife_title), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.milife_subtitle), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showSettings = true }, enabled = !state.busy,
                modifier = Modifier.size(KhaiRCDimens.HeaderIconButtonSize)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.milife_configure))
            }
            IconButton(onClick = refresh, enabled = !state.busy && state.configured,
                modifier = Modifier.size(KhaiRCDimens.HeaderIconButtonSize)) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.milife_refresh))
            }
        }
        Text(
            when (state.power) {
                true -> stringResource(CoreUiR.string.common_state_on)
                false -> stringResource(CoreUiR.string.common_state_off)
                null -> stringResource(R.string.milife_unverified)
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (state.power == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.secondary
        )
        Text(state.message.asString(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PowerIconButton(
                onClick = { controller.setPower(true) },
                contentDescription = stringResource(CoreUiR.string.common_on),
                tone = PowerTone.Primary,
                enabled = !state.busy && state.power == false,
                modifier = Modifier.weight(1f),
                showLabel = true
            )
            PowerIconButton(
                onClick = { controller.setPower(false) },
                contentDescription = stringResource(CoreUiR.string.common_off),
                tone = PowerTone.Danger,
                enabled = !state.busy && state.power == true,
                modifier = Modifier.weight(1f),
                showLabel = true
            )
        }
    }

    if (showSettings) {
        var address by remember { mutableStateOf(state.address) }
        // Secret is only held in this dialog's ephemeral state; never restored or displayed.
        var newKey by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<UiText?>(null) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.milife_settings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.milife_settings_intro),
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = address, onValueChange = { address = it; error = null },
                        label = { Text(stringResource(R.string.milife_ip_label)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newKey, onValueChange = { newKey = it; error = null },
                        label = { Text(stringResource(R.string.milife_key_label)) },
                        supportingText = { Text(if (state.configured) stringResource(R.string.milife_keep_key) else stringResource(R.string.milife_enter_key)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (controller.saveConfiguration(address, newKey)) {
                        newKey = ""
                        showSettings = false
                    } else {
                        error = uiText(R.string.milife_invalid_input)
                    }
                }) { Text(stringResource(CoreUiR.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { newKey = ""; showSettings = false }) { Text(stringResource(CoreUiR.string.common_cancel)) }
            }
        )
    }
}
