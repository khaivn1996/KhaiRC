package com.khaiit.kremote

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.R as CoreUiR
import com.khaiit.kremote.core.ui.components.DeviceCard
import com.khaiit.kremote.core.ui.components.TemperatureStepper
import com.khaiit.kremote.core.ui.components.PowerIconButton
import com.khaiit.kremote.core.ui.components.PowerTone
import com.khaiit.kremote.core.ui.components.PressFeedbackButton as Button
import com.khaiit.kremote.core.ui.components.PressFeedbackOutlinedButton as OutlinedButton
import com.khaiit.kremote.core.network.LOCAL_NETWORK_PERMISSION
import com.khaiit.kremote.core.network.hasLocalNetworkPermission
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcState
import com.khaiit.kremote.feature.ac.toshiba.ToshibaFanSpeed

/** Replaces the phone-IR Toshiba card and Stage 2B standalone FIX card. */
@Composable
fun ToshibaHttpsControlsCard(controller: ToshibaHttpsControlsController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ui = controller.state
    val model = ui.model
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Do not automatically transmit a command after a permission dialog.
        if (!granted) { /* No permission: next manual press will still be blocked. */ }
    }
    val canSend = !ui.busy && !ui.needsManualCheck
    val send: (() -> Unit) -> Unit = { command ->
        if (!context.hasLocalNetworkPermission()) {
            permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        } else command()
    }
    DeviceCard(modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.toshiba_https_title), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.toshiba_https_subtitle), style = MaterialTheme.typography.bodySmall)
            }
            val powerLabel = if (model.power) stringResource(CoreUiR.string.common_off) else stringResource(CoreUiR.string.common_on)
            PowerIconButton(
                onClick = { send(controller::togglePower) },
                contentDescription = powerLabel,
                tone = if (model.power) PowerTone.Danger else PowerTone.Primary,
                enabled = canSend,
                showLabel = true
            )
        }
        Text(stringResource(R.string.toshiba_https_estimate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TemperatureStepper(
            temperatureC = model.temperatureC,
            active = model.power,
            canDecrease = canSend && model.power && model.temperatureC > ToshibaAcState.MIN_TEMPERATURE_C,
            canIncrease = canSend && model.power && model.temperatureC < ToshibaAcState.MAX_TEMPERATURE_C,
            onDecrease = { send(controller::decreaseTemperature) },
            onIncrease = { send(controller::increaseTemperature) }
        )
        Button(onClick = { send(controller::chooseCool) },
            enabled = canSend && model.power, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(CoreUiR.string.common_cool))
        }
        Text(stringResource(CoreUiR.string.common_fan_speed), fontWeight = FontWeight.SemiBold)
        ToshibaFanSpeed.entries.chunked(3).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                group.forEach { fan ->
                    val selected = model.fanSpeed == fan
                    val click = { send { controller.setFanSpeed(fan) } }
                    if (selected) {
                        Button(onClick = click, enabled = canSend && model.power,
                            modifier = Modifier.weight(1f)) { Text(fan.label) }
                    } else {
                        OutlinedButton(onClick = click, enabled = canSend && model.power,
                            modifier = Modifier.weight(1f)) { Text(fan.label) }
                    }
                }
            }
        }
        Text(stringResource(CoreUiR.string.common_wind_direction), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { send(controller::fix) },
                enabled = canSend && model.power, modifier = Modifier.weight(1f)) {
                Text(stringResource(CoreUiR.string.common_fix))
            }
            OutlinedButton(onClick = { send(controller::toggleSwing) },
                enabled = canSend && model.power, modifier = Modifier.weight(1f)) {
                Text(if (model.swing) stringResource(R.string.toshiba_https_swing_off) else stringResource(R.string.toshiba_https_swing_on))
            }
        }
        Text(ui.message.asString(), style = MaterialTheme.typography.bodySmall,
            color = if (ui.needsManualCheck) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.needsManualCheck) {
            OutlinedButton(
                onClick = controller::confirmObserved,
                enabled = !ui.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.toshiba_https_confirm_observed)) }
        }
    }
}
