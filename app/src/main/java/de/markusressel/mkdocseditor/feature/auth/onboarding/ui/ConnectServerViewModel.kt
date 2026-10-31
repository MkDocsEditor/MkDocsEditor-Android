package de.markusressel.mkdocseditor.feature.auth.onboarding.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.discovery.DiscoveryResult
import de.markusressel.mkdocseditor.auth.discovery.ServerDiscoverer
import de.markusressel.mkdocseditor.auth.link.AuthLinkParser
import de.markusressel.mkdocseditor.auth.link.ConnectLinkParser
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.auth.common.data.ServerSetup
import de.markusressel.mkdocseditor.feature.auth.common.data.UiMessage
import de.markusressel.mkdocseditor.feature.auth.common.data.toAuthMessage
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

/**
 * The first screen without a server: one "Server address" field. The address may be the web
 * client, the backend or a pasted connect or invite link; discovery fills in the rest.
 */
@HiltViewModel
internal class ConnectServerViewModel @Inject constructor(
    private val discovery: ServerDiscoverer,
    private val backends: AuthBackends,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<ConnectServerEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.AddressChanged -> _uiState.update { it.copy(address = event.text, error = null) }
                is UiEvent.AdvancedToggled -> _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
                is UiEvent.NameChanged -> _uiState.update { it.copy(name = event.text) }
                is UiEvent.DescriptionChanged -> _uiState.update { it.copy(description = event.text) }
                is UiEvent.WikiUsernameChanged -> _uiState.update { it.copy(wikiUsername = event.text) }
                is UiEvent.WikiPasswordChanged -> _uiState.update { it.copy(wikiPassword = event.text) }
                is UiEvent.ContinueClicked -> onContinue()
                is UiEvent.SetupLinkChanged -> _uiState.update { it.copy(setupLink = event.text, error = null) }
                is UiEvent.SetupLinkSubmitted -> onSetupLink()
                is UiEvent.BackToAddressClicked -> _uiState.update { it.copy(setupBackendId = null, error = null) }
            }
        }
    }

    private suspend fun onContinue() {
        val state = uiState.value
        val address = state.address.trim()
        if (address.isEmpty()) return

        // a link pasted into the address field works too
        if (ConnectLinkParser.isConnectLink(address)) {
            _events.send(ConnectServerEvent.OpenConnectLink(address))
            return
        }
        if (AuthLinkParser.parse(address)?.kind != null) {
            _events.send(ConnectServerEvent.OpenAuthLink(address, backendId = null))
            return
        }

        _uiState.update { it.copy(loading = true, error = null) }
        try {
            when (val result = discovery.discover(address)) {
                is DiscoveryResult.InvalidAddress -> showError(UiMessage(R.string.onboarding_error_address))
                is DiscoveryResult.Unreachable -> showError(UiMessage(R.string.onboarding_error_unreachable))
                is DiscoveryResult.NotMkDocsEditor -> showError(UiMessage(R.string.onboarding_error_not_mkdocseditor))
                is DiscoveryResult.Found -> {
                    val server = result.server
                    val wikiAuth = if (state.wikiUsername.isNotBlank() && state.wikiPassword.isNotEmpty()) {
                        AuthConfig(username = state.wikiUsername.trim(), password = state.wikiPassword)
                    } else null
                    val config = backends.connect(
                        ServerSetup(
                            backendUrl = server.backendUrl,
                            suggestedName = server.displayName,
                            name = state.name.takeIf { it.isNotBlank() },
                            description = state.description.takeIf { it.isNotBlank() },
                            wikiUrl = server.wikiUrl,
                            wikiAuth = wikiAuth,
                        )
                    )
                    when {
                        server.info.authDisabled -> _events.send(ConnectServerEvent.Done)
                        server.info.setupRequired -> _uiState.update {
                            it.copy(setupBackendId = config.id, serverName = config.name)
                        }

                        else -> _events.send(ConnectServerEvent.OpenLogin(config.id))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Adding the server failed")
            showError(e.toAuthMessage())
        } finally {
            _uiState.update { it.copy(loading = false) }
        }
    }

    private suspend fun onSetupLink() {
        val state = uiState.value
        val link = AuthLinkParser.parse(state.setupLink)
        if (link == null) {
            showError(UiMessage(R.string.auth_error_link_unreadable))
            return
        }
        _events.send(ConnectServerEvent.OpenAuthLink(state.setupLink.trim(), state.setupBackendId))
    }

    private fun showError(message: UiMessage) = _uiState.update { it.copy(error = message) }

    internal data class UiState(
        val address: String = "",
        val advancedExpanded: Boolean = false,
        val name: String = "",
        val description: String = "",
        val wikiUsername: String = "",
        val wikiPassword: String = "",
        val loading: Boolean = false,
        val error: UiMessage? = null,
        /** set when the server has no admin yet: it needs its setup link */
        val setupBackendId: Long? = null,
        val serverName: String = "",
        val setupLink: String = "",
    ) {
        override fun toString() = "UiState(address=$address, setupBackendId=$setupBackendId, loading=$loading, error=$error)"
    }

    internal sealed interface UiEvent {
        data class AddressChanged(val text: String) : UiEvent
        data object AdvancedToggled : UiEvent
        data class NameChanged(val text: String) : UiEvent
        data class DescriptionChanged(val text: String) : UiEvent
        data class WikiUsernameChanged(val text: String) : UiEvent
        data class WikiPasswordChanged(val text: String) : UiEvent {
            override fun toString() = "WikiPasswordChanged"
        }

        data object ContinueClicked : UiEvent
        data class SetupLinkChanged(val text: String) : UiEvent {
            override fun toString() = "SetupLinkChanged"
        }

        data object SetupLinkSubmitted : UiEvent
        data object BackToAddressClicked : UiEvent
    }

    internal sealed interface ConnectServerEvent {
        data class OpenLogin(val backendId: Long) : ConnectServerEvent
        data class OpenConnectLink(val link: String) : ConnectServerEvent {
            override fun toString() = "OpenConnectLink"
        }

        data class OpenAuthLink(val link: String, val backendId: Long?) : ConnectServerEvent {
            override fun toString() = "OpenAuthLink(backendId=$backendId)"
        }

        /** a server without authentication: nothing more to do */
        data object Done : ConnectServerEvent
    }
}
