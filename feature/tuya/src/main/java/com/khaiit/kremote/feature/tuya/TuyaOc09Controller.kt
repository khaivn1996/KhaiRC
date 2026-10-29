package com.khaiit.kremote.feature.tuya

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** UI-state updates are invoked on the main thread by the Activity / Compose. */
class TuyaOc09Controller(
    private val configStore: TuyaConfigStore,
    private val repository: TuyaOc09Repository
) {
    private val operationMutex = Mutex()
    var state by mutableStateOf(TuyaOc09State())
        private set

    init {
        runCatching { configStore.load() }.onSuccess { config ->
            state = state.copy(configured = config != null)
        }.onFailure {
            state = state.copy(message = "Không đọc được cấu hình Tuya đã lưu. Hãy nhập lại cấu hình.")
        }
    }

    fun configurationSummary(): Pair<String, String> = runCatching {
        configStore.load()?.let { it.clientId to it.deviceId } ?: ("" to "")
    }.getOrDefault("" to "")

    /** Empty secret means keep the existing value, if one exists. */
    suspend fun saveConfiguration(clientId: String, secretInput: String, deviceId: String): Boolean {
        var success = false
        operationMutex.withLock {
            state = state.copy(busy = true, message = null)
            try {
                val previous = if (secretInput.isBlank()) withContext(Dispatchers.IO) { configStore.load() } else null
                val credentials = TuyaCredentials(
                    clientId.trim(), secretInput.takeIf { it.isNotBlank() } ?: previous?.clientSecret.orEmpty(), deviceId.trim()
                ).validated()
                withContext(Dispatchers.IO) { configStore.save(credentials) }
                repository.clearSession()
                state = TuyaOc09State(configured = true)
                success = true
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                state = state.copy(message = e.message ?: "Không thể lưu cấu hình")
            } finally {
                state = state.copy(busy = false)
            }
        }
        if (success) refresh()
        return success
    }

    suspend fun refresh() {
        operationMutex.withLock {
            val credentials = runCatching { withContext(Dispatchers.IO) { configStore.load() } }
                .getOrElse {
                    state = state.copy(verified = false, message = "Không đọc được cấu hình Tuya")
                    return@withLock
                }
            if (credentials == null) {
                state = state.copy(configured = false, verified = false, channelValues = emptyMap())
                return@withLock
            }
            state = state.copy(configured = true, busy = true, message = null)
            try {
                val values = repository.readStatus(credentials)
                state = state.copy(channelValues = values, verified = true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                state = state.copy(verified = false, message = "Không cập nhật được trạng thái: ${e.message ?: "Lỗi mạng"}")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    suspend fun toggle(code: String) {
        require(code in TuyaChannel.CODES)
        operationMutex.withLock {
            if (!state.configured || !state.verified || state.busy) return@withLock
            val current = state.channelValues[code] ?: return@withLock
            state = state.copy(busy = true, message = null)
            try {
                val credentials = withContext(Dispatchers.IO) { configStore.load() }
                    ?: error("Chưa cấu hình Tuya")
                val desired = !current
                repository.setPower(credentials, code, desired)
                // A success response only means the cloud accepted the command.
                // POST success means the cloud accepted the request, not that the
                // latest /status response already reflects the new relay state.
                // Refresh automatically, allowing bounded time for Tuya propagation.
                val values = readStatusUntilConfirmed(credentials, code, desired)
                state = state.copy(channelValues = values, verified = true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                // Never claim an ON/OFF transition if confirmation did not succeed.
                state = state.copy(verified = false, message = "Chưa xác minh được lệnh: ${e.message ?: "Lỗi mạng"}. Hãy Refresh.")
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    /**
     * Read-only retries: NEVER resend the power command. Some Tuya devices
     * report the previous status briefly after a successful POST.
     */
    private suspend fun readStatusUntilConfirmed(
        credentials: TuyaCredentials,
        code: String,
        desired: Boolean
    ): Map<String, Boolean> {
        repeat(CONFIRM_READ_ATTEMPTS) { attempt ->
            val values = repository.readStatus(credentials)
            if (values[code] == desired) return values
            if (attempt < CONFIRM_READ_ATTEMPTS - 1) {
                delay(CONFIRM_DELAY_MS * (attempt + 1))
            }
        }
        error("Tuya chưa cập nhật trạng thái $code sau khi gửi lệnh")
    }


    /**
     * Master indicator is (S1 OR ... OR S6). Its ACTION is a batch of six
     * commands in ONE Tuya POST, permitted only after device-level validation.
     * Any partial/uncertain result marks the displayed status unverified.
     */
    suspend fun toggleMaster() {
        operationMutex.withLock {
            if (!state.configured || !state.verified || state.busy) return@withLock
            val current = state.masterPower ?: return@withLock
            val desired = !current
            state = state.copy(busy = true, message = null)
            try {
                val credentials = withContext(Dispatchers.IO) { configStore.load() }
                    ?: error("Chưa cấu hình Tuya")
                repository.setAllPower(credentials, desired)
                // Never retry POST. Only GET is retried while Tuya propagates state.
                val values = readAllStatusUntilConfirmed(credentials, desired)
                state = state.copy(channelValues = values, verified = true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                state = state.copy(
                    verified = false,
                    message = "Power tổng chưa xác minh được cả 6 kênh: " +
                        "${e.message ?: "Lỗi mạng"}. Hãy Refresh để kiểm tra trạng thái từng ổ."
                )
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    /** Confirmation reads never issue an additional write or pretend partial success. */
    private suspend fun readAllStatusUntilConfirmed(
        credentials: TuyaCredentials,
        desired: Boolean
    ): Map<String, Boolean> {
        repeat(CONFIRM_READ_ATTEMPTS) { attempt ->
            val values = repository.readStatus(credentials)
            if (TuyaChannel.CODES.all { values[it] == desired }) return values
            if (attempt < CONFIRM_READ_ATTEMPTS - 1) delay(CONFIRM_DELAY_MS * (attempt + 1))
        }
        error("Cloud chưa xác nhận đủ 6 kênh")
    }

    private companion object {
        const val CONFIRM_READ_ATTEMPTS = 4
        const val CONFIRM_DELAY_MS = 500L
    }
}
