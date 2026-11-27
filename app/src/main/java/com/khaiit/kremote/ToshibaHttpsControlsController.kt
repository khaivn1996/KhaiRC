package com.khaiit.kremote

import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.khaiit.kremote.core.network.HubHttpsEndpoint
import com.khaiit.kremote.core.network.ToshibaHttpsAction
import com.khaiit.kremote.core.network.ToshibaHttpsControlResult
import com.khaiit.kremote.core.network.ToshibaHttpsControlsApi
import com.khaiit.kremote.core.network.ToshibaHttpsFan
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcState
import com.khaiit.kremote.feature.ac.toshiba.ToshibaAcStateStore
import com.khaiit.kremote.feature.ac.toshiba.ToshibaFanSpeed
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** AC state is a local estimate, never a verified appliance reading. */
data class ToshibaHttpsUiState(
    val model: ToshibaAcState,
    val busy: Boolean = false,
    val needsManualCheck: Boolean = false,
    val message: UiText = uiText(R.string.toshiba_https_initial)
)

class ToshibaHttpsControlsController(
    private val api: ToshibaHttpsControlsApi,
    private val hubStore: HubConfigStore,
    private val endpointProvider: () -> HubHttpsEndpoint?,
    private val stateStore: ToshibaAcStateStore,
    private val networkAllowed: () -> Boolean
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = AtomicBoolean(false)
    var state by mutableStateOf(ToshibaHttpsUiState(model = stateStore.load()))
        private set

    fun togglePower() = withModel { model ->
        val updated = model.copy(power = !model.power)
        sendState(updated)
    }

    fun chooseCool() = withModel { model -> sendState(model.copy(power = true)) }

    fun increaseTemperature() = withModel { model ->
        if (!model.power || model.temperatureC >= ToshibaAcState.MAX_TEMPERATURE_C) return@withModel
        sendState(model.copy(temperatureC = model.temperatureC + 1))
    }

    fun decreaseTemperature() = withModel { model ->
        if (!model.power || model.temperatureC <= ToshibaAcState.MIN_TEMPERATURE_C) return@withModel
        sendState(model.copy(temperatureC = model.temperatureC - 1))
    }

    fun setFanSpeed(speed: ToshibaFanSpeed) = withModel { model ->
        if (!model.power) return@withModel
        sendState(model.copy(fanSpeed = speed))
    }

    fun fix() = withModel { model ->
        if (model.power) submit(ToshibaHttpsAction.Fix, null)
    }

    fun toggleSwing() = withModel { model ->
        if (!model.power) return@withModel
        val updated = model.copy(swing = !model.swing)
        submit(if (updated.swing) ToshibaHttpsAction.SwingOn else ToshibaHttpsAction.SwingOff, updated)
    }

    /** Explicit manual acknowledgement after an ambiguous send; never automatic. */
    fun confirmObserved() {
        if (state.busy || !state.needsManualCheck) return
        state = state.copy(needsManualCheck = false,
            message = uiText(R.string.toshiba_https_observed))
    }

    private inline fun withModel(action: (ToshibaAcState) -> Unit) {
        if (state.busy || state.needsManualCheck) return
        action(state.model)
    }

    private fun sendState(next: ToshibaAcState) {
        val fan = when (next.fanSpeed) {
            ToshibaFanSpeed.AUTO -> ToshibaHttpsFan.AUTO
            ToshibaFanSpeed.LEVEL_1 -> ToshibaHttpsFan.LEVEL_1
            ToshibaFanSpeed.LEVEL_2 -> ToshibaHttpsFan.LEVEL_2
            ToshibaFanSpeed.LEVEL_3 -> ToshibaHttpsFan.LEVEL_3
            ToshibaFanSpeed.LEVEL_4 -> ToshibaHttpsFan.LEVEL_4
            ToshibaFanSpeed.LEVEL_5 -> ToshibaHttpsFan.LEVEL_5
        }
        submit(ToshibaHttpsAction.State(next.power, next.temperatureC, fan), next)
    }

    /** Only successful 200/validated acknowledgement updates locally stored state. */
    private fun submit(command: ToshibaHttpsAction, newState: ToshibaAcState?) {
        if (!gate.compareAndSet(false, true)) return
        state = state.copy(busy = true, message = uiText(R.string.toshiba_https_sending))
        scope.launch {
            try {
                if (!networkAllowed()) {
                    state = state.copy(message = uiText(R.string.toshiba_https_lan_permission))
                    return@launch
                }
                val endpoint = endpointProvider()
                if (endpoint == null) {
                    state = state.copy(message = uiText(R.string.toshiba_https_https_setup_needed))
                    return@launch
                }
                val config = hubStore.load()?.config
                if (config == null) {
                    state = state.copy(message = uiText(R.string.toshiba_https_missing_key))
                    return@launch
                }
                when (val result = api.send(config, endpoint, command)) {
                    ToshibaHttpsControlResult.Sent -> {
                        if (newState != null) stateStore.save(newState)
                        state = state.copy(
                            model = newState ?: state.model,
                            message = uiText(R.string.toshiba_https_sent, command.action))
                    }
                    is ToshibaHttpsControlResult.Rejected -> {
                        state = state.copy(message = when (result.status) {
                            401 -> uiText(R.string.toshiba_https_unauthorized)
                            422 -> uiText(R.string.toshiba_https_invalid_command)
                            503 -> uiText(R.string.toshiba_https_driver_unavailable)
                            else -> uiText(R.string.toshiba_https_rejected, result.status.toString())
                        })
                    }
                    is ToshibaHttpsControlResult.Uncertain -> {
                        state = state.copy(needsManualCheck = true,
                            message = uiText(R.string.toshiba_https_uncertain, result.reason.toString()))
                    }
                }
            } catch (cancelled: CancellationException) {
                state = state.copy(needsManualCheck = true,
                    message = uiText(R.string.toshiba_https_cancelled))
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(needsManualCheck = true,
                    message = uiText(R.string.toshiba_https_unknown_result))
            } finally {
                state = state.copy(busy = false)
                gate.set(false)
            }
        }
    }

    override fun close() { scope.cancel() }
}
