package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui

import kotlinx.serialization.Serializable

/**
 * What the user changed in the form, so it survives the system killing the app.
 *
 * Saved state is not encrypted, so this must never hold a password. After a restore the password is the stored one if
 * the username is still the stored one, and has to be typed again otherwise.
 */
@Serializable
internal data class BackendConfigDraft(
    val name: String = "",
    val description: String = "",
    val address: String = "",
    val webAddress: String = "",
    val wikiUsername: String = "",
)

internal fun BackendConfigEditViewModel.UiState.toDraft() = BackendConfigDraft(
    name = name,
    description = description,
    address = address,
    webAddress = webAddress,
    wikiUsername = wikiUsername,
)

internal fun BackendConfigEditViewModel.UiState.withDraft(draft: BackendConfigDraft) = copy(
    name = draft.name,
    description = draft.description,
    address = draft.address,
    webAddress = draft.webAddress,
    wikiUsername = draft.wikiUsername,
    wikiPassword = config?.mkDocsWebAuthConfig?.takeIf { it.username == draft.wikiUsername }?.password.orEmpty(),
)
