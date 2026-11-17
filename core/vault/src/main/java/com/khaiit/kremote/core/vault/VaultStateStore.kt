package com.khaiit.kremote.core.vault

import android.content.Context

class VaultStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vault_state_v1", Context.MODE_PRIVATE)
    val syncedForInstall: Boolean get() = vaultStorage { prefs.getBoolean("syncedForInstall", false) }
    val dirty: Boolean get() = vaultStorage { prefs.getBoolean("dirty", false) }
    val lastPushDigest: String? get() = vaultStorage { prefs.getString("lastPushDigest", null) }
    val lastSyncAt: Long get() = vaultStorage { prefs.getLong("lastSyncAt", 0) }

    fun markDirty(): Unit = vaultStorage {
        check(prefs.edit().putBoolean("dirty", true).commit())
    }

    fun completed(digest: String, dirty: Boolean = false): Unit = vaultStorage {
        check(prefs.edit().putBoolean("syncedForInstall", true).putBoolean("dirty", dirty)
            .putString("lastPushDigest", digest).putLong("lastSyncAt", System.currentTimeMillis()).commit())
    }

    /** Cloud key changed on another install: unlock again; keep dirty so local edits are pushed afterwards. */
    fun resetSync(): Unit = vaultStorage {
        check(prefs.edit().putBoolean("syncedForInstall", false).putBoolean("dirty", true)
            .remove("lastPushDigest").commit())
    }
}
