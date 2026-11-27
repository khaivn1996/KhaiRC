package com.khaiit.kremote.vault

import com.khaiit.kremote.core.ui.text.UiText
import com.khaiit.kremote.core.ui.text.uiText
import androidx.annotation.StringRes
import com.khaiit.kremote.R
import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.khaiit.kremote.core.vault.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Group(@StringRes val labelRes: Int) {
    TUYA(R.string.vault_group_tuya), HUB(R.string.vault_group_hub), HTTPS(R.string.vault_group_https), WOL(R.string.vault_group_wol), LABELS(R.string.vault_group_labels)
}

sealed interface VaultUiState {
    data object Ready : VaultUiState
    data object Checking : VaultUiState
    data object NeedSignIn : VaultUiState
    data class NeedPassword(val error: UiText? = null) : VaultUiState
    data class NeedSetup(val localGroups: Set<Group>, val error: UiText? = null) : VaultUiState
    data class Working(val msg: UiText) : VaultUiState
    data class Error(val msg: UiText, val canSkip: Boolean) : VaultUiState
}

private class WeakPasswordException : Exception("Mật khẩu chủ không hợp lệ")

class VaultController(
    private val sources: LocalVaultSources,
    private val auth: VaultAuth,
    private val remote: VaultRemote,
    private val keys: VaultKeyStore,
    private val markers: VaultStateStore,
    private val onRestored: () -> Unit
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = AtomicBoolean(false)
    private var pendingDoc: SealedDoc? = null
    private var skipped = false
    private var started = false
    private var revision = 0L
    private val dirtyLock = Any()
    private val syncedAtLaunch = isSynced()
    var applying: Boolean = false
        private set
    var replacingCloud by mutableStateOf(false)
        private set
    // A synced install opens straight into Ready: no gate flash, no network call.
    var state: VaultUiState by mutableStateOf(if (syncedAtLaunch) VaultUiState.Ready else VaultUiState.Checking)
        private set
    /** True only when this install holds a key and has completed a sync; enables manual/auto push. */
    var backupEnabled by mutableStateOf(syncedAtLaunch)
        private set
    var syncStatus by mutableStateOf<UiText>(
        if (!syncedAtLaunch) uiText(R.string.vault_sync_missing) else if (isDirty()) uiText(R.string.vault_sync_dirty) else uiText(R.string.vault_sync_done)
    )
        private set

    fun start() {
        if (skipped || gate.get()) return
        if (started && state !is VaultUiState.Error) {
            if (state == VaultUiState.Ready && backupEnabled && isDirty()) pushNow()
            return
        }
        started = true
        if (isSynced()) {
            replacingCloud = false
            backupEnabled = true
            state = VaultUiState.Ready
            syncStatus = if (isDirty()) uiText(R.string.vault_sync_dirty) else uiText(R.string.vault_sync_done)
            if (isDirty()) pushNow()
            return
        }
        runOperation(uiText(R.string.vault_checking)) {
            replacingCloud = false
            checkCloud()
        }
    }

    /** Re-opens the gate from the Network tab after "Bỏ qua lần này" or an unfinished setup. */
    fun reopenSetup() {
        if (gate.get() || backupEnabled) return
        skipped = false
        started = false
        start()
    }

    private suspend fun checkCloud() {
        val uid = auth.currentUid()
        if (uid == null) {
            state = VaultUiState.NeedSignIn
            return
        }
        pendingDoc = withContext(Dispatchers.IO) { remote.fetch(uid) }
        state = if (pendingDoc != null) VaultUiState.NeedPassword()
        else VaultUiState.NeedSetup(groups(sources.readAll()))
    }

    fun signIn(activity: Activity) {
        runOperation(uiText(R.string.vault_signing_in)) {
            auth.signIn(activity)
            checkCloud()
        }
    }

    fun unlock(password: CharArray) {
        runOperation(uiText(R.string.vault_opening), cleanup = { password.fill('\u0000') }) {
            val doc = pendingDoc ?: throw VaultFormatException()
            val uid = auth.currentUid() ?: throw VaultSignInException()
            val key = withContext(Dispatchers.Default) { VaultCrypto.deriveKek(password, doc.salt, doc.iterations) }
            try {
                val cloud = try {
                    withContext(Dispatchers.Default) {
                        val plaintext = VaultCrypto.open(key, SealedBlob(doc.iv, doc.ct), uid)
                        try { VaultCodec.decode(plaintext) } finally { plaintext.fill(0) }
                    }
                } catch (_: VaultAuthException) {
                    // Stay on the password screen: keeps the fetched doc and the "Quên mật khẩu" path.
                    state = VaultUiState.NeedPassword(uiText(R.string.vault_error_password))
                    return@runOperation
                }
                val merge = VaultMerge.merge(sources.readAll(), cloud)
                apply(merge.toApply)
                val storedKey = VaultKey(key, doc.salt, doc.iterations)
                withContext(Dispatchers.IO) { keys.save(storedKey) }
                // Only write over the exact copy just decrypted; a newer password elsewhere aborts the push.
                val digest = if (merge.needsPush) {
                    put(uid, storedKey, merge.merged, VaultPrecondition.SameKey(doc.salt))
                } else digest(merge.merged)
                withContext(Dispatchers.IO) { markers.completed(digest) }
                onRestored()
            } finally { key.fill(0) }
        }
    }

    fun setup(form: VaultBundle, password: CharArray, confirm: CharArray) {
        // Replacing requires the explicit overwrite confirmation; a first setup must not clobber a copy
        // that another install created after this gate fetched "no copy".
        val precondition = if (replacingCloud) VaultPrecondition.Overwrite else VaultPrecondition.MustNotExist
        runOperation(uiText(R.string.vault_creating), cleanup = { password.fill('\u0000'); confirm.fill('\u0000') }) {
            try {
                if (password.size < VaultCrypto.MIN_PASSWORD || !password.contentEquals(confirm)) {
                    throw WeakPasswordException()
                }
                val uid = auth.currentUid() ?: throw VaultSignInException()
                apply(fillForm(form, sources.readAll()))
                val normalized = sources.readAll()
                val salt = ByteArray(VaultCrypto.SALT_BYTES).also { SecureRandom().nextBytes(it) }
                val key = withContext(Dispatchers.Default) {
                    VaultCrypto.deriveKek(password, salt, VaultCrypto.DEFAULT_ITERATIONS)
                }
                try {
                    val storedKey = VaultKey(key, salt, VaultCrypto.DEFAULT_ITERATIONS)
                    val digest = put(uid, storedKey, normalized, precondition)
                    withContext(Dispatchers.IO) {
                        keys.save(storedKey)
                        markers.completed(digest)
                    }
                    onRestored()
                } finally { key.fill(0) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: VaultKeyChangedException) {
                // A copy now exists that this form did not see: "Thử lại" re-fetches it and asks its password.
                state = VaultUiState.Error(errorText(error), canSkip = true)
            } catch (error: Exception) {
                // Back to the form (its fields are hoisted in VaultGateScreen) with a group-specific hint.
                state = VaultUiState.NeedSetup(groups(sources.readAll()), errorText(error))
            }
        }
    }

    fun forgotPassword() {
        runOperation(uiText(R.string.vault_reading_local)) {
            replacingCloud = true
            state = VaultUiState.NeedSetup(groups(sources.readAll()))
        }
    }

    fun skipThisSession() {
        if (gate.get()) return
        skipped = true
        state = VaultUiState.Ready
        syncStatus = uiText(R.string.vault_sync_skipped)
    }

    fun markDirty() {
        synchronized(dirtyLock) {
            revision++
            // Runs inside a SharedPreferences listener: a marker failure must not crash the app.
            try { markers.markDirty() } catch (_: Exception) { }
        }
        if (backupEnabled) syncStatus = uiText(R.string.vault_sync_dirty)
    }

    /** Idempotent Firestore set; never retries or dispatches a device POST/IR command. */
    fun pushNow() {
        if (skipped || !backupEnabled || state != VaultUiState.Ready) return
        runOperation(uiText(R.string.vault_syncing), background = true) {
            val uid = auth.currentUid() ?: throw VaultSignInException()
            val key = withContext(Dispatchers.IO) { keys.load() } ?: throw VaultStorageException()
            try {
                val before = revision
                // Strict: an unreadable local group aborts the push instead of deleting it from the cloud copy.
                val bundle = sources.readAllStrict()
                val hash = digest(bundle)
                if (hash != markers.lastPushDigest) put(uid, key, bundle, VaultPrecondition.SameKey(key.salt))
                withContext(Dispatchers.IO) {
                    synchronized(dirtyLock) { markers.completed(hash, dirty = before != revision) }
                }
                syncStatus = if (isDirty()) uiText(R.string.vault_sync_dirty) else uiText(R.string.vault_sync_done)
            } catch (_: VaultKeyChangedException) {
                // Another install set a new master password: never overwrite its copy with this older key.
                // Local edits stay dirty and are merged/pushed after unlocking with the new password.
                withContext(Dispatchers.IO) { markers.resetSync() }
                backupEnabled = false
                syncStatus = uiText(R.string.vault_sync_key_changed)
            } finally { key.kek.fill(0) }
        }
    }

    private fun runOperation(
        message: UiText,
        background: Boolean = false,
        cleanup: () -> Unit = {},
        block: suspend () -> Unit
    ) {
        if (!gate.compareAndSet(false, true)) { cleanup(); return }
        if (!background) state = VaultUiState.Working(message) else syncStatus = message
        scope.launch {
            var succeeded = false
            try {
                block()
                succeeded = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: VaultCancelledSignIn) {
                state = VaultUiState.NeedSignIn
            } catch (error: Exception) {
                val text = errorText(error)
                if (background) syncStatus = text else state = VaultUiState.Error(text, canSkip = true)
            } finally {
                cleanup()
                gate.set(false)
            }
            if (succeeded && state == VaultUiState.Ready && backupEnabled && isDirty() && !skipped) pushNow()
        }
    }

    /** Fixed Vietnamese texts only; never includes exception messages or input values. */
    private fun errorText(error: Exception): UiText = when (error) {
        is VaultAuthException -> uiText(R.string.vault_error_password)
        is VaultSignInException ->
            uiText(R.string.vault_error_sign_in)
        is VaultStorageException -> uiText(R.string.vault_error_storage)
        is VaultFormatException -> uiText(R.string.vault_error_format)
        is VaultKeyChangedException ->
            uiText(R.string.vault_error_cloud_replaced)
        is VaultLocalReadException ->
            uiText(R.string.vault_error_local_read, UiText.Join(error.groups.map { uiText(it.labelRes) }))
        is VaultInvalidGroupException -> when (error.group) {
            Group.TUYA -> uiText(R.string.vault_error_tuya)
            Group.HUB -> uiText(R.string.vault_error_hub)
            Group.HTTPS -> uiText(R.string.vault_error_https)
            Group.WOL -> uiText(R.string.vault_error_wol)
            Group.LABELS -> uiText(R.string.vault_error_labels)
        }
        is WeakPasswordException -> uiText(R.string.vault_error_weak_password)
        else -> uiText(R.string.vault_error_sync)
    }

    private fun isSynced(): Boolean = try { markers.syncedForInstall } catch (_: Exception) { false }

    private fun isDirty(): Boolean = try { markers.dirty } catch (_: Exception) { false }

    private suspend fun apply(bundle: VaultBundle) {
        applying = true
        try { sources.apply(bundle) } finally { applying = false }
    }

    private suspend fun put(uid: String, key: VaultKey, bundle: VaultBundle, precondition: VaultPrecondition): String =
        withContext(Dispatchers.Default) {
            val plaintext = VaultCodec.encode(bundle)
            try {
                val blob = VaultCrypto.seal(key.kek, plaintext, uid)
                withContext(Dispatchers.IO) {
                    remote.put(
                        uid, SealedDoc(iterations = key.iterations, salt = key.salt, iv = blob.iv, ct = blob.ct),
                        precondition
                    )
                }
                hash(plaintext)
            } finally { plaintext.fill(0) }
        }

    private suspend fun digest(bundle: VaultBundle): String = withContext(Dispatchers.Default) {
        val bytes = VaultCodec.encode(bundle)
        try { hash(bytes) } finally { bytes.fill(0) }
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun groups(bundle: VaultBundle): Set<Group> = buildSet {
        if (bundle.tuya != null) add(Group.TUYA)
        if (bundle.hub != null) add(Group.HUB)
        if (bundle.https != null) add(Group.HTTPS)
        if (bundle.wol != null) add(Group.WOL)
        if (!bundle.labels.isNullOrEmpty()) add(Group.LABELS)
    }

    private fun fillForm(form: VaultBundle, local: VaultBundle) = VaultBundle(
        tuya = form.tuya?.let {
            TuyaPart(it.clientId.ifBlank { local.tuya?.clientId.orEmpty() },
                it.clientSecret.ifBlank { local.tuya?.clientSecret.orEmpty() },
                it.deviceId.ifBlank { local.tuya?.deviceId.orEmpty() })
        } ?: local.tuya,
        hub = form.hub?.let {
            HubPart(it.ipv4.ifBlank { local.hub?.ipv4.orEmpty() }, it.apiKey.ifBlank { local.hub?.apiKey.orEmpty() })
        } ?: local.hub,
        https = form.https?.let {
            HttpsPart(it.host.ifBlank { local.https?.host.orEmpty() }, it.port.takeIf { port -> port != 0 } ?: local.https?.port ?: 443,
                it.fingerprintHex.ifBlank { local.https?.fingerprintHex.orEmpty() })
        } ?: local.https,
        wol = form.wol?.let {
            WolPart(it.mac.ifBlank { local.wol?.mac.orEmpty() }, it.ip.ifBlank { local.wol?.ip.orEmpty() })
        } ?: local.wol,
        labels = form.labels?.let { local.labels.orEmpty() + it } ?: local.labels
    )

    override fun close() { scope.cancel() }
}
