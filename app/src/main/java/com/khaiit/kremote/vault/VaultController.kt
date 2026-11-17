package com.khaiit.kremote.vault

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

enum class Group(val label: String) {
    TUYA("Tuya"), HUB("Hub"), HTTPS("HTTPS"), WOL("WOL"), LABELS("tên ổ cắm")
}

sealed interface VaultUiState {
    data object Ready : VaultUiState
    data object Checking : VaultUiState
    data object NeedSignIn : VaultUiState
    data class NeedPassword(val error: String? = null) : VaultUiState
    data class NeedSetup(val localGroups: Set<Group>, val error: String? = null) : VaultUiState
    data class Working(val msg: String) : VaultUiState
    data class Error(val msg: String, val canSkip: Boolean) : VaultUiState
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
    var syncStatus by mutableStateOf(
        if (!syncedAtLaunch) "Chưa sao lưu cloud" else if (isDirty()) "Có thay đổi chưa sao lưu" else "Đã đồng bộ cloud"
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
            syncStatus = if (isDirty()) "Có thay đổi chưa sao lưu" else "Đã đồng bộ cloud"
            if (isDirty()) pushNow()
            return
        }
        runOperation("Đang kiểm tra bản sao…") {
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
        runOperation("Đang đăng nhập Google…") {
            auth.signIn(activity)
            checkCloud()
        }
    }

    fun unlock(password: CharArray) {
        runOperation("Đang mở bản sao…", cleanup = { password.fill('\u0000') }) {
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
                    state = VaultUiState.NeedPassword("Sai mật khẩu chủ hoặc dữ liệu cloud bị hỏng")
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
        runOperation("Đang tạo bản sao…", cleanup = { password.fill('\u0000'); confirm.fill('\u0000') }) {
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
        runOperation("Đang đọc cấu hình trên máy…") {
            replacingCloud = true
            state = VaultUiState.NeedSetup(groups(sources.readAll()))
        }
    }

    fun skipThisSession() {
        if (gate.get()) return
        skipped = true
        state = VaultUiState.Ready
        syncStatus = "Đã bỏ qua sao lưu trong phiên này"
    }

    fun markDirty() {
        synchronized(dirtyLock) {
            revision++
            // Runs inside a SharedPreferences listener: a marker failure must not crash the app.
            try { markers.markDirty() } catch (_: Exception) { }
        }
        if (backupEnabled) syncStatus = "Có thay đổi chưa sao lưu"
    }

    /** Idempotent Firestore set; never retries or dispatches a device POST/IR command. */
    fun pushNow() {
        if (skipped || !backupEnabled || state != VaultUiState.Ready) return
        runOperation("Đang sao lưu…", background = true) {
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
                syncStatus = if (isDirty()) "Có thay đổi chưa sao lưu" else "Đã đồng bộ cloud"
            } catch (_: VaultKeyChangedException) {
                // Another install set a new master password: never overwrite its copy with this older key.
                // Local edits stay dirty and are merged/pushed after unlocking with the new password.
                withContext(Dispatchers.IO) { markers.resetSync() }
                backupEnabled = false
                syncStatus = "Mật khẩu chủ của bản sao đã đổi ở máy khác. Nhấn Thiết lập sao lưu và nhập mật khẩu mới; thay đổi trên máy sẽ được sao lưu sau đó."
            } finally { key.kek.fill(0) }
        }
    }

    private fun runOperation(
        message: String,
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
    private fun errorText(error: Exception): String = when (error) {
        is VaultAuthException -> "Sai mật khẩu chủ hoặc dữ liệu cloud bị hỏng"
        is VaultSignInException ->
            "Không đăng nhập được Google. Kiểm tra tài khoản Google trên máy và cấu hình Firebase (SHA-1, Google provider)."
        is VaultStorageException -> "Không đọc hoặc ghi được dữ liệu sao lưu trên máy. Hãy thử lại."
        is VaultFormatException -> "Dữ liệu sao lưu không hợp lệ"
        is VaultKeyChangedException ->
            "Bản sao cloud vừa được tạo lại hoặc đổi mật khẩu chủ ở máy khác. Nhấn Thử lại rồi nhập mật khẩu chủ mới."
        is VaultLocalReadException ->
            "Không đọc được cấu hình ${error.groups.joinToString { it.label }} trên máy; chưa sao lưu để tránh làm mất bản trên cloud."
        is VaultInvalidGroupException -> when (error.group) {
            Group.TUYA -> "Tuya: cần đủ Client ID, Client Secret và Device ID hợp lệ."
            Group.HUB -> "Hub: IPv4 phải là địa chỉ LAN; API key gồm đúng 64 ký tự ASCII."
            Group.HTTPS -> "HTTPS: kiểm tra hostname/IPv4, cổng và SHA-256 fingerprint (64 ký tự hex)."
            Group.WOL -> "WOL: MAC hoặc IP đích không hợp lệ."
            Group.LABELS -> "Tên ổ cắm tối đa 24 ký tự, không chứa ký tự điều khiển."
        }
        is WeakPasswordException -> "Mật khẩu chủ tối thiểu 12 ký tự và hai lần nhập phải giống nhau"
        else -> "Không thể đồng bộ cloud. Hãy kiểm tra kết nối rồi thử lại"
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
