package com.khaiit.kremote

import androidx.lifecycle.ViewModel
import com.khaiit.kremote.core.network.HubApi
import com.khaiit.kremote.core.network.HubCommand
import com.khaiit.kremote.core.network.HubConfig
import com.khaiit.kremote.core.network.HubEndpoint
import com.khaiit.kremote.core.network.HubSendResult
import com.khaiit.kremote.core.network.HubSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Contains display data only, never a credential or a pending command to replay. */
data class HubUiState(
    val busy: Boolean = true,
    val configured: Boolean = false,
    val address: String = "",
    val fanTransport: HubTransport = HubTransport.PHONE,
    val snapshot: HubSnapshot? = null,
    val message: String = "Đang đọc cấu hình Hub…",
    val settingsError: String? = null,
    val settingsRevision: Int = 0
) {
    fun canSendFan(phoneIrAvailable: Boolean): Boolean = !busy && when (fanTransport) {
        HubTransport.PHONE -> phoneIrAvailable
        HubTransport.HUB -> configured && listOf(
            HubCommand.FAN_HIGH, HubCommand.FAN_SWING, HubCommand.FAN_OFF
        ).all { snapshot?.supports(it) == true }
    }
}

/** Retained by the Activity's ViewModelStore, including the single in-flight gate. */
class HubController(
    private val api: HubApi,
    private val store: HubConfigStore,
    private val networkAllowed: () -> Boolean,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel(), AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val gate = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(HubUiState())
    val state: StateFlow<HubUiState> = mutableState.asStateFlow()
    private var settings: HubSettings? = null
    private var operation: Job? = null

    init {
        operation = scope.launch {
            try {
                settings = store.load()
                val current = settings
                mutableState.value = if (current == null) HubUiState(
                    message = "Chưa cấu hình Hub · Quạt dùng IR điện thoại"
                ) else HubUiState(
                    configured = true,
                    address = current.config.endpoint.ipv4,
                    fanTransport = current.fanTransport,
                    message = "Đã lưu cấu hình · Kiểm tra kết nối trước khi dùng Hub"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = HubUiState(
                    message = "Không thể đọc cấu hình Hub. Cần nhập lại IP và API key.",
                    settingsError = "Không thể giải mã cấu hình đã lưu; dữ liệu chưa bị xóa."
                )
            } finally {
                releaseGate()
            }
        }
    }

    fun saveConfiguration(address: String, replacementKey: String?, transport: HubTransport): Boolean {
        if (gate.get() || closed.get()) return false
        val candidate = try {
            val key = replacementKey ?: settings?.config?.apiKey
                ?: throw IllegalArgumentException()
            HubSettings(
                HubConfig(HubEndpoint(address.trim()), key), transport,
                settings?.fixTransport ?: HubTransport.PHONE
            )
        } catch (_: IllegalArgumentException) {
            mutableState.value = state.value.copy(
                settingsError = "Nhập IPv4 LAN hợp lệ và API key gồm 64 ký tự ASCII in được."
            )
            return false
        }
        return saveSettings(candidate)
    }

    fun saveSettings(candidate: HubSettings): Boolean = launchLocked(
        "Đang lưu cấu hình Hub…", saving = true
    ) {
        store.save(candidate)
        settings = candidate
        mutableState.value = state.value.copy(
            configured = true,
            address = candidate.config.endpoint.ipv4,
            fanTransport = candidate.fanTransport,
            snapshot = null,
            settingsError = null,
            settingsRevision = state.value.settingsRevision + 1,
            message = "Đã lưu cấu hình · Kiểm tra kết nối trước khi dùng Hub"
        )
    }

    fun checkHub(): Boolean {
        if (gate.get() || closed.get()) return false
        val config = settings?.config ?: return false
        if (!networkAllowed()) {
            permissionDenied()
            return false
        }
        return launchLocked("Đang kiểm tra kết nối Hub…") {
            mutableState.value = state.value.copy(snapshot = null)
            val result = api.inspect(config.endpoint)
            result.onSuccess { snapshot ->
                mutableState.value = state.value.copy(
                    snapshot = snapshot,
                    message = if (snapshot.irSend) {
                        "Hub ${snapshot.firmware} đã kết nối · API key chưa được kiểm chứng"
                    } else {
                        "Đã kết nối Hub · Driver IR chưa sẵn sàng"
                    }
                )
            }.onFailure {
                mutableState.value = state.value.copy(
                    snapshot = null,
                    message = "Không thể kiểm tra Hub. Kiểm tra IP, Wi-Fi, quyền LAN và phiên bản API."
                )
            }
        }
    }

    /** Acquires immediately; a second press is rejected rather than queued. */
    fun submit(command: HubCommand, phoneSend: () -> Unit): Boolean {
        if (gate.get() || closed.get()) return false
        val current = settings
        val transport = if (command == HubCommand.TOSHIBA_FIX) {
            current?.fixTransport ?: HubTransport.PHONE
        } else current?.fanTransport ?: HubTransport.PHONE
        if (transport == HubTransport.PHONE) {
            return launchLocked("Đang gửi IR điện thoại…", sending = true) {
                phoneSend()
                mutableState.value = state.value.copy(message = "Đã gửi IR điện thoại · Chưa xác minh thiết bị")
            }
        }
        if (!networkAllowed()) {
            permissionDenied()
            return false
        }
        if (current == null || state.value.snapshot?.supports(command) != true) {
            mutableState.value = state.value.copy(message = "Hub chưa sẵn sàng cho lệnh này. Hãy kiểm tra kết nối.")
            return false
        }
        return launchLocked("Đang gửi IR qua Hub…", sending = true) {
            when (val result = api.send(current.config, command)) {
                HubSendResult.Sent -> mutableState.value = state.value.copy(
                    message = "Đã gửi IR qua Hub · Chưa xác minh thiết bị"
                )
                is HubSendResult.Rejected -> mutableState.value = state.value.copy(
                    snapshot = null,
                    message = when (result.httpStatus) {
                        401 -> "Hub từ chối API key. Kiểm tra lại cấu hình."
                        404, 422 -> "Hub không hỗ trợ thiết bị hoặc lệnh này."
                        503 -> "Driver IR chưa sẵn sàng. Có thể cần khởi động lại Hub."
                        else -> "Hub từ chối lệnh. Kiểm tra lại kết nối và cấu hình."
                    }
                )
                is HubSendResult.Uncertain -> mutableState.value = state.value.copy(
                    snapshot = null, message = UNCERTAIN_MESSAGE
                )
            }
        }
    }

    fun permissionDenied() {
        if (gate.get() || closed.get()) return
        mutableState.value = state.value.copy(snapshot = null, message = "Cần cấp quyền mạng LAN để dùng Hub.")
    }

    fun cancelOperation() { operation?.cancel() }

    private fun launchLocked(
        message: String,
        sending: Boolean = false,
        saving: Boolean = false,
        block: suspend () -> Unit
    ): Boolean {
        if (closed.get() || !gate.compareAndSet(false, true)) return false
        mutableState.value = state.value.copy(busy = true, message = message, settingsError = null)
        operation = scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                if (!closed.get()) mutableState.value = state.value.copy(
                    snapshot = null,
                    message = if (sending) UNCERTAIN_MESSAGE else "Đã hủy thao tác Hub."
                )
                throw cancelled
            } catch (_: Exception) {
                if (!closed.get()) mutableState.value = state.value.copy(
                    snapshot = null,
                    settingsError = if (saving) "Không thể lưu cấu hình Hub. Cấu hình cũ được giữ lại." else null,
                    message = if (sending) UNCERTAIN_MESSAGE else "Thao tác Hub không thành công."
                )
            } finally {
                releaseGate()
            }
        }
        return true
    }

    private fun releaseGate() {
        if (!closed.get()) {
            mutableState.value = state.value.copy(busy = false)
            gate.set(false)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) scope.cancel()
    }

    override fun onCleared() { close() }

    private companion object {
        const val UNCERTAIN_MESSAGE = "Chưa xác định lệnh đã phát hay chưa. Quan sát thiết bị trước khi thao tác lại."
    }
}
