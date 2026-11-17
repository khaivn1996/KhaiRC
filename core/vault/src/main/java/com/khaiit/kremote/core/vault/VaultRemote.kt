package com.khaiit.kremote.core.vault

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface VaultRemote {
    suspend fun fetch(uid: String): SealedDoc?
    /** Throws [VaultKeyChangedException] when [precondition] rejects the current cloud doc; nothing is written. */
    suspend fun put(uid: String, doc: SealedDoc, precondition: VaultPrecondition)
}

class FirestoreVaultRemote : VaultRemote {
    private companion object {
        val db: FirebaseFirestore by lazy {
            FirebaseFirestore.getInstance().apply {
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            }
        }
    }

    override suspend fun fetch(uid: String): SealedDoc? = network {
        val snapshot = db.document("users/$uid/vault/main").get(Source.SERVER).await()
        if (snapshot.exists()) SealedDoc.fromMap(snapshot.data ?: throw VaultFormatException()) else null
    }

    /**
     * Whole-document set is idempotent cloud backup, never a device POST/IR command.
     * The precondition is read and the doc written in one transaction, so a concurrent
     * password change on another install cannot be overwritten by an older key.
     */
    override suspend fun put(uid: String, doc: SealedDoc, precondition: VaultPrecondition) {
        val written = network {
            val ref = db.document("users/$uid/vault/main")
            val data = doc.toMap() + ("updatedAt" to FieldValue.serverTimestamp())
            db.runTransaction { transaction ->
                val current = transaction.get(ref)
                val cloudSalt = runCatching { current.getString("salt") }.getOrNull()
                val allowed = precondition.allows(current.exists(), cloudSalt)
                if (allowed) transaction.set(ref, data)
                allowed
            }.await()
        }
        if (!written) throw VaultKeyChangedException()
    }

    private suspend fun <T> network(block: suspend () -> T): T {
        try {
            // Box the nullable fetch result so timeout is distinct from a missing document.
            return (withTimeoutOrNull(20_000) { listOf(block()) } ?: throw VaultNetworkException()).single()
        } catch (error: CancellationException) {
            throw error
        } catch (error: VaultFormatException) {
            throw error
        } catch (_: Exception) {
            throw VaultNetworkException()
        }
    }
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isCanceled) continuation.cancel()
        else if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(VaultNetworkException())
    }
}
