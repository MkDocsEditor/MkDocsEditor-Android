package de.markusressel.mkdocseditor.feature.auth.account.ui.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AccountInfo
import de.markusressel.mkdocseditor.auth.api.PasskeyInfo
import de.markusressel.mkdocseditor.auth.api.SessionInfo
import de.markusressel.mkdocseditor.auth.api.TotpEnrollment
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel.Dialog
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.OneTimeCodeField
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.PasswordField
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.QrCode
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import java.time.Instant

/**
 * @param onOpenUri opens a link in another app, false when no app takes it
 */
@Composable
internal fun AccountScreenContent(
    uiState: AccountViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onBackClicked: () -> Unit,
    onOpenUri: (String) -> Boolean,
    onShareText: (String) -> Unit,
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.auth_account_title),
                canGoBack = true,
                onBackClicked = onBackClicked,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.error?.let {
                Text(
                    text = it.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.info?.let {
                Text(text = it.resolve(), style = MaterialTheme.typography.bodyMedium)
            }

            val account = uiState.account
            if (account != null && uiState.showPasskeyReminder) {
                PasskeyReminderCard(uiState.loading, onUiEvent)
            }
            if (account != null) {
                ProfileSection(uiState, account, onUiEvent)
                SecuritySection(account, onUiEvent)
                PasskeySection(uiState.passkeys, uiState.loading, onUiEvent)
                SessionSection(uiState.sessions, uiState.loading, onUiEvent)
                if (account.role == ROLE_ADMIN) {
                    AdminSection(onOpenUri)
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onUiEvent(UiEvent.SignOutClicked) },
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null)
                Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(R.string.auth_sign_out))
            }
        }
    }

    AccountDialogs(uiState, onUiEvent, onOpenUri, onShareText)
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PasskeyReminderCard(loading: Boolean, onUiEvent: (UiEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.passkey_setup_title)) {
        Text(text = stringResource(R.string.passkey_reminder_hint), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !loading, onClick = { onUiEvent(UiEvent.AddPasskeyClicked) }) {
                Text(stringResource(R.string.passkey_setup_create))
            }
            TextButton(onClick = { onUiEvent(UiEvent.PasskeyReminderDismissed) }) {
                Text(stringResource(R.string.passkey_reminder_dismiss))
            }
        }
    }
}

@Composable
private fun ProfileSection(uiState: AccountViewModel.UiState, account: AccountInfo, onUiEvent: (UiEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.account_profile)) {
        Text(
            text = stringResource(R.string.auth_signed_in_as, "${account.username} (${uiState.backendName})"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(text = stringResource(R.string.account_role, account.role), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.displayName,
            onValueChange = { onUiEvent(UiEvent.DisplayNameChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.auth_display_name)) },
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.email,
            onValueChange = { onUiEvent(UiEvent.EmailChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.account_email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.color,
            onValueChange = { onUiEvent(UiEvent.ColorChanged(it)) },
            singleLine = true,
            label = { Text(stringResource(R.string.account_color)) },
            supportingText = { Text(stringResource(R.string.account_color_hint)) },
        )
        Button(
            enabled = !uiState.loading,
            onClick = { onUiEvent(UiEvent.SaveProfileClicked) },
        ) {
            Text(stringResource(R.string.account_save_profile))
        }
    }
}

@Composable
private fun SecuritySection(account: AccountInfo, onUiEvent: (UiEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.account_security)) {
        Text(
            text = stringResource(
                if (account.hasPassword) R.string.account_password_set else R.string.account_password_not_set
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { onUiEvent(UiEvent.ChangePasswordClicked) }) {
            Text(
                stringResource(
                    if (account.hasPassword) R.string.account_change_password else R.string.account_set_password
                )
            )
        }

        HorizontalDivider()

        Text(text = stringResource(R.string.account_totp), style = MaterialTheme.typography.titleSmall)
        if (account.totpEnabled) {
            Text(
                text = stringResource(R.string.account_totp_enabled, account.recoveryCodesLeft),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onUiEvent(UiEvent.RegenerateRecoveryCodesClicked) }) {
                    Text(stringResource(R.string.account_recovery_codes_regenerate))
                }
                TextButton(onClick = { onUiEvent(UiEvent.DisableTotpClicked) }) {
                    Text(stringResource(R.string.account_totp_disable))
                }
            }
        } else {
            Text(text = stringResource(R.string.account_totp_not_enabled), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onUiEvent(UiEvent.EnableTotpClicked) }) {
                Text(stringResource(R.string.account_totp_enable))
            }
        }
    }
}

