package com.khaiit.kremote.core.vault

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException

interface VaultAuth {
    fun currentUid(): String?
    suspend fun signIn(activity: Activity): String
}

class GoogleVaultAuth(private val serverClientId: String) : VaultAuth {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun signIn(activity: Activity): String {
        try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId).build()
            val result = CredentialManager.create(activity).getCredential(
                activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
            val credential = result.credential
            require(credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val token = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(token.idToken, null)
            return auth.signInWithCredential(firebaseCredential).await().user?.uid ?: throw VaultSignInException()
        } catch (error: CancellationException) {
            throw error
        } catch (_: GetCredentialCancellationException) {
            throw VaultCancelledSignIn()
        } catch (error: VaultNetworkException) {
            throw error
        } catch (_: Exception) {
            throw VaultSignInException()
        }
    }
}
