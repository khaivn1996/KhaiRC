package com.khaiit.kremote.core.vault

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class SealedBlob(val iv: ByteArray, val ct: ByteArray) {
    override fun toString() = "SealedBlob(<redacted>)"
}

object VaultCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    const val SALT_BYTES = 16
    const val MIN_PASSWORD = 12

    fun deriveKek(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(salt.size == SALT_BYTES && iterations in 1..2_000_000)
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(kek: ByteArray, plaintext: ByteArray, uid: String): SealedBlob {
        require(kek.size == 32 && uid.isNotBlank())
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("khairc-vault-v1|$uid".toByteArray(Charsets.UTF_8))
        return SealedBlob(iv, cipher.doFinal(plaintext))
    }

    fun open(kek: ByteArray, blob: SealedBlob, uid: String): ByteArray {
        require(kek.size == 32 && blob.iv.size == 12 && blob.ct.size >= 16 && uid.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, blob.iv))
        cipher.updateAAD("khairc-vault-v1|$uid".toByteArray(Charsets.UTF_8))
        return try {
            cipher.doFinal(blob.ct)
        } catch (_: AEADBadTagException) {
            throw VaultAuthException()
        }
    }
}
