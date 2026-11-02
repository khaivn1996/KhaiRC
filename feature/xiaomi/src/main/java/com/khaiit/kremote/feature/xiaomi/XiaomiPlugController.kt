package com.khaiit.kremote.feature.xiaomi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class XiaomiPlugUiState(
    val configured: Boolean = false,
    val ip: String = "",
    val power: Boolean? = null,
    val busy: Boolean = false,
    val message: String? = null
)

class XiaomiPlugController(
    private val store: XiaomiPlugConfigStore,
    private val repository: XiaomiPlugRepository
) {
    private val mutex = Mutex()
    var state by mutableStateOf(XiaomiPlugUiState())
        private set

    init {
        runCatching { store.load() }
            .onSuccess { config ->
                state = state.copy(configured = config != null, ip = config?.ip.orEmpty())
            }.onFailure {
                state = state.copy(message = "Không thể đọc cấu hình Xiaomi đã lưu")
            }
    }

    /** Empty fields retain stored values. Never return a saved token to Compose UI. */
    suspend fun saveConfig(ip: String, token: String): Boolean {
        var saved = false
        mutex.withLock {
            state = state.copy(busy = true, message = null)
            try {
                val previous = withContext(Dispatchers.IO) { store.load() }
                val config = XiaomiPlugConfig(
                    ip = ip.trim().ifBlank { previous?.ip.orEmpty() },
                    token = token.trim().ifBlank { previous?.token.orEmpty() }
                ).validated()
                withContext(Dispatchers.IO) { store.save(config) }
                state = XiaomiPlugUiState(configured = true, ip = config.ip)
                saved = true
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                state = state.copy(message = "Không lưu được cấu hình: " + (e.message ?: "lỗi"))
            } finally {
                state = state.copy(busy = false)
            }
        }
        if (saved) refresh()
        return saved
    }

    suspend fun refresh() {
        mutex.withLock {
            state = state.copy(busy = true, message = null, power = null)
            try {
                val config = withContext(Dispatchers.IO) { store.load() }
                if (config == null) {
                    state = XiaomiPlugUiState(message = "Chưa cấu hình IP và token")
                    return@withLock
                }
                state = state.copy(configured = true, ip = config.ip)
                val observed = repository.readPower(config)
                state = state.copy(power = observed)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                state = state.copy(power = null, message = "Không thể đọc trạng thái qua LAN/VPN")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    /** A single write followed by one read. No auto-retry on ambiguous failures. */
    suspend fun setPower(target: Boolean) {
        mutex.withLock {
            val current = state.power
            if (!state.configured || state.busy || current == null || current == target) return@withLock
            state = state.copy(busy = true, power = null, message = null)
            try {
                val config = withContext(Dispatchers.IO) { store.load() }
                    ?: throw IllegalStateException("Missing Xiaomi configuration")
                repository.setPower(config, target)
                val verified = repository.readPower(config)
                state = state.copy(
                    power = verified,
                    message = if (verified == target) null
                        else "Trạng thái thực tế chưa khớp lệnh. Không tự gửi lại."
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                state = state.copy(
                    power = null,
                    message = "Không xác minh được sau lệnh. Thiết bị CÓ THỂ đã đổi trạng thái; " +
                        "không tự gửi lại. Nếu OFF làm mất VPN, cần khôi phục nguồn tại nhà."
                )
            } finally {
                state = state.copy(busy = false)
            }
        }
    }
}
