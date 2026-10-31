package de.markusressel.mkdocseditor.feature.auth.common.data

import android.content.Context
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import timber.log.Timber

/**
 * A credential the user picked in the system sheet
 */
sealed interface PickedCredential {
    /**
     * @param authenticationResponseJson the assertion, sent to the backend as is
     */
    data class Passkey(val authenticationResponseJson: String) : PickedCredential

    data class Password(val username: String, val password: String) : PickedCredential {
        override fun toString() = "Password(username=$username)"
    }
}

/**
 * Talks to Android Credential Manager, so passkeys and passwords work with every credential
 * provider the user chose (Google Password Manager, Bitwarden, ...), not only Google's.
 *
 * All calls need an Activity context: they show system UI.
 */
interface CredentialClient {

    /**
     * Shows the sign-in sheet with passkeys (when [passkeyRequestJson] is given) and saved passwords.
     *
     * @param passkeyRequestJson the `publicKey` options of the backend
     * @return the picked credential, or null when the user cancelled or has none
     */
    suspend fun pickCredential(
        activityContext: Context,
        passkeyRequestJson: String?,
        includePasswords: Boolean,
    ): PickedCredential?

    /**
     * Creates a passkey.
     *
     * @param requestJson the `publicKey` creation options of the backend
     * @return the registration response JSON, or null when the user cancelled
     * @throws CredentialException when the provider failed
     */
    suspend fun createPasskey(activityContext: Context, requestJson: String): String?

    /**
     * Offers to save a username and password in the credential provider. Never throws.
     */
    suspend fun savePassword(activityContext: Context, username: String, password: String)
}

/**
 * @param message a user-readable reason
 */
class CredentialException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AndroidCredentialClient : CredentialClient {

    override suspend fun pickCredential(
        activityContext: Context,
        passkeyRequestJson: String?,
        includePasswords: Boolean
    ): PickedCredential? {
        val options = buildList<CredentialOption> {
            passkeyRequestJson?.let { add(GetPublicKeyCredentialOption(requestJson = it)) }
            if (includePasswords) add(GetPasswordOption())
        }
        if (options.isEmpty()) return null

        return try {
            val result = CredentialManager.create(activityContext)
                .getCredential(activityContext, GetCredentialRequest(options))
            when (val credential = result.credential) {
                is PublicKeyCredential -> PickedCredential.Passkey(credential.authenticationResponseJson)
                is PasswordCredential -> PickedCredential.Password(credential.id, credential.password)
                else -> throw CredentialException("Unsupported credential type ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: NoCredentialException) {
            null
        } catch (e: GetCredentialException) {
            Timber.w(e, "getCredential failed")
            throw CredentialException(e.errorMessage?.toString() ?: e.type, e)
        }
    }

    override suspend fun createPasskey(activityContext: Context, requestJson: String): String? {
        return try {
            val response: CreateCredentialResponse = CredentialManager.create(activityContext)
                .createCredential(activityContext, CreatePublicKeyCredentialRequest(requestJson = requestJson))
            (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
                ?: throw CredentialException("Unexpected response ${response.type}")
        } catch (e: CreateCredentialCancellationException) {
            null
        } catch (e: CreateCredentialException) {
            Timber.w(e, "createCredential failed")
            throw CredentialException(e.errorMessage?.toString() ?: e.type, e)
        }
    }

    override suspend fun savePassword(activityContext: Context, username: String, password: String) {
        try {
            CredentialManager.create(activityContext)
                .createCredential(activityContext, CreatePasswordRequest(id = username, password = password))
        } catch (e: CreateCredentialException) {
            // cancelled, or no provider that saves passwords
            Timber.i("Password not saved: ${e.type}")
        }
    }
}
