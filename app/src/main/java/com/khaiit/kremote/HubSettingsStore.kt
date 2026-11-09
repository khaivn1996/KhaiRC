package com.khaiit.kremote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.khaiit.kremote.core.network.HubConfig
import com.khaiit.kremote.core.network.HubEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class HubTransport { PHONE, HUB }

data class HubSettings(
    val config: HubConfig,
    val fanTransport: HubTransport = HubTransport.PHONE,
    val fixTransport: HubTransport = HubTransport.PHONE
) {
    override fun toString(): String = "HubSettings(<redacted>)"
}

interface HubConfigStore {
    suspend fun load(): HubSettings?
    suspend fun save(settings: HubSettings)
}

/** The encrypted preferences are excluded from both cloud backup and device transfer. */
class HubSettingsStore(context: Context) : HubConfigStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun load(): HubSettings? = withContext(Dispatchers.IO) {
        try {
            val payload = prefs.getString(KEY_BLOB, null) ?: return@withContext null
            val sealed = Base64.decode(payload, Base64.NO_WRAP)
            require(sealed.size > IV_BYTES + TAG_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            // Loading never replaces an unavailable key or erases an unreadable configuration.
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(createIfMissing = false),
                GCMParameterSpec(TAG_BYTES * 8, sealed.copyOfRange(0, IV_BYTES))
            )
            val plaintext = cipher.doFinal(sealed.copyOfRange(IV_BYTES, sealed.size))
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            require(json.getInt("schemaVersion") == 1)
            HubSettings(
                config = HubConfig(HubEndpoint(json.getString("ipv4")), json.getString("apiKey")),
                fanTransport = HubTransport.valueOf(json.getString("fanTransport")),
                fixTransport = HubTransport.valueOf(json.getString("fixTransport"))
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Parser/crypto exception details can contain decrypted input. Do not retain them.
            throw IllegalStateException("Không thể đọc cấu hình Hub. Hãy nhập lại địa chỉ và API key để thay thế cấu hình đã lưu.")
        }
    }

    override suspend fun save(settings: HubSettings): Unit = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
                .put("schemaVersion", 1)
                .put("ipv4", settings.config.endpoint.ipv4)
                .put("apiKey", settings.config.apiKey)
                .put("fanTransport", settings.fanTransport.name)
                .put("fixTransport", settings.fixTransport.name)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(createIfMissing = true))
            check(cipher.iv.size == IV_BYTES)
            val sealed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
            check(prefs.edit().putString(KEY_BLOB, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw IllegalStateException("Không thể xác nhận lưu cấu hình Hub. Hãy thử lưu lại.")
        }
    }

    private fun secretKey(createIfMissing: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(createIfMissing)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "hub_secure_v1"
        const val KEY_BLOB = "config"
        const val KEY_ALIAS = "khairc_hub_aes_v1"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