@Composable
private fun PasskeySection(passkeys: List<PasskeyInfo>, loading: Boolean, onUiEvent: (UiEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.account_passkeys)) {
        if (passkeys.isEmpty()) {
            Text(text = stringResource(R.string.account_passkeys_empty), style = MaterialTheme.typography.bodyMedium)
        }
        passkeys.forEach { passkey ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = passkey.name, style = MaterialTheme.typography.bodyLarge)
                    val details = stringResource(
                        R.string.account_passkey_details,
                        relativeTime(passkey.createdAt),
                        relativeTime(passkey.lastUsedAt),
                    )
                    val synced = stringResource(R.string.account_passkey_synced).takeIf { passkey.backedUp }
                    Text(
                        text = listOfNotNull(passkey.provider, details, synced).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { onUiEvent(UiEvent.RenamePasskeyClicked(passkey)) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.account_passkey_rename))
                }
                IconButton(onClick = { onUiEvent(UiEvent.DeletePasskeyClicked(passkey)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.account_passkey_delete))
                }
            }
        }
        Button(enabled = !loading, onClick = { onUiEvent(UiEvent.AddPasskeyClicked) }) {
            Text(stringResource(R.string.account_passkey_add))
        }
    }
}

@Composable
private fun SessionSection(sessions: List<SessionInfo>, loading: Boolean, onUiEvent: (UiEvent) -> Unit) {
    SectionCard(title = stringResource(R.string.account_sessions)) {
        sessions.forEach { session ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.deviceName ?: session.userAgent ?: session.type ?: session.id,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val details = if (session.current) {
                        stringResource(R.string.account_session_current)
                    } else {
                        stringResource(R.string.account_session_details, relativeTime(session.lastUsedAt))
                    }
                    Text(
                        text = listOfNotNull(details, session.ip).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { onUiEvent(UiEvent.RevokeSessionClicked(session)) }) {
                    Text(stringResource(R.string.account_session_revoke))
                }
            }
        }
        if (sessions.count { !it.current } > 0) {
            OutlinedButton(enabled = !loading, onClick = { onUiEvent(UiEvent.RevokeOtherSessionsClicked) }) {
                Text(stringResource(R.string.account_sessions_revoke_others))
            }
        }
    }
}

@Composable
private fun AdminSection(onOpenUri: (String) -> Boolean) {
    val url = stringResource(R.string.auth_web_client_url)
    SectionCard(title = stringResource(R.string.account_admin)) {
        Text(text = stringResource(R.string.account_admin_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onOpenUri(url) }) {
            Text(stringResource(R.string.account_open_web_client))
        }
    }
}

