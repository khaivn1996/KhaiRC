package com.khaiit.kremote.feature.xiaomi

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

/** Token is a 16-byte miIO secret. It must never be logged or committed. */
data class XiaomiPlugConfig(val ip: String, val token: String) {
    fun validated(): XiaomiPlugConfig {
        val address = ip.trim()
        val octets = address.split(".")
        require(octets.size == 4 && octets.all {
            it.isNotEmpty() && it.all(Char::isDigit) &&
                it.toIntOrNull() in 0..255
        }) { "Nhập địa chỉ IPv4 hợp lệ" }
        require(token.matches(Regex("[a-fA-F0-9]{32}"))) {
            "Token miIO phải gồm đúng 32 ký tự hex"
        }
        return copy(ip = address, token = token.lowercase())
    }

    override fun toString(): String = "XiaomiPlugConfig(<redacted>)"
}

interface XiaomiPlugConfigStore {
    fun load(): XiaomiPlugConfig?
    fun save(config: XiaomiPlugConfig)
}

/** Non-exportable AES-GCM key in Android Keystore; backup exclusions are mandatory. */
class AndroidXiaomiPlugConfigStore(context: Context) : XiaomiPlugConfigStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): XiaomiPlugConfig? {
        val encoded = prefs.getString(BLOB, null) ?: return null
        try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_LENGTH + 16)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(),
                GCMParameterSpec(128, payload.copyOfRange(0, IV_LENGTH))
            )
            val json = JSONObject(
                String(cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size)), Charsets.UTF_8)
            )
            return XiaomiPlugConfig(json.getString("ip"), json.getString("token")).validated()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Không giải mã được cấu hình Xiaomi. Kiểm tra khóa Keystore trước khi nhập lại.", e
            )
        }
    }

    override fun save(config: XiaomiPlugConfig) {
        val valid = config.validated()
        val json = JSONObject().put("ip", valid.ip).put("token", valid.token)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(BLOB, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Không thể lưu cấu hình Xiaomi"
        }
    }

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "xiaomi_plug_secure"
        const val BLOB = "config"
        const val ALIAS = "khairc_xiaomi_plug_aes_v1"
        const val IV_LENGTH = 12
    }
}
