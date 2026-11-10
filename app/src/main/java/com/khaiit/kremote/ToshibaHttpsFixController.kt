package com.khaiit.kremote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.khaiit.kremote.core.network.HubHttpsEndpoint
import com.khaiit.kremote.core.network.ToshibaFixResult
import com.khaiit.kremote.core.network.ToshibaHttpsFixApi
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** No command persistence, automatic retries, UI power-state assumptions or HTTP downgrade. */
class ToshibaHttpsFixController(
    private val api: ToshibaHttpsFixApi,
    private val store: HubConfigStore,
    private val endpointProvider: () -> HubHttpsEndpoint?,
    private val networkAllowed: () -> Boolean
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = AtomicBoolean(false)
    var state by mutableStateOf(FixUiState())
        private set

    fun sendOnce() {
        if (!gate.compareAndSet(false, true)) return
        state = FixUiState(busy = true, message = "Đang gửi một lệnh FIX qua HTTPS…")
        scope.launch {
            try {
                if (!networkAllowed()) {
                    state = FixUiState(message = "Cần quyền truy cập mạng LAN.")
                    return@launch
                }
                val endpoint = endpointProvider()
                if (endpoint == null) {
                    state = FixUiState(message = "Chưa có cấu hình HTTPS và fingerprint hợp lệ.")
                    return@launch
                }
                val config = store.load()?.config
                if (config == null) {
                    state = FixUiState(message = "Chưa có API key Hub; vào cấu hình Hub để thiết lập.")
                    return@launch
                }
                state = FixUiState(busy = true, message = "Đang chờ Hub phản hồi…")
                state = when (val result = api.sendFix(config, endpoint)) {
                    ToshibaFixResult.Sent -> FixUiState(
                        message = "Đã gửi Toshiba FIX một lần. Chưa xác minh vị trí cánh đảo gió."
                    )
                    is ToshibaFixResult.Rejected -> FixUiState(
                        message = when (result.status) {
                            401 -> "Hub từ chối API key (401). Không phát IR."
                            422 -> "Hub không cho phép lệnh Toshiba FIX (422)."
                            503 -> "Hub IR chưa sẵn sàng hoặc đang bận (503)."
                            else -> "Hub từ chối lệnh (${result.status})."
                        }
                    )
                    is ToshibaFixResult.Uncertain -> FixUiState(
                        message = "Chưa xác định FIX đã phát hay chưa (${result.code}). " +
                            "Quan sát máy lạnh trước khi nhấn lại; không tự gửi lại."
                    )
                }
            } catch (cancelled: CancellationException) {
                state = FixUiState(message = "Yêu cầu bị hủy; chưa rõ FIX đã phát hay chưa. Không tự gửi lại.")
                throw cancelled
            } catch (_: Exception) {
                state = FixUiState(message = "Yêu cầu không chắc đã hoàn tất. Quan sát máy lạnh trước khi nhấn lại.")
            } finally {
                state = state.copy(busy = false)
                gate.set(false)
            }
        }
    }

    fun permissionDenied() {
        state = FixUiState(message = "Cần quyền mạng LAN để gửi FIX qua HTTPS.")
    }

    override fun close() { scope.cancel() }
}

data class FixUiState(
    val busy: Boolean = false,
    val message: String = "Chỉ phát Toshiba FIX khi bạn nhấn nút; không tự động gửi lệnh."
)
