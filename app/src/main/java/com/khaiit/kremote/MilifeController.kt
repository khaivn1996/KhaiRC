package com.khaiit.kremote

import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import com.khaiit.kremote.core.network.HubClientException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.khaiit.kremote.core.network.HubConfig
import com.khaiit.kremote.core.network.HubEndpoint
import com.khaiit.kremote.core.network.MilifePowerApi
import com.khaiit.kremote.core.network.MilifePowerResult
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
    val message: UiText = uiText(R.string.milife_reading_config)
)

/** Shares the existing encrypted HubSettingsStore; never stores a key in UI state. */
class MilifeController(
    private val api: MilifePowerApi,
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
                    message = if (current == null) uiText(R.string.milife_not_configured) else uiText(R.string.milife_unverified)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = MilifeUiState(busy = false,
                    message = uiText(R.string.milife_config_unreadable))
            }
            if (settings != null && networkAllowed()) refresh()
        }
    }

    /** A single GET. No command vocabulary, writes, retry or cloud fallback. */
    fun refresh() {
        if (state.busy) return
        val config = settings?.config
        if (config == null) {
            state = state.copy(power = null, message = uiText(R.string.milife_not_configured))
            return
        }
        if (!networkAllowed()) {
            permissionDenied()
            return
        }
        state = state.copy(busy = true, power = null, message = uiText(R.string.milife_reading_https))
        scope.launch {
            try {
                val result = api.readMilife(config)
                state = result.fold(
                    onSuccess = { power -> state.copy(power = power, message = uiText(R.string.milife_verified)) },
                    onFailure = { error ->
                        val code = (
                                error as? HubClientException
                                )?.code

                        val detail = when (code) {
                            "https_not_configured" -> uiText(R.string.milife_https_setup_needed)
                            "tls_validation_failed" -> uiText(R.string.milife_tls_failed)
                            "redirect_blocked" -> uiText(R.string.milife_redirect_blocked)
                            "http_401" -> uiText(R.string.milife_unauthorized)
                            "http_404" -> uiText(R.string.milife_not_found)
                            "http_502" -> uiText(R.string.milife_read_failed)
                            "http_504" -> uiText(R.string.milife_device_timeout)
                            "timeout" -> uiText(R.string.milife_hub_timeout)
                            "network_permission" -> uiText(R.string.milife_network_permission)
                            "network_error" -> uiText(R.string.milife_network_error)
                            "invalid_response" -> uiText(R.string.milife_invalid_response)
                            else -> uiText(R.string.milife_unknown_error)
                        }

                        state.copy(power = null, message = detail)
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(power = null, message = uiText(R.string.milife_request_failed))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }

    fun setPower(power: Boolean) {
        val currentPower = state.power
        if (state.busy || currentPower == null || currentPower == power) return
        val config = settings?.config ?: return
        if (!networkAllowed()) {
            permissionDenied()
            return
        }
        state = state.copy(busy = true, power = null, message = uiText(R.string.milife_sending))
        scope.launch {
            try {
                when (val result = api.setPower(config, power)) {
                    is MilifePowerResult.Rejected -> {
                        val detail = when (result.code) {
                            "https_not_configured" -> uiText(R.string.milife_https_not_configured)
                            "unauthorized" -> uiText(R.string.milife_unauthorized)
                            "unsupported_action" -> uiText(R.string.milife_unsupported_action)
                            "miot_hello_timeout" -> uiText(R.string.milife_hello_timeout)
                            "miot_not_configured" -> uiText(R.string.milife_miot_not_configured)
                            "device_not_ready" -> uiText(R.string.milife_device_not_ready)
                            "miot_device_rejected" -> uiText(R.string.milife_device_rejected)
                            else -> uiText(R.string.milife_command_rejected)
                        }
                        state = state.copy(message = uiText(R.string.milife_rejected_read_back, detail))
                    }
                    is MilifePowerResult.ReadBack -> {
                        val message = when (result.power) {
                            power -> if (power) uiText(R.string.milife_verified_on) else uiText(R.string.milife_verified_off)
                            null -> uiText(R.string.milife_result_unverified)
                            else -> uiText(R.string.milife_target_mismatch)
                        }
                        state = state.copy(power = result.power, message = message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(message = uiText(R.string.milife_result_unverified))
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
                message = uiText(R.string.milife_invalid_credentials))
            return false
        }
        state = state.copy(busy = true, power = null, message = uiText(R.string.milife_saving))
        scope.launch {
            var saved = false
            try {
                store.save(candidate)
                settings = candidate
                saved = true
                state = state.copy(configured = true, address = candidate.config.endpoint.ipv4,
                    power = null, message = uiText(R.string.milife_saved))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(power = null, message = uiText(R.string.milife_save_failed))
            } finally {
                state = state.copy(busy = false)
            }
            if (saved && networkAllowed()) refresh()
        }
        return true
    }

    fun permissionDenied() {
        if (!state.busy) state = state.copy(power = null, message = uiText(R.string.milife_lan_permission))
    }

    override fun close() { scope.cancel() }
}
