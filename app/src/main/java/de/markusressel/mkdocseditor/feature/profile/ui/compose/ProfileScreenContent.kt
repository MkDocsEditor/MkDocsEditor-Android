package de.markusressel.mkdocseditor.feature.profile.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.profile.domain.model.ProfileData
import de.markusressel.mkdocseditor.feature.profile.ui.ProfileViewModel
import de.markusressel.mkdocseditor.feature.profile.ui.ProfileViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun ProfileScreenContent(
    uiState: ProfileViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(id = R.string.screen_profile_title),
            )
        },
        floatingActionButton = {
            ExpandableFab(
                modifier = Modifier.fillMaxSize(),
                items = uiState.fabConfig.right,
                onItemClicked = {
                    onUiEvent(UiEvent.ExpandableFabItemSelected(item = it))
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
        ) {
            ProfileSection(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                uiState = uiState, onUiEvent = onUiEvent
            )
            BackendSection(uiState = uiState, onUiEvent = onUiEvent)
        }
    }
}

@Composable
internal fun BackendSection(
    uiState: ProfileViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = "Backend",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )

        uiState.backendConfigs.forEach { backendConfig ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = backendConfig.name,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun ProfileSection(
    uiState: ProfileViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        uiState.activeProfile?.let { profile ->
            Avatar(
                modifier = Modifier.padding(16.dp),
                profile = profile,
            )
        }

        if (uiState.profilesExpanded) {
            uiState.profiles.forEach { profile ->
                Avatar(
                    modifier = Modifier.padding(16.dp),
                    profile = profile,
                )
            }
        }

        Button(onClick = { onUiEvent(UiEvent.SwitchProfileClicked) }) {
            Text("Switch Profile")
        }
    }
}

@Composable
fun Avatar(
    profile: ProfileData,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(100))
                .zIndex(0f),
            painter = painterResource(id = R.drawable.app_icon_no_padding),
            contentDescription = null,
        )

        AssistChip(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(28.dp)
                .offset(y = 8.dp)
                .clip(RoundedCornerShape(100))
                .background(color = MaterialTheme.colorScheme.primary)
                .zIndex(1f),
            onClick = { },
            label = {
                Text(
                    text = profile.name,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        )
    }
}

@CombinedPreview
@Composable
internal fun ProfileScreenContentPreview() {
    MkDocsEditorTheme {
        ProfileScreenContent(
            uiState = ProfileViewModel.UiState(
                activeProfile = ProfileData(
                    name = "Markus",
                    active = true,
                ),
                profiles = listOf(
                    ProfileData(
                        name = "Markus",
                        active = true,
                    ),
                    ProfileData(
                        name = "Iris",
                        active = true,
                    )
                ),
                backendConfigs = listOf(
                    BackendConfig(
                        name = "Backend 1",
                        description = "Description 1",
                        isSelected = true,
                        backendAuthConfig = null,
                        id = 0,
                        mkDocsWebAuthConfig = null,
                        mkDocsWebConfig = null,
                        serverConfig = null,
                    )
                ),
            ),
            onUiEvent = {}
        )
    }
}