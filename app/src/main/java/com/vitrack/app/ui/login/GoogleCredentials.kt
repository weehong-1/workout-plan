package com.vitrack.app.ui.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult
    data class Ended(val outcome: GoogleOutcome) : GoogleResult
}

/**
 * Asks Google, through Android's Credential Manager, for an ID token for
 * [request]'s client: Google's own sign-in sheet, with its account picker.
 * Needs an Activity [context] to show the sheet.
 */
suspend fun requestGoogleIdToken(context: Context, request: GoogleRequest): GoogleResult {
    val option = GetSignInWithGoogleOption.Builder(serverClientId = request.clientId)
        .setNonce(request.hashedNonce)
        .build()
    return try {
        val result = CredentialManager.create(context)
            .getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleResult.Ended(GoogleOutcome.Failed)
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleResult.Ended(GoogleOutcome.Cancelled)
    } catch (e: NoCredentialException) {
        GoogleResult.Ended(GoogleOutcome.NoAccount)
    } catch (e: GetCredentialException) {
        GoogleResult.Ended(GoogleOutcome.Failed)
    } catch (e: GoogleIdTokenParsingException) {
        GoogleResult.Ended(GoogleOutcome.Failed)
    }
}
