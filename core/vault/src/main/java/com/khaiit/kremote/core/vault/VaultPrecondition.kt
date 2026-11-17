package com.khaiit.kremote.core.vault

import java.util.Base64

/**
 * Condition on the current cloud document, evaluated atomically with the write (Firestore transaction).
 * The salt identifies the master-password generation: every new password (setup or "Quên mật khẩu")
 * draws a fresh salt, so an install holding an older KEK can never overwrite a newer copy.
 */
sealed interface VaultPrecondition {
    /** Explicit replacement, only after the "Quên mật khẩu" overwrite confirmation. */
    data object Overwrite : VaultPrecondition

    /** First setup: refuse if another install created a copy in the meantime. */
    data object MustNotExist : VaultPrecondition

    /** Routine push/merge: only while the cloud copy still uses this install's key generation. */
    class SameKey(val salt: ByteArray) : VaultPrecondition {
        override fun toString() = "SameKey(<redacted>)"
    }

    /** [cloudSalt] is the stored Base64 salt field (null if missing or not a string). */
    fun allows(cloudExists: Boolean, cloudSalt: String?): Boolean = when (this) {
        Overwrite -> true
        MustNotExist -> !cloudExists
        is SameKey -> !cloudExists || cloudSalt == Base64.getEncoder().encodeToString(salt)
    }
}

/** The cloud copy was recreated with another master password; this install must unlock it again. */
class VaultKeyChangedException : Exception("Bản sao cloud đã đổi mật khẩu chủ")
