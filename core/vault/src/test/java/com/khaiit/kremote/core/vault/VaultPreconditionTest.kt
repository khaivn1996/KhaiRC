package com.khaiit.kremote.core.vault

import org.junit.Assert.*
import org.junit.Test

class VaultPreconditionTest {
    private val oldSalt = ByteArray(16) { 1 }
    private val newSalt = ByteArray(16) { 2 }

    // The salt field exactly as FirestoreVaultRemote stores it.
    private fun storedSalt(salt: ByteArray) =
        SealedDoc(1000, salt, ByteArray(12), ByteArray(16)).toMap()["salt"] as String

    @Test fun olderKeyCannotOverwriteCopyRecreatedWithNewPassword() {
        // Install A still holds the old KEK; install B used "Quên mật khẩu" and wrote a new salt.
        assertFalse(VaultPrecondition.SameKey(oldSalt).allows(cloudExists = true, cloudSalt = storedSalt(newSalt)))
    }

    @Test fun sameKeyGenerationMayPush() {
        assertTrue(VaultPrecondition.SameKey(oldSalt).allows(cloudExists = true, cloudSalt = storedSalt(oldSalt)))
    }

    @Test fun sameKeyMayRecreateMissingDoc() {
        assertTrue(VaultPrecondition.SameKey(oldSalt).allows(cloudExists = false, cloudSalt = null))
    }

    @Test fun malformedSaltIsTreatedAsChangedKey() {
        assertFalse(VaultPrecondition.SameKey(oldSalt).allows(cloudExists = true, cloudSalt = null))
        assertFalse(VaultPrecondition.SameKey(oldSalt).allows(cloudExists = true, cloudSalt = "not-base64"))
    }

    @Test fun firstSetupNeverClobbersExistingCopy() {
        assertFalse(VaultPrecondition.MustNotExist.allows(cloudExists = true, cloudSalt = storedSalt(newSalt)))
        assertTrue(VaultPrecondition.MustNotExist.allows(cloudExists = false, cloudSalt = null))
    }

    @Test fun confirmedReplacementAlwaysWrites() {
        assertTrue(VaultPrecondition.Overwrite.allows(cloudExists = true, cloudSalt = storedSalt(newSalt)))
        assertTrue(VaultPrecondition.Overwrite.allows(cloudExists = false, cloudSalt = null))
    }

    @Test fun sameKeyRedactsSalt() {
        assertTrue(VaultPrecondition.SameKey(oldSalt).toString().contains("<redacted>"))
    }
}
