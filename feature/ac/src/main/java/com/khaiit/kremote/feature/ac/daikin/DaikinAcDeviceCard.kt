package com.khaiit.kremote.feature.ac.daikin

import androidx.compose.ui.res.stringResource
import com.khaiit.kremote.core.ui.text.asString
import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import com.khaiit.kremote.core.ui.R as CoreUiR
import com.khaiit.kremote.core.ui.components.DeviceCard
import com.khaiit.kremote.core.ui.components.PowerIconButton
import com.khaiit.kremote.core.ui.components.PowerTone
import com.khaiit.kremote.core.ui.components.TemperatureStepper
import com.khaiit.kremote.core.ui.components.PressFeedbackButton as Button
import androidx.annotation.StringRes
import com.khaiit.kremote.feature.ac.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Daikin AC card layout:
 * header + power, centered temperature controls, state summary, and
 * grouped mode/fan/swing controls.
 *
 * The IR commands are unchanged; this file only rearranges the UI.
 */
@Composable
fun DaikinAcDeviceCard(
    controller: DaikinAcController,
    modifier: Modifier = Modifier
) {
    var state by remember(controller) { mutableStateOf(controller.state) }
    var statusText by remember(controller) { mutableStateOf<UiText?>(null) }

    val execute: (() -> DaikinAcState) -> Unit = { action ->
        runCatching(action)
            .onSuccess { updated ->
                state = updated
                statusText = null
            }
            .onFailure { error ->
                statusText = error.message?.let(UiText::Raw) ?: uiText(CoreUiR.string.common_ir_command_failed)
            }
    }

    DeviceCard(modifier) {
        // Same header and power-button semantics as Toshiba.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.daikin_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (controller.isIrAvailable) stringResource(R.string.daikin_ir_ready) else stringResource(CoreUiR.string.common_ir_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PowerIconButton(
                onClick = { execute(controller::togglePower) },
                enabled = controller.isIrAvailable,
                contentDescription = if (state.power) stringResource(R.string.daikin_power_off) else stringResource(R.string.daikin_power_on),
                tone = if (state.power) PowerTone.Danger else PowerTone.Primary
            )
        }

        // Align the temperature +/- controls with the Toshiba card.
        TemperatureStepper(
            temperatureC = state.temperatureC,
            active = state.power,
            canDecrease = controller.isIrAvailable && state.power &&
                state.temperatureC > DaikinAcState.MIN_TEMPERATURE_C,
            canIncrease = controller.isIrAvailable && state.power &&
                state.temperatureC < DaikinAcState.MAX_TEMPERATURE_C,
            onDecrease = { execute(controller::decreaseTemperature) },
            onIncrease = { execute(controller::increaseTemperature) }
        )

        Text(
            text = stringResource(
                R.string.daikin_summary,
                stringResource(state.mode.displayName()),
                stringResource(state.fanSpeed.displayName()),
                stringResource(if (state.swing) R.string.daikin_swing_on else R.string.daikin_swing_off)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Daikin cycles through modes instead of selecting only COOL.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { execute(controller::cycleMode) },
                enabled = controller.isIrAvailable && state.power,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.daikin_mode)) }
            Button(
                onClick = { execute(controller::cycleFanSpeed) },
                enabled = controller.isIrAvailable && state.power,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.daikin_speed)) }
            Button(
                onClick = { execute(controller::toggleSwing) },
                enabled = controller.isIrAvailable && state.power,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
            ) { Text(stringResource(CoreUiR.string.common_swing)) }
        }

        statusText?.let { message ->
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = message.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@StringRes
private fun DaikinMode.displayName(): Int = when (this) {
    DaikinMode.AUTO -> R.string.daikin_mode_auto
    DaikinMode.COOL -> R.string.daikin_mode_cool
    DaikinMode.DRY -> R.string.daikin_mode_dry
    DaikinMode.FAN -> R.string.daikin_mode_fan
    DaikinMode.HEAT -> R.string.daikin_mode_heat
}

@StringRes
private fun DaikinFanSpeed.displayName(): Int = when (this) {
    DaikinFanSpeed.LOW -> R.string.daikin_fan_low
    DaikinFanSpeed.HIGH -> R.string.daikin_fan_high
}