@Composable
private fun AccountDialogs(
    uiState: AccountViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onOpenUri: (String) -> Boolean,
    onShareText: (String) -> Unit,
) {
    val dismiss = { onUiEvent(UiEvent.DismissDialog) }
    when (val dialog = uiState.dialog) {
        null -> Unit

        is Dialog.ChangePassword -> ConfirmDialog(
            title = stringResource(R.string.account_change_password),
            enabled = !uiState.loading,
            onConfirm = { onUiEvent(UiEvent.ChangePasswordConfirmed) },
            onDismiss = dismiss,
        ) {
            if (uiState.account?.hasPassword == true) {
                PasswordField(
                    value = dialog.current,
                    onValueChange = { onUiEvent(UiEvent.ChangePasswordInput(it, dialog.new, dialog.confirmation)) },
                    label = stringResource(R.string.account_current_password),
                    supportingText = stringResource(R.string.account_current_password_hint),
                    imeAction = ImeAction.Next,
                )
            }
            PasswordField(
                value = dialog.new,
                onValueChange = { onUiEvent(UiEvent.ChangePasswordInput(dialog.current, it, dialog.confirmation)) },
                label = stringResource(R.string.auth_new_password),
                newPassword = true,
                supportingText = stringResource(R.string.auth_password_rules, AccountViewModel.MIN_PASSWORD_LENGTH),
                imeAction = ImeAction.Next,
            )
            PasswordField(
                value = dialog.confirmation,
                onValueChange = { onUiEvent(UiEvent.ChangePasswordInput(dialog.current, dialog.new, it)) },
                label = stringResource(R.string.auth_repeat_password),
                newPassword = true,
                onImeAction = { onUiEvent(UiEvent.ChangePasswordConfirmed) },
            )
            dialog.error?.let { ErrorText(it.resolve()) }
        }

        is Dialog.TotpEnroll -> TotpEnrollDialog(dialog, uiState.loading, onUiEvent, onOpenUri)

        is Dialog.RecoveryCodes -> RecoveryCodesDialog(dialog.codes, onShareText, dismiss)

        is Dialog.ConfirmDisableTotp -> ConfirmDialog(
            title = stringResource(R.string.account_totp_disable),
            enabled = !uiState.loading,
            onConfirm = { onUiEvent(UiEvent.DisableTotpConfirmed) },
            onDismiss = dismiss,
        ) {
            Text(stringResource(R.string.account_totp_disable_confirm))
        }

        is Dialog.RenamePasskey -> ConfirmDialog(
            title = stringResource(R.string.account_passkey_rename),
            enabled = !uiState.loading && dialog.name.isNotBlank(),
            onConfirm = { onUiEvent(UiEvent.RenamePasskeyConfirmed) },
            onDismiss = dismiss,
        ) {
            OutlinedTextField(
                value = dialog.name,
                onValueChange = { onUiEvent(UiEvent.RenamePasskeyInput(it)) },
                singleLine = true,
                label = { Text(stringResource(R.string.account_passkey_name)) },
            )
        }

        is Dialog.ConfirmDeletePasskey -> ConfirmDialog(
            title = stringResource(R.string.account_passkey_delete),
            enabled = !uiState.loading,
            onConfirm = { onUiEvent(UiEvent.DeletePasskeyConfirmed) },
            onDismiss = dismiss,
        ) {
            Text(stringResource(R.string.account_passkey_delete_confirm, dialog.name))
        }

        is Dialog.StepUp -> ConfirmDialog(
            title = stringResource(R.string.account_step_up_title),
            enabled = !uiState.loading && (dialog.hasPassword || dialog.needsCode),
            showConfirm = dialog.hasPassword || dialog.needsCode,
            onConfirm = { onUiEvent(UiEvent.StepUpConfirmed) },
            onDismiss = dismiss,
        ) {
            Text(stringResource(R.string.account_step_up_hint))
            if (dialog.hasPassword) {
                PasswordField(
                    value = dialog.password,
                    onValueChange = { onUiEvent(UiEvent.StepUpInput(it, dialog.code)) },
                    imeAction = if (dialog.needsCode) ImeAction.Next else ImeAction.Done,
                    onImeAction = { if (!dialog.needsCode) onUiEvent(UiEvent.StepUpConfirmed) },
                )
            }
            if (dialog.needsCode) {
                OneTimeCodeField(
                    value = dialog.code,
                    onValueChange = { onUiEvent(UiEvent.StepUpInput(dialog.password, it)) },
                    onImeAction = { onUiEvent(UiEvent.StepUpConfirmed) },
                )
            }
            if (dialog.passkeyAvailable) {
                OutlinedButton(
                    enabled = !uiState.loading,
                    onClick = { onUiEvent(UiEvent.StepUpWithPasskeyClicked) },
                ) {
                    Text(stringResource(R.string.account_step_up_with_passkey))
                }
            }
            dialog.error?.let { ErrorText(it.resolve()) }
        }
    }
}

