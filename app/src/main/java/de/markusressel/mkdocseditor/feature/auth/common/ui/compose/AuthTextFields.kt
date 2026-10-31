package de.markusressel.mkdocseditor.feature.auth.common.ui.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import de.markusressel.mkdocseditor.R

/**
 * Autofill hint of authenticator app codes (View.AUTOFILL_HINT_2FA_APP_OTP), which password
 * managers like Bitwarden fill from the TOTP they store
 */
private val AuthenticatorCode = ContentType("2faAppOTPCode")

/**
 * Username field with autofill semantics, so password managers can fill it
 *
 * @param newUsername true when the user picks a new username (registration)
 */
@Composable
internal fun UsernameField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    newUsername: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        modifier = modifier.semantics {
            contentType = if (newUsername) ContentType.NewUsername else ContentType.Username
        },
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(R.string.auth_username)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
    )
}

/**
 * Password field with autofill semantics
 *
 * @param newPassword true when a new password is chosen (registration, reset, change), so
 * password managers offer to generate and save one
 */
@Composable
internal fun PasswordField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String = stringResource(R.string.auth_password),
    newPassword: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        modifier = modifier.semantics {
            contentType = if (newPassword) ContentType.NewPassword else ContentType.Password
        },
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.auth_hide_password else R.string.auth_show_password
                    ),
                )
            }
        },
    )
}

/**
 * Field for a 6 digit authenticator code, with one-time-code autofill semantics
 */
@Composable
internal fun OneTimeCodeField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        modifier = modifier.semantics {
            contentType = AuthenticatorCode + ContentType.SmsOtpCode
        },
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(R.string.auth_totp_code)) },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
    )
}

/**
 * Field for a recovery code like `abcd-efgh-jkmn`
 */
@Composable
internal fun RecoveryCodeField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(R.string.auth_recovery_code)) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
    )
}

/**
 * The Activity of a Compose context, needed for Credential Manager
 */
internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
