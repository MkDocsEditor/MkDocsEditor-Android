package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui

import kotlinx.serialization.Serializable

/**
 * The inputs of the form that have not been saved yet, kept across process death.
 *
 * Saved state is not encrypted, so this must never hold a password. An auth config is only
 * referenced by its id, and the inputs for a new one are not kept.
 */
@Serializable
internal data class BackendConfigDraft(
    val name: String = "",
    val description: String = "",
    val domain: String = "",
    val port: String = "",
    val useSsl: Boolean = false,
    val webDomain: String = "",
    val webPort: String = "",
    val webUseSsl: Boolean = false,
    val webAuthConfigId: Long? = null,
)

internal fun BackendConfigEditViewModel.UiState.toDraft() = BackendConfigDraft(
    name = name,
    description = description,
    domain = currentDomain,
    port = currentPort,
    useSsl = currentUseSsl,
    webDomain = currentMkDocsWebDomain,
    webPort = currentMkDocsWebPort,
    webUseSsl = currentMkDocsWebUseSsl,
    webAuthConfigId = currentMkDocsWebAuthConfig?.id,
)

internal fun BackendConfigEditViewModel.UiState.withDraft(draft: BackendConfigDraft) = copy(
    name = draft.name,
    description = draft.description,
    currentDomain = draft.domain,
    currentPort = draft.port,
    currentUseSsl = draft.useSsl,
    currentMkDocsWebDomain = draft.webDomain,
    currentMkDocsWebPort = draft.webPort,
    currentMkDocsWebUseSsl = draft.webUseSsl,
    // the auth config may have been deleted in the meantime
    currentMkDocsWebAuthConfig = authConfigs.find { it.id == draft.webAuthConfigId },
)