@Composable
private fun TotpEnrollDialog(
    dialog: Dialog.TotpEnroll,
    loading: Boolean,
    onUiEvent: (UiEvent) -> Unit,
    onOpenUri: (String) -> Boolean,
) {
    val context = LocalContext.current
    val enrollment: TotpEnrollment = dialog.enrollment
    val focusRequester = remember { FocusRequester() }
    ConfirmDialog(
        title = stringResource(R.string.account_totp_enroll_title),
        confirmText = stringResource(R.string.auth_verify),
        enabled = !loading && dialog.code.length == 6,
        onConfirm = { onUiEvent(UiEvent.ConfirmTotpClicked) },
        onDismiss = { onUiEvent(UiEvent.DismissDialog) },
    ) {
        Text(stringResource(R.string.account_totp_enroll_hint), style = MaterialTheme.typography.bodyMedium)
        QrCode(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = enrollment.otpauthUri,
            contentDescription = stringResource(R.string.account_totp_qr_description),
        )
        val noApp = stringResource(R.string.account_totp_no_app)
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!onOpenUri(enrollment.otpauthUri)) {
                    Toast.makeText(context, noApp, Toast.LENGTH_LONG).show()
                }
            },
        ) {
            Text(stringResource(R.string.account_totp_open_app))
        }
        SelectionContainer {
            Text(
                text = stringResource(R.string.account_totp_secret, enrollment.secret.chunked(4).joinToString(" ")),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val copied = stringResource(R.string.account_copied)
            TextButton(onClick = { context.copyToClipboard(enrollment.secret, sensitive = true, copied) }) {
                Text(stringResource(R.string.account_totp_copy_secret))
            }
            TextButton(onClick = { context.copyToClipboard(enrollment.otpauthUri, sensitive = true, copied) }) {
                Text(stringResource(R.string.account_totp_copy_link))
            }
        }
        OneTimeCodeField(
            modifier = Modifier.focusRequester(focusRequester),
            value = dialog.code,
            onValueChange = { onUiEvent(UiEvent.TotpCodeChanged(it)) },
            onImeAction = { onUiEvent(UiEvent.ConfirmTotpClicked) },
        )
        dialog.error?.let { ErrorText(it.resolve()) }
    }
    LaunchedEffect(focusRequester) {
        // the code field keeps the focus, so a pasted or autofilled code lands there
        runCatching { focusRequester.requestFocus() }
    }
}

@Composable
private fun RecoveryCodesDialog(codes: List<String>, onShareText: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = codes.joinToString("\n")
    AlertDialog(
        // only the explicit button closes it, the codes are not shown again
        onDismissRequest = {},
        title = { Text(stringResource(R.string.account_recovery_codes)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.account_recovery_codes_hint), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text(text = text, fontFamily = FontFamily.Monospace)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val copied = stringResource(R.string.account_copied)
                    TextButton(onClick = { context.copyToClipboard(text, sensitive = true, copied) }) {
                        Text(stringResource(R.string.account_recovery_codes_copy))
                    }
                    TextButton(onClick = { onShareText(text) }) {
                        Text(stringResource(R.string.account_recovery_codes_share))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.account_recovery_codes_saved))
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.account_confirm),
    showConfirm: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        },
        confirmButton = {
            if (showConfirm) {
                Button(enabled = enabled, onClick = onConfirm) { Text(confirmText) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.account_cancel)) }
        },
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun relativeTime(instant: Instant?): String {
    if (instant == null) return stringResource(R.string.account_never)
    return DateUtils.getRelativeTimeSpanString(
        instant.toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}

/**
 * Copies [text]; sensitive content is hidden from the clipboard preview (Android 13+)
 */
private fun Context.copyToClipboard(text: String, sensitive: Boolean, confirmation: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(null, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show()
}

private const val ROLE_ADMIN = "admin"

/** ClipDescription.EXTRA_IS_SENSITIVE (API 33), older versions ignore it */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

@CombinedPreview
@Composable
private fun AccountScreenContentPreview() {
    MkDocsEditorTheme {
        AccountScreenContent(
            uiState = AccountViewModel.UiState(
                backendName = "wiki",
                account = AccountInfo(
                    id = "u1",
                    username = "markus",
                    displayName = "Markus",
                    role = ROLE_ADMIN,
                    hasPassword = true,
                    totpEnabled = true,
                    recoveryCodesLeft = 8,
                    passkeyCount = 1,
                ),
                passkeys = listOf(PasskeyInfo(id = "p1", name = "Phone", backedUp = true, provider = "Bitwarden")),
                sessions = listOf(SessionInfo(id = "s1", deviceName = "Pixel 9", current = true)),
                displayName = "Markus",
            ),
            onUiEvent = {},
            onBackClicked = {},
            onOpenUri = { true },
            onShareText = {},
        )
    }
}
