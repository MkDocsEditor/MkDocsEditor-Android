package de.markusressel.mkdocseditor.feature.profile.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.profile.domain.GetActiveProfileUseCase
import de.markusressel.mkdocseditor.feature.profile.domain.GetProfilesUseCase
import de.markusressel.mkdocseditor.feature.profile.domain.model.ProfileData
import de.markusressel.mkdocseditor.ui.activity.SnackbarData
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getActiveProfileUseCase: GetActiveProfileUseCase,
    private val getProfilesUseCase: GetProfilesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    internal val events = MutableLiveData<UiEvent>()

    init {
        launch {
            val profiles = getProfilesUseCase()
            val activeProfile = getActiveProfileUseCase()

            _uiState.update { old ->
                old.copy(
                    activeProfile = activeProfile,
                    profiles = profiles,
                    fabConfig = FabConfig(
                        left = listOf(),
                    ),
                )
            }
        }

    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.TopAppBarActionClicked -> onTopAppBarActionClicked(event.action)
                is UiEvent.ExpandableFabItemSelected -> when (event.item.id) {
                    ProfileFabId.EnableEditMode -> TODO()
                    ProfileFabId.DisableEditMode -> TODO()
                }

                is UiEvent.BackPressed -> {}
                is UiEvent.SnackbarActionClicked -> onSnackbarAction(event.snackbar)
            }
        }
    }

    private fun showSnackbar(snackbar: SnackbarData?) {
        _uiState.update { old ->
            old.copy(snackbar = snackbar)
        }
    }

    private suspend fun onTopAppBarActionClicked(action: TopAppBarAction.CodeEditor) {
//        when (action) {
//            is TopAppBarAction.CodeEditor.ShowInBrowserAction -> onOpenInBrowserClicked()
//        }
    }

    private fun onSnackbarAction(snackbar: SnackbarData) {
    }

    private fun dismissSnackbar() = showSnackbar(null)

    data class UiState(
        val fabConfig: FabConfig<ProfileFabId> = FabConfig(),
        val loading: Boolean = false,
        val activeProfile: ProfileData? = null,
        val profiles: List<ProfileData> = emptyList(),
        val snackbar: SnackbarData? = null,
    )

    sealed class UiEvent {
        data class TopAppBarActionClicked(val action: TopAppBarAction.CodeEditor) : UiEvent()

        data class ExpandableFabItemSelected(val item: FabConfig.Fab<ProfileFabId>) : UiEvent()
        data class SnackbarActionClicked(val snackbar: SnackbarData) : UiEvent()

        data object BackPressed : UiEvent()
    }

    sealed class ProfileFabId {
        data object EnableEditMode : ProfileFabId()
        data object DisableEditMode : ProfileFabId()
    }
}
