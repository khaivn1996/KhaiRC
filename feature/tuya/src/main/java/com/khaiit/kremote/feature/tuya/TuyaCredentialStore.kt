package com.khaiit.kremote.feature.tuya

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Never put these values into Gradle, resources, logcat, or a Git repository. */
data class TuyaCredentials(val clientId: String, val clientSecret: String, val deviceId: String) {
    fun validated(): TuyaCredentials {
        require(clientId.isNotBlank() && clientSecret.isNotBlank()) { "Thiếu Client ID hoặc Client Secret" }
        require(deviceId.matches(Regex("[A-Za-z0-9_-]{6,128}"))) { "Device ID không hợp lệ" }
        return copy(clientId = clientId.trim(), deviceId = deviceId.trim())
    }
}

interface TuyaConfigStore {
    fun load(): TuyaCredentials?
    fun save(credentials: TuyaCredentials)
}

/**
 * AES/GCM with a non-exportable Android Keystore key. The encrypted SharedPreferences
 * file MUST be excluded from cloud backup AND device-to-device transfer.
 */
class TuyaCredentialStore(context: Context) : TuyaConfigStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): TuyaCredentials? {
        val payload = prefs.getString(KEY_BLOB, null) ?: return null
        try {
            val all = Base64.decode(payload, Base64.NO_WRAP)
            require(all.size > IV_BYTES + 16)
            val iv = all.copyOfRange(0, IV_BYTES)
            val ciphertext = all.copyOfRange(IV_BYTES, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            return TuyaCredentials(
                clientId = json.getString("clientId"),
                clientSecret = json.getString("clientSecret"),
                deviceId = json.getString("deviceId")
            ).validated()
        } catch (e: Exception) {
            // Do not silently wipe an encrypted credential if its Keystore key is unavailable.
            throw IllegalStateException("Không thể giải mã cấu hình Tuya. Cần nhập lại cấu hình sau khi xử lý dữ liệu lưu trữ.", e)
        }
    }

    override fun save(credentials: TuyaCredentials) {
        val valid = credentials.validated()
        val json = JSONObject()
            .put("clientId", valid.clientId)
            .put("clientSecret", valid.clientSecret)
            .put("deviceId", valid.deviceId)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(KEY_BLOB, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()) {
            "Không thể lưu cấu hình Tuya"
        }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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
        const val PREFS = "tuya_oc09_secure"
        const val KEY_BLOB = "config"
        const val KEY_ALIAS = "khairc_tuya_oc09_aes_v1"
        const val IV_BYTES = 12
    }
}
