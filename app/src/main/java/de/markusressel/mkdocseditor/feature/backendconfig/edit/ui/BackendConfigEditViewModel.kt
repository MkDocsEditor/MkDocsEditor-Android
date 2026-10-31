package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.MkDocsWebConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendAuthConfigsUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.ValidateEditedBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.ValidateNewBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.AddAuthConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.DeleteAuthConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.DeleteBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.SaveBackendConfigItemsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject


@HiltViewModel
internal class BackendConfigEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getBackendConfigUseCase: GetBackendConfigUseCase,
    private val getBackendAuthConfigsUseCase: GetBackendAuthConfigsUseCase,
    private val addAuthConfigUseCase: AddAuthConfigUseCase,
    private val deleteAuthConfigUseCase: DeleteAuthConfigUseCase,
    private val validateNewBackendConfigUseCase: ValidateNewBackendConfigUseCase,
    private val validateEditedBackendConfigUseCase: ValidateEditedBackendConfigUseCase,
    private val saveBackendConfigUseCase: SaveBackendConfigItemsUseCase,
    private val deleteBackendConfigUseCase: DeleteBackendConfigUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    // TODO: use savedState
    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<BackendEditEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    fun initialize(id: Long?) {
        launch {
            try {
                updateAuthConfigs()
                if (id == null) {
                    clearInputs()
                } else {
                    loadBackendConfig(id)
                }
            } catch (e: Exception) {
                Timber.e(e)
                showError("Failed to load backend config")
            }
        }
    }

    private fun clearInputs() {
        _uiState.update { old ->
            old.copy(
                name = "",
                description = "",
                authConfigEditMode = false,
                isDeleteButtonEnabled = false,
            )
        }
    }

    private suspend fun loadBackendConfig(id: Long) {
        val data = getBackendConfigUseCase(id)
        if (data == null) {
            showError("Backend config not found")
            return
        }

        _uiState.update { old ->
            old.copy(
                currentBackendConfig = data,

                name = data.name,
                description = data.description,

                currentDomain = data.serverConfig?.domain ?: "",
                currentPort = data.serverConfig?.port?.toString() ?: "",
                currentUseSsl = data.serverConfig?.useSsl ?: false,

                currentMkDocsWebDomain = data.mkDocsWebConfig?.domain ?: "",
                currentMkDocsWebPort = data.mkDocsWebConfig?.port?.toString() ?: "",
                currentMkDocsWebUseSsl = data.mkDocsWebConfig?.useSsl ?: false,

                currentMkDocsWebAuthConfig = data.mkDocsWebAuthConfig,

                isDeleteButtonEnabled = true,
            )
        }
        updateSessionState()

        updateSaveButtonEnabled()
    }

    private suspend fun updateAuthConfigs() {
        val authConfigs = getBackendAuthConfigsUseCase()
        _uiState.update { old ->
            old.copy(
                authConfigs = authConfigs,
            )
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.NameChanged -> processNameInput(event.text)
                is UiEvent.DescriptionChanged -> processDescriptionInput(event.text)

                is UiEvent.DomainChanged -> processDomainInput(event.text)
                is UiEvent.PortChanged -> processPortInput(event.port)
                is UiEvent.UseSslChanged -> processUseSslInput(event.checked)

                is UiEvent.MkDocsWebDomainChanged -> processWebDomainInput(event.text)
                is UiEvent.MkDocsWebPortChanged -> processWebPortInput(event.port)
                is UiEvent.MkDocsWebUseSslChanged -> processWebUseSslInput(event.checked)
                is UiEvent.MkDocsWebAuthConfigSelectionChanged -> processMkDocsWebAuthConfigSelectionChange(
                    event.authConfig
                )

                is UiEvent.MkDocsWebAuthConfigAddButtonClicked -> enableAuthConfigEditMode()
                is UiEvent.MkDocsWebAuthConfigAbortButtonClicked -> disableAuthConfigEditMode()
                is UiEvent.MkDocsWebAuthConfigDeleteButtonClicked -> deleteAuthConfig(event.authConfig)
                is UiEvent.MkDocsWebAuthConfigUsernameInputChanged -> processAuthConfigUsernameInput(
                    event.input
                )

                is UiEvent.MkDocsWebAuthConfigPasswordInputChanged -> processAuthConfigPasswordInput(
                    event.input
                )

                is UiEvent.MkDocsWebSaveButtonClicked -> addAuthConfigFromCurrentInputs()

                is UiEvent.SaveClicked -> save()
                is UiEvent.DeleteClicked -> delete()
                is UiEvent.SignInClicked -> signIn()
                is UiEvent.SignOutClicked -> signOut()
            }
        }
    }

    private suspend fun delete() {
        try {
            deleteBackendConfigUseCase(requireNotNull(uiState.value.currentBackendConfig))
            _events.send(BackendEditEvent.CloseScreen)
        } catch (ex: Exception) {
            Timber.e(ex)
            showError("Failed to delete backend config")
        }
    }

    private suspend fun processDomainInput(text: String) {
        _uiState.update { old ->
            old.copy(currentDomain = text)
        }
        updateSaveButtonEnabled()
    }

    private suspend fun processPortInput(port: String) {
        _uiState.update { old ->
            old.copy(currentPort = port)
        }
        updateSaveButtonEnabled()
    }

    private suspend fun processUseSslInput(checked: Boolean) {
        _uiState.update { old ->
            old.copy(currentUseSsl = checked)
        }
        updateSaveButtonEnabled()
    }

    private suspend fun processWebDomainInput(text: String) {
        _uiState.update { old ->
            old.copy(currentMkDocsWebDomain = text)
        }
        updateSaveButtonEnabled()
    }

    private suspend fun processWebPortInput(port: String) {
        _uiState.update { old ->
            old.copy(currentMkDocsWebPort = port)
        }
        updateSaveButtonEnabled()
    }

    private suspend fun processWebUseSslInput(checked: Boolean) {
        _uiState.update { old ->
            old.copy(currentMkDocsWebUseSsl = checked)
        }
        updateSaveButtonEnabled()
    }

    private suspend fun deleteAuthConfig(authConfig: AuthConfig) {
        try {
            deleteAuthConfigUseCase(authConfig.id)
            updateAuthConfigs()
            showInfo("Auth config deleted")
        } catch (e: Exception) {
            Timber.e(e)
            showError("Failed to delete auth config")
        }
    }

    private suspend fun showInfo(text: String) {
        _events.send(BackendEditEvent.Info(text))
    }

    private fun enableAuthConfigEditMode() {
        // TODO: needs modifications to support editing existing auth config
        _uiState.update { old ->
            old.copy(
                authConfigEditMode = true,
                currentAuthConfigUsername = old.currentMkDocsWebAuthConfig?.username ?: "",
                currentAuthConfigPassword = old.currentMkDocsWebAuthConfig?.password ?: "",
                authConfigSaveButtonEnabled = validateAuthConfig(
                    old.currentAuthConfigUsername,
                    old.currentAuthConfigPassword
                )
            )
        }
    }

    private fun disableAuthConfigEditMode() {
        _uiState.update { old ->
            old.copy(
                authConfigEditMode = false,
                currentAuthConfigUsername = "",
                currentAuthConfigPassword = "",
                authConfigSaveButtonEnabled = false,
            )
        }
    }

    private suspend fun addAuthConfigFromCurrentInputs() {
        val authConfig = AuthConfig(
            username = uiState.value.currentAuthConfigUsername,
            password = uiState.value.currentAuthConfigPassword,
        )
        val newConfigId = addAuthConfigUseCase(authConfig)
        val newAuthConfigs = getBackendAuthConfigsUseCase()
        _uiState.update { old ->
            old.copy(
                authConfigs = newAuthConfigs,
                currentMkDocsWebAuthConfig = newAuthConfigs.find { it.id == newConfigId },
                authConfigEditMode = false,
                currentAuthConfigUsername = "",
                currentAuthConfigPassword = "",
            )
        }
    }

    private suspend fun processDescriptionInput(text: String) {
        _uiState.update { old ->
            old.copy(description = text)
        }
        updateSaveButtonEnabled()
    }

    private suspend fun updateSaveButtonEnabled() {
        _uiState.update { old ->
            old.copy(
                saveButtonEnabled = validateCurrentInput()
            )
        }
    }

    private suspend fun save() {
        val isValid = validateCurrentInput()
        if (!isValid) {
            showError("Backend config is not valid")
        } else {
            try {
                val serverConfig = BackendServerConfig(
                    id = uiState.value.currentBackendConfig?.serverConfig?.id ?: 0,
                    domain = uiState.value.currentDomain,
                    port = uiState.value.currentPort.takeIf { it.isNotBlank() }?.toInt() ?: 8123,
                    useSsl = uiState.value.currentUseSsl,
                )

                val mkDocsWebConfig = MkDocsWebConfig(
                    id = uiState.value.currentBackendConfig?.mkDocsWebConfig?.id ?: 0,
                    domain = uiState.value.currentMkDocsWebDomain,
                    port = uiState.value.currentMkDocsWebPort.takeIf { it.isNotBlank() }?.toInt() ?: 80,
                    useSsl = uiState.value.currentMkDocsWebUseSsl,
                )

                val config = BackendConfig(
                    id = uiState.value.currentBackendConfig?.id ?: 0,
                    name = uiState.value.name,
                    description = uiState.value.description,
                    isSelected = false,
                    serverConfig = serverConfig,
                    mkDocsWebConfig = mkDocsWebConfig,
                    mkDocsWebAuthConfig = uiState.value.currentMkDocsWebAuthConfig,
                )
                val id = saveBackendConfigUseCase(config)
                val saved = config.copy(id = id)
                if (needsSignIn(saved)) {
                    _events.send(BackendEditEvent.OpenLogin(id, replaceScreen = true))
                } else {
                    _events.send(BackendEditEvent.CloseScreen)
                }
            } catch (e: Exception) {
                Timber.e(e) { "Failed to save backend config" }
                showError("Failed to save backend config")
            }
        }
    }

    private suspend fun validateCurrentInput(): Boolean {
        uiState.value.apply {
            return when (uiState.value.currentBackendConfig) {
                null -> validateNewBackendConfigUseCase(
                    name = name,
                    description = description,
                    domain = currentDomain,
                    port = currentPort,
                    currentUseSsl = currentUseSsl,
                )

                else -> validateEditedBackendConfigUseCase(
                    name = name,
                    description = description,
                    domain = currentDomain,
                    port = currentPort,
                )
            }
        }
    }

    private suspend fun processMkDocsWebAuthConfigSelectionChange(authConfig: AuthConfig?) {
        _uiState.update { old ->
            old.copy(currentMkDocsWebAuthConfig = authConfig)
        }
        updateSaveButtonEnabled()
    }

    private fun processAuthConfigUsernameInput(input: String) {
        _uiState.update { old ->
            old.copy(
                currentAuthConfigUsername = input,
                authConfigSaveButtonEnabled = validateAuthConfig(
                    input,
                    old.currentAuthConfigPassword
                )
            )
        }
    }

    private fun processAuthConfigPasswordInput(input: String) {
        _uiState.update { old ->
            old.copy(
                currentAuthConfigPassword = input,
                authConfigSaveButtonEnabled = validateAuthConfig(
                    old.currentAuthConfigUsername,
                    input
                )
            )
        }
    }

    private fun validateAuthConfig(username: String, password: String): Boolean {
        return username.isNotBlank() && password.isNotBlank()
    }

    private suspend fun processNameInput(text: String) {
        _uiState.update { old ->
            old.copy(name = text)
        }
        updateSaveButtonEnabled()
    }

    private fun updateSessionState() {
        val id = uiState.value.currentBackendConfig?.id ?: return
        _uiState.update { old ->
            old.copy(signedInUsername = authRepository.session(id)?.username?.ifBlank { "?" })
        }
    }

    /**
     * A new backend needs a sign-in unless its server runs without authentication
     */
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
        val id = uiState.value.currentBackendConfig?.id ?: return
        _events.send(BackendEditEvent.OpenLogin(id, replaceScreen = false))
    }

    private suspend fun signOut() {
        val backend = uiState.value.currentBackendConfig?.toBackendRef() ?: return
        authRepository.signOut(backend)
        updateSessionState()
        showInfo("Signed out")
    }

    private suspend fun showError(s: String) {
        _events.send(BackendEditEvent.Error(s))
    }


    internal sealed class UiEvent {
        data object SaveClicked : UiEvent()
        data object DeleteClicked : UiEvent()
        data object SignInClicked : UiEvent()
        data object SignOutClicked : UiEvent()

        data class NameChanged(val text: String) : UiEvent()
        data class DescriptionChanged(val text: String) : UiEvent()

        data class DomainChanged(val text: String) : UiEvent()
        data class PortChanged(val port: String) : UiEvent()
        data class UseSslChanged(val checked: Boolean) : UiEvent()

        data class MkDocsWebDomainChanged(val text: String) : UiEvent()
        data class MkDocsWebPortChanged(val port: String) : UiEvent()
        data class MkDocsWebUseSslChanged(val checked: Boolean) : UiEvent()

        data class MkDocsWebAuthConfigSelectionChanged(val authConfig: AuthConfig?) : UiEvent()
        data object MkDocsWebAuthConfigAddButtonClicked : UiEvent()
        data object MkDocsWebAuthConfigAbortButtonClicked : UiEvent()
        data class MkDocsWebAuthConfigDeleteButtonClicked(val authConfig: AuthConfig) : UiEvent()
        data class MkDocsWebAuthConfigUsernameInputChanged(val input: String) : UiEvent()
        data class MkDocsWebAuthConfigPasswordInputChanged(val input: String) : UiEvent()
        data object MkDocsWebSaveButtonClicked : UiEvent()
    }

    internal data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,

        val currentBackendConfig: BackendConfig? = null,

        val serverConfigs: List<BackendServerConfig> = emptyList(),
        val authConfigs: List<AuthConfig> = emptyList(),

        val name: String = "",
        val description: String = "",

        val currentDomain: String = "",
        val currentPort: String = "",
        val currentUseSsl: Boolean = false,

        /** the user of the stored session, null when signed out */
        val signedInUsername: String? = null,

        val currentMkDocsWebDomain: String = "",
        val currentMkDocsWebPort: String = "",
        val currentMkDocsWebUseSsl: Boolean = false,
        val currentMkDocsWebAuthConfig: AuthConfig? = null,

        val authConfigEditMode: Boolean = false,
        val currentAuthConfigUsername: String = "",
        val currentAuthConfigPassword: String = "",
        val authConfigSaveButtonEnabled: Boolean = false,

        val saveButtonEnabled: Boolean = false,
        val isDeleteButtonEnabled: Boolean = false,
    )

    internal sealed class BackendEditEvent {
        data object CloseScreen : BackendEditEvent()
        data class Error(val message: String) : BackendEditEvent()
        data class Info(val message: String) : BackendEditEvent()

        /**
         * @param replaceScreen true to replace this screen with the login (after saving)
         */
        data class OpenLogin(val backendId: Long, val replaceScreen: Boolean) : BackendEditEvent()
    }
}
