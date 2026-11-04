package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.ParsedBackendUrl
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.MkDocsWebConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.displayAddress
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendConfigsUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.DeleteAuthConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.DeleteBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.SaveBackendConfigItemsUseCase
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Edits a server that is already set up. A new one starts with the address only, see `ConnectServerScreen`.
 */
@HiltViewModel
internal class BackendConfigEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getBackendConfigUseCase: GetBackendConfigUseCase,
    private val getBackendConfigsUseCase: GetBackendConfigsUseCase,
    private val deleteAuthConfigUseCase: DeleteAuthConfigUseCase,
    private val saveBackendConfigUseCase: SaveBackendConfigItemsUseCase,
    private val deleteBackendConfigUseCase: DeleteBackendConfigUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** null until the user changed something, so a form without changes follows the database */
    private var draft by savedStateHandle.saved<BackendConfigDraft?> { null }

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<BackendEditEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    /** The screen initializes again when it returns, and that must not undo the user's changes */
    fun initialize(id: Long) {
        launch {
            try {
                val config = getBackendConfigUseCase(id)
                if (config == null) {
                    _events.send(BackendEditEvent.CloseScreen)
                    return@launch
                }
                _uiState.update { old ->
                    val loaded = old.withConfig(config).copy(signedInUsername = signedInUsername(id))
                    draft?.let { loaded.withDraft(it) } ?: loaded
                }
            } catch (ex: Exception) {
                Timber.e(ex)
                showMessage(ex.userMessage())
            }
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.NameChanged -> edit { it.copy(name = event.text) }
                is UiEvent.DescriptionChanged -> edit { it.copy(description = event.text) }
                is UiEvent.AddressChanged -> edit { it.copy(address = event.text) }
                is UiEvent.WebAddressChanged -> edit { it.copy(webAddress = event.text) }
                is UiEvent.WikiUsernameChanged -> edit { it.copy(wikiUsername = event.text) }
                is UiEvent.WikiPasswordChanged -> edit { it.copy(wikiPassword = event.text) }

                is UiEvent.AdvancedToggled -> _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
                is UiEvent.MessageShown -> _uiState.update { it.copy(message = null) }

                is UiEvent.SaveClicked -> save()
                is UiEvent.DeleteClicked -> {
                    val unsyncedPages = uiState.value.config?.let { deleteBackendConfigUseCase.unsyncedPages(it) } ?: 0
                    _uiState.update { it.copy(confirmingDelete = true, unsyncedPagesOnDelete = unsyncedPages) }
                }

                is UiEvent.DeleteDismissed -> _uiState.update { it.copy(confirmingDelete = false) }
                is UiEvent.DeleteConfirmed -> delete()

                is UiEvent.SignInClicked -> signIn()
                is UiEvent.SignOutClicked -> signOut()
            }
        }
    }

    private fun edit(change: (UiState) -> UiState) {
        _uiState.update(change)
        draft = uiState.value.toDraft()
    }

    private suspend fun save() {
        val state = uiState.value
        val config = state.config ?: return
        val url = ParsedBackendUrl.parse(state.address) ?: return
        val webUrl = state.webAddress.takeIf { it.isNotBlank() }?.let { ParsedBackendUrl.parse(it) ?: return }
        try {
            val oldWikiAuth = config.mkDocsWebAuthConfig
            val wikiAuth = when {
                state.wikiUsername.isBlank() -> null
                oldWikiAuth?.username == state.wikiUsername && oldWikiAuth.password == state.wikiPassword -> oldWikiAuth
                // always a new one, because another server may use the old one
                else -> AuthConfig(username = state.wikiUsername, password = state.wikiPassword)
            }
            val saved = config.copy(
                name = state.name.trim(),
                description = state.description.trim(),
                serverConfig = BackendServerConfig(
                    id = config.serverConfig?.id ?: 0,
                    domain = url.host,
                    port = url.port,
                    useSsl = url.useSsl,
                ),
                mkDocsWebConfig = webUrl?.let {
                    MkDocsWebConfig(
                        id = config.mkDocsWebConfig?.id ?: 0,
                        domain = it.host,
                        port = it.port,
                        useSsl = it.useSsl,
                    )
                },
                mkDocsWebAuthConfig = wikiAuth,
            )
            saveBackendConfigUseCase(saved)
            if (oldWikiAuth != null && oldWikiAuth != wikiAuth) deleteIfUnused(oldWikiAuth)
            draft = null
            if (needsSignIn(saved)) {
                _events.send(BackendEditEvent.OpenLogin(saved.id, replaceScreen = true))
            } else {
                _events.send(BackendEditEvent.CloseScreen)
            }
        } catch (ex: Exception) {
            Timber.e(ex) { "Failed to save the server" }
            showMessage(R.string.server_save_failed)
        }
    }

    private suspend fun deleteIfUnused(authConfig: AuthConfig) {
        val inUse = getBackendConfigsUseCase().any { it.mkDocsWebAuthConfig?.id == authConfig.id }
        if (!inUse) deleteAuthConfigUseCase(authConfig.id)
    }

    private suspend fun delete() {
        val config = uiState.value.config ?: return
        _uiState.update { it.copy(confirmingDelete = false) }
        try {
            deleteBackendConfigUseCase(config)
            _events.send(BackendEditEvent.CloseScreen)
        } catch (ex: Exception) {
            Timber.e(ex)
            showMessage(R.string.server_delete_failed)
        }
    }

    private fun signedInUsername(id: Long): String? = authRepository.session(id)?.username?.ifBlank { "?" }

    /** A server needs a sign-in unless it runs without authentication */
    private suspend fun needsSignIn(config: BackendConfig): Boolean {
        val backend = config.toBackendRef() ?: return false
        if (authRepository.isSignedIn(backend.id)) return false
        return try {
            !authRepository.status(backend).authDisabled
        } catch (e: Exception) {
            // unreachable or an old backend: the login screen explains it
            Timber.w(e) { "Could not read the auth status" }
            true
        }
    }

    private suspend fun signIn() {
        val id = uiState.value.config?.id ?: return
        _events.send(BackendEditEvent.OpenLogin(id, replaceScreen = false))
    }

    private suspend fun signOut() {
        val config = uiState.value.config ?: return
        val backend = config.toBackendRef() ?: return
        authRepository.signOut(backend)
        _uiState.update { it.copy(signedInUsername = signedInUsername(config.id)) }
        showMessage(R.string.server_signed_out)
    }

    private fun showMessage(@StringRes message: Int) {
        _uiState.update { it.copy(message = message) }
    }

    internal sealed class UiEvent {
        data object SaveClicked : UiEvent()
        data object DeleteClicked : UiEvent()
        data object DeleteDismissed : UiEvent()
        data object DeleteConfirmed : UiEvent()
        data object SignInClicked : UiEvent()
        data object SignOutClicked : UiEvent()
        data object AdvancedToggled : UiEvent()
        data object MessageShown : UiEvent()

        data class NameChanged(val text: String) : UiEvent()
        data class DescriptionChanged(val text: String) : UiEvent()
        data class AddressChanged(val text: String) : UiEvent()
        data class WebAddressChanged(val text: String) : UiEvent()
        data class WikiUsernameChanged(val text: String) : UiEvent()

        data class WikiPasswordChanged(val text: String) : UiEvent() {
            override fun toString() = "WikiPasswordChanged"
        }
    }

    /**
     * @param config the server as it is stored
     * @param signedInUsername the user of the stored session, null when signed out
     * @param message a short note for the snackbar
     */
    internal data class UiState(
        val config: BackendConfig? = null,
        val name: String = "",
        val description: String = "",
        val address: String = "",
        val webAddress: String = "",
        val wikiUsername: String = "",
        val wikiPassword: String = "",
        val signedInUsername: String? = null,
        val advancedExpanded: Boolean = false,
        val confirmingDelete: Boolean = false,
        val unsyncedPagesOnDelete: Int = 0,
        @param:StringRes val message: Int? = null,
    ) {
        val addressIsValid: Boolean get() = ParsedBackendUrl.parse(address) != null
        val webAddressIsValid: Boolean get() = webAddress.isBlank() || ParsedBackendUrl.parse(webAddress) != null
        val canSave: Boolean get() = config != null && name.isNotBlank() && addressIsValid && webAddressIsValid

        override fun toString() = "UiState(name=$name, address=$address, webAddress=$webAddress)"
    }

    internal sealed class BackendEditEvent {
        data object CloseScreen : BackendEditEvent()

        /**
         * @param replaceScreen true to replace this screen with the login (after saving)
         */
        data class OpenLogin(val backendId: Long, val replaceScreen: Boolean) : BackendEditEvent()
    }
}

internal fun BackendConfigEditViewModel.UiState.withConfig(config: BackendConfig) = copy(
    config = config,
    name = config.name,
    description = config.description,
    address = config.serverConfig?.run { displayAddress(domain, port, useSsl) }.orEmpty(),
    webAddress = config.mkDocsWebConfig?.run { displayAddress(domain, port, useSsl) }.orEmpty(),
    wikiUsername = config.mkDocsWebAuthConfig?.username.orEmpty(),
    wikiPassword = config.mkDocsWebAuthConfig?.password.orEmpty(),
)
