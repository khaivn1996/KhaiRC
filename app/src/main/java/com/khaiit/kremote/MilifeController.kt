package com.khaiit.kremote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.khaiit.kremote.core.network.HubConfig
import com.khaiit.kremote.core.network.HubEndpoint
import com.khaiit.kremote.core.network.MilifeReadApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Power == null means UNVERIFIED, never the last-known relay state. */
data class MilifeUiState(
    val busy: Boolean = true,
    val configured: Boolean = false,
    val address: String = "",
    val power: Boolean? = null,
    val message: String = "Đang đọc cấu hình Hub…"
)

/** Shares the existing encrypted HubSettingsStore; never stores a key in UI state. */
class MilifeController(
    private val api: MilifeReadApi,
    private val store: HubConfigStore,
    private val networkAllowed: () -> Boolean
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settings: HubSettings? = null
    var state by mutableStateOf(MilifeUiState())
        private set

    init {
        scope.launch {
            try {
                settings = store.load()
                val current = settings
                state = MilifeUiState(
                    busy = false,
                    configured = current != null,
                    address = current?.config?.endpoint?.ipv4.orEmpty(),
                    message = if (current == null) "Chưa cấu hình ESP32 Hub" else "Chưa xác minh trạng thái"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = MilifeUiState(busy = false,
                    message = "Không đọc được cấu hình Hub đã mã hóa. Hãy nhập lại cấu hình.")
            }
            if (settings != null && networkAllowed()) refresh()
        }
    }

    /** A single GET. No command vocabulary, writes, retry or cloud fallback. */
    fun refresh() {
        if (state.busy) return
        val config = settings?.config
        if (config == null) {
            state = state.copy(power = null, message = "Chưa cấu hình ESP32 Hub")
            return
        }
        if (!networkAllowed()) {
            permissionDenied()
            return
        }
        state = state.copy(busy = true, power = null, message = "Đang đọc trạng thái qua HTTPS…")
        scope.launch {
            try {
                val result = api.readMilife(config)
                state = result.fold(
                    onSuccess = { power -> state.copy(power = power, message = "Trạng thái đã xác minh từ Hub") },
                    onFailure = { error ->
                        val code = (
                                error as? com.khaiit.kremote.core.network.HubClientException
                                )?.code

                        val detail = when (code) {
                            "https_not_configured" -> "Chưa cấu hình HTTPS Hub; vào card HTTPS Hub để thiết lập."
                            "tls_validation_failed" -> "Chứng chỉ TLS không hợp lệ; kiểm tra fingerprint."
                            "redirect_blocked" -> "HTTPS trả chuyển hướng; đã chặn."
                            "http_401" -> "API key bị Hub từ chối (401)"
                            "http_404" -> "Không tìm thấy API Milife (404)"
                            "http_502" -> "Hub không đọc được Milife (502)"
                            "http_504" -> "Hub chờ Milife quá lâu (504)"
                            "timeout" -> "Kết nối Hub quá thời gian chờ"
                            "network_permission" -> "Ứng dụng không có quyền truy cập mạng"
                            "network_error" -> "Lỗi kết nối mạng hoặc đường VPN"
                            "invalid_response" -> "Hub trả dữ liệu không hợp lệ"
                            else -> "Không kết nối · Lỗi chưa xác định"
                        }

                        state.copy(power = null, message = detail)
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(power = null, message = "Không kết nối · Yêu cầu không thành công")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    /** Blank replacementKey preserves the existing Hub key and IR routing settings. */
    fun saveConfiguration(address: String, replacementKey: String): Boolean {
        if (state.busy) return false
        val candidate = try {
            val previous = settings
            HubSettings(
                config = HubConfig(
                    HubEndpoint(address.trim()),
                    replacementKey.takeIf { it.isNotEmpty() } ?: previous?.config?.apiKey
                        ?: throw IllegalArgumentException("missing key")
                ),
                fanTransport = previous?.fanTransport ?: HubTransport.PHONE,
                fixTransport = previous?.fixTransport ?: HubTransport.PHONE
            )
        } catch (_: IllegalArgumentException) {
            state = state.copy(power = null,
                message = "IPv4 phải là địa chỉ LAN; API key gồm đúng 64 ký tự ASCII.")
            return false
        }
        state = state.copy(busy = true, power = null, message = "Đang lưu cấu hình Hub…")
        scope.launch {
            var saved = false
            try {
                store.save(candidate)
                settings = candidate
                saved = true
                state = state.copy(configured = true, address = candidate.config.endpoint.ipv4,
                    power = null, message = "Đã lưu cấu hình Hub · Chưa xác minh trạng thái")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(power = null, message = "Không lưu được cấu hình Hub; cấu hình cũ vẫn giữ nguyên")
            } finally {
                state = state.copy(busy = false)
            }
            if (saved && networkAllowed()) refresh()
        }
        return true
    }

    fun permissionDenied() {
        if (!state.busy) state = state.copy(power = null, message = "Cần quyền mạng LAN để đọc Milife")
    }

    override fun close() { scope.cancel() }
}
