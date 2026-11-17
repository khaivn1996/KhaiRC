package com.khaiit.kremote.core.vault

import org.junit.Assert.*
import org.junit.Test

class VaultCryptoTest {
    private val salt = ByteArray(16) { it.toByte() }
    private fun key(password: String) = VaultCrypto.deriveKek(password.toCharArray(), salt, 1000)

    @Test fun roundtrip() {
        val kek = key("master-password")
        val plaintext = "private configuration".toByteArray()
        val blob = VaultCrypto.seal(kek, plaintext, "user-a")
        assertEquals(12, blob.iv.size)
        assertArrayEquals(plaintext, VaultCrypto.open(kek, blob, "user-a"))
    }

    @Test fun wrongPassword() {
        val blob = VaultCrypto.seal(key("master-password"), byteArrayOf(1, 2), "user-a")
        assertThrows(VaultAuthException::class.java) { VaultCrypto.open(key("wrong-password"), blob, "user-a") }
    }

    @Test fun changedCiphertext() {
        val kek = key("master-password")
        val blob = VaultCrypto.seal(kek, byteArrayOf(1, 2), "user-a")
        blob.ct[0] = (blob.ct[0].toInt() xor 1).toByte()
        assertThrows(VaultAuthException::class.java) { VaultCrypto.open(kek, blob, "user-a") }
    }

    @Test fun differentUid() {
        val kek = key("master-password")
        val blob = VaultCrypto.seal(kek, byteArrayOf(1, 2), "user-a")
        assertThrows(VaultAuthException::class.java) { VaultCrypto.open(kek, blob, "user-b") }
    }

    @Test fun freshIvForEverySeal() {
        val kek = key("master-password")
        assertFalse(VaultCrypto.seal(kek, byteArrayOf(1), "user-a").iv
            .contentEquals(VaultCrypto.seal(kek, byteArrayOf(1), "user-a").iv))
    }
}
