package com.khaiit.kremote.core.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import kotlinx.coroutines.CancellationException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun <T> vaultStorage(block: () -> T): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    throw VaultStorageException()
}

data class VaultKey(val kek: ByteArray, val salt: ByteArray, val iterations: Int) {
    override fun toString() = "VaultKey(<redacted>)"
}

class VaultKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vault_secure_v1", Context.MODE_PRIVATE)

    fun save(key: VaultKey): Unit = vaultStorage {
        require(key.kek.size == 32 && key.salt.size == VaultCrypto.SALT_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(true))
        check(cipher.iv.size == 12)
        val wrapped = cipher.iv + cipher.doFinal(key.kek)
        check(prefs.edit()
            .putString("kek", Base64.getEncoder().encodeToString(wrapped))
            .putString("salt", Base64.getEncoder().encodeToString(key.salt))
            .putInt("iterations", key.iterations).commit())
    }

    fun load(): VaultKey? = vaultStorage {
        val encoded = prefs.getString("kek", null) ?: return@vaultStorage null
        run {
            val wrapped = Base64.getDecoder().decode(encoded)
            require(wrapped.size == 60)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(false), GCMParameterSpec(128, wrapped.copyOfRange(0, 12)))
            val salt = Base64.getDecoder().decode(prefs.getString("salt", null) ?: throw VaultFormatException())
            val iterations = prefs.getInt("iterations", 0)
            require(salt.size == VaultCrypto.SALT_BYTES && iterations in 1..2_000_000)
            VaultKey(cipher.doFinal(wrapped.copyOfRange(12, wrapped.size)), salt, iterations)
        }
    }

    private fun secretKey(createIfMissing: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("khairc_vault_aes_v1", null) as? SecretKey)?.let { return it }
        check(createIfMissing)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("khairc_vault_aes_v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
}
