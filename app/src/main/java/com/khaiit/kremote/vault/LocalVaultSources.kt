package com.khaiit.kremote.vault

import android.content.Context
import com.khaiit.kremote.HttpsProbePreferences
import com.khaiit.kremote.HubSettings
import com.khaiit.kremote.HubSettingsStore
import com.khaiit.kremote.core.network.HubConfig
import com.khaiit.kremote.core.network.HubEndpoint
import com.khaiit.kremote.core.network.HubHttpsEndpoint
import com.khaiit.kremote.core.network.Ipv4Address
import com.khaiit.kremote.core.network.MacAddress
import com.khaiit.kremote.core.vault.*
import com.khaiit.kremote.feature.tuya.TuyaChannel
import com.khaiit.kremote.feature.tuya.TuyaCredentialStore
import com.khaiit.kremote.feature.tuya.TuyaCredentials
import com.khaiit.kremote.feature.tuya.TuyaOc09LabelBackup
import com.khaiit.kremote.feature.wol.WolConfig
import com.khaiit.kremote.feature.wol.WolSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A group failed validation. Carries only the group, never the rejected value. */
class VaultInvalidGroupException(val group: Group) : Exception("Cấu hình sao lưu không hợp lệ")

/** Stored groups exist but cannot be read. Carries only group names, never values. */
class VaultLocalReadException(val groups: Set<Group>) : Exception("Không đọc được cấu hình trên máy")

class LocalVaultSources(context: Context) {
    private val tuya = TuyaCredentialStore(context)
    private val hub = HubSettingsStore(context)
    private val https = HttpsProbePreferences(context)
    private val wol = WolSettings(context)
    private val labels = TuyaOc09LabelBackup(context)

    /** Restore/setup view: an unreadable group counts as empty, so cloud or the form may replace it. */
    suspend fun readAll(): VaultBundle = read(strict = false)

    /** Backup snapshot: never omits an unreadable group, so a push cannot delete it from the cloud copy. */
    suspend fun readAllStrict(): VaultBundle = read(strict = true)

    private suspend fun read(strict: Boolean): VaultBundle = withContext(Dispatchers.IO) {
        val failed = linkedSetOf<Group>()
        val bundle = VaultBundle(
            tuya = readGroup(Group.TUYA, failed) {
                tuya.load()?.let { TuyaPart(it.clientId, it.clientSecret, it.deviceId) }
            },
            hub = readGroup(Group.HUB, failed) { hub.load()?.config?.let { HubPart(it.endpoint.ipv4, it.apiKey) } },
            https = readGroup(Group.HTTPS, failed) { https.readStored()?.let { HttpsPart(it.host, it.port, it.fingerprintHex) } },
            wol = readGroup(Group.WOL, failed) {
                if (!wol.hasSavedConfig()) null else wol.getConfig().let {
                    require(MacAddress.isValid(MacAddress.normalize(it.macAddress)) && Ipv4Address.isValid(it.targetIp))
                    WolPart(it.macAddress, it.targetIp)
                }
            },
            labels = readGroup(Group.LABELS, failed) { labels.loadCustom().takeIf { it.isNotEmpty() } }
        )
        if (strict && failed.isNotEmpty()) throw VaultLocalReadException(failed)
        bundle
    }

    suspend fun apply(bundle: VaultBundle): Unit = withContext(Dispatchers.IO) {
        // Validate every group before writing any group, including authenticated cloud input.
        val validTuya = bundle.tuya?.let {
            valid(Group.TUYA) { TuyaCredentials(it.clientId, it.clientSecret, it.deviceId).validated() }
        }
        val validHub = bundle.hub?.let { valid(Group.HUB) { HubConfig(HubEndpoint(it.ipv4), it.apiKey) } }
        val validHttps = bundle.https?.let {
            valid(Group.HTTPS) { HubHttpsEndpoint(it.host, it.port, it.fingerprintHex) }
        }
        val validWol = bundle.wol?.let {
            valid(Group.WOL) {
                val mac = MacAddress.normalize(it.mac)
                val ip = it.ip.trim()
                require(MacAddress.isValid(mac) && Ipv4Address.isValid(ip))
                WolConfig(mac, ip)
            }
        }
        bundle.labels?.let { map ->
            valid(Group.LABELS) {
                map.forEach { (key, value) ->
                    require(key in TuyaChannel.CODES && value.trim().length <= 24 && value.trim().none(Char::isISOControl))
                }
            }
        }
        try {
            validTuya?.let { tuya.save(it) }
            validHub?.let { config ->
                val previous = readGroup(Group.HUB, mutableSetOf()) { hub.load() }
                hub.save(previous?.copy(config = config) ?: HubSettings(config))
            }
            validHttps?.let { check(https.save(it)) }
            validWol?.let { wol.saveConfig(it) }
            bundle.labels?.let { labels.saveCustom(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Store exceptions can carry decrypted input; keep only a fixed local-storage error.
            throw VaultStorageException()
        }
    }

    private inline fun <T> valid(group: Group, block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        throw VaultInvalidGroupException(group)
    }

    private suspend fun <T> readGroup(group: Group, failed: MutableSet<Group>, block: suspend () -> T?): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failed += group
        null
    }
}
