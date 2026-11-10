package com.khaiit.kremote

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
    val message: String = "Toshiba qua HTTPS · Chỉ phát IR khi nhấn nút"
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
            message = "Đã xác nhận kiểm tra thực tế. Trạng thái trong app vẫn chỉ là ước lượng.")
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
        state = state.copy(busy = true, message = "Đang gửi một lệnh Toshiba qua HTTPS…")
        scope.launch {
            try {
                if (!networkAllowed()) {
                    state = state.copy(message = "Chưa có quyền truy cập mạng LAN.")
                    return@launch
                }
                val endpoint = endpointProvider()
                if (endpoint == null) {
                    state = state.copy(message = "Chưa cấu hình HTTPS Hub hoặc fingerprint.")
                    return@launch
                }
                val config = hubStore.load()?.config
                if (config == null) {
                    state = state.copy(message = "Chưa có API key của Hub.")
                    return@launch
                }
                when (val result = api.send(config, endpoint, command)) {
                    ToshibaHttpsControlResult.Sent -> {
                        if (newState != null) stateStore.save(newState)
                        state = state.copy(
                            model = newState ?: state.model,
                            message = "Đã phát ${command.action} một lần qua HTTPS; máy lạnh chưa phản hồi trạng thái.")
                    }
                    is ToshibaHttpsControlResult.Rejected -> {
                        state = state.copy(message = when (result.status) {
                            401 -> "Hub từ chối API key (401), không phát IR."
                            422 -> "Hub từ chối lệnh hoặc tham số Toshiba (422)."
                            503 -> "Driver IR chưa sẵn sàng hoặc đang bận (503)."
                            else -> "Hub từ chối lệnh (${result.status})."
                        })
                    }
                    is ToshibaHttpsControlResult.Uncertain -> {
                        state = state.copy(needsManualCheck = true,
                            message = "Chưa rõ lệnh đã phát hay chưa (${result.reason}). " +
                                "Hãy kiểm tra máy lạnh trước khi gửi thêm.")
                    }
                }
            } catch (cancelled: CancellationException) {
                state = state.copy(needsManualCheck = true,
                    message = "Lệnh bị hủy; chưa rõ đã phát chưa. Kiểm tra máy lạnh trước khi thao tác tiếp.")
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(needsManualCheck = true,
                    message = "Không xác định kết quả lệnh. Kiểm tra máy lạnh trước khi thao tác tiếp.")
            } finally {
                state = state.copy(busy = false)
                gate.set(false)
            }
        }
    }

    override fun close() { scope.cancel() }
}
