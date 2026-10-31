package de.markusressel.mkdocseditor.feature.auth.common.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.domain.NotSignedInException
import java.io.IOException

/**
 * A text for the user, resolved in the UI
 */
data class UiMessage(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList(),
) {
    @Composable
    fun resolve(): String = stringResource(resId, *args.toTypedArray())
}

/**
 * Maps errors of the auth layer to messages, see docs/auth.md section 10
 */
fun Throwable.toAuthMessage(): UiMessage = when (this) {
    is AuthApiException -> toAuthMessage()
    is NotSignedInException -> UiMessage(R.string.auth_error_not_signed_in)
    is CredentialException -> UiMessage(R.string.auth_error_credential_provider, listOf(message.orEmpty()))
    is IOException -> UiMessage(R.string.auth_error_network, listOf(message ?: javaClass.simpleName))
    else -> UiMessage(R.string.auth_error_unknown, listOf(message ?: javaClass.simpleName))
}

private fun AuthApiException.toAuthMessage(): UiMessage = when (code) {
    AuthErrorCode.INVALID_CREDENTIALS -> UiMessage(R.string.auth_error_invalid_credentials)
    AuthErrorCode.INVALID_SECOND_FACTOR -> when (val left = (details["attemptsLeft"] as? Number)?.toInt()) {
        null -> UiMessage(R.string.auth_error_invalid_code)
        else -> UiMessage(R.string.auth_error_invalid_code_attempts, listOf(left))
    }

    AuthErrorCode.CHALLENGE_EXPIRED -> UiMessage(R.string.auth_error_challenge_expired)
    AuthErrorCode.PASSKEY_VERIFICATION_FAILED -> UiMessage(R.string.auth_error_passkey_failed)
    AuthErrorCode.PASSKEY_REGISTRATION_FAILED -> UiMessage(R.string.auth_error_passkey_registration_failed)
    AuthErrorCode.PASSKEYS_DISABLED -> UiMessage(R.string.auth_error_passkeys_disabled)
    AuthErrorCode.ACCOUNT_DISABLED -> UiMessage(R.string.auth_error_account_disabled)
    AuthErrorCode.RATE_LIMITED -> UiMessage(R.string.auth_error_rate_limited, listOf(retryAfterSeconds ?: 60L))
    AuthErrorCode.INVALID_LINK -> UiMessage(R.string.auth_error_invalid_link)
    AuthErrorCode.USERNAME_TAKEN -> UiMessage(R.string.auth_error_username_taken)
    AuthErrorCode.INVALID_USERNAME -> UiMessage(R.string.auth_error_invalid_username)
    AuthErrorCode.WEAK_PASSWORD -> when (details["reason"]) {
        "too-short" -> UiMessage(
            R.string.auth_error_password_too_short,
            listOf((details["minLength"] as? Number)?.toInt() ?: 10)
        )

        "too-long" -> UiMessage(
            R.string.auth_error_password_too_long,
            listOf((details["maxLength"] as? Number)?.toInt() ?: 256)
        )

        "common" -> UiMessage(R.string.auth_error_password_common)
        "repetitive" -> UiMessage(R.string.auth_error_password_repetitive)
        "contains-username" -> UiMessage(R.string.auth_error_password_contains_username)
        else -> UiMessage(R.string.auth_error_password_weak)
    }

    AuthErrorCode.REAUTH_REQUIRED -> UiMessage(R.string.auth_error_reauth_required)
    AuthErrorCode.SECOND_FACTOR_SETUP_REQUIRED -> UiMessage(R.string.auth_error_second_factor_setup_required)
    AuthErrorCode.LAST_LOGIN_METHOD -> UiMessage(R.string.auth_error_last_login_method)
    AuthErrorCode.SECOND_FACTOR_REQUIRED -> UiMessage(R.string.auth_error_second_factor_required)
    AuthErrorCode.TOTP_ALREADY_ENABLED -> UiMessage(R.string.auth_error_totp_already_enabled)
    AuthErrorCode.TOTP_NOT_ENABLED -> UiMessage(R.string.auth_error_totp_not_enabled)
    AuthErrorCode.UNAUTHENTICATED -> UiMessage(R.string.auth_error_not_signed_in)
    AuthErrorCode.FORBIDDEN -> UiMessage(R.string.auth_error_forbidden)
    null -> when (status) {
        // an older backend without the account endpoints
        404, 405 -> UiMessage(R.string.auth_error_not_supported)
        401 -> UiMessage(R.string.auth_error_not_signed_in)
        else -> UiMessage(R.string.auth_error_http, listOf(status, message.orEmpty()))
    }

    else -> UiMessage(R.string.auth_error_http, listOf(status, message.orEmpty()))
}
