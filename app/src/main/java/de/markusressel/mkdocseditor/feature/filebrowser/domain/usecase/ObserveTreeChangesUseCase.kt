package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.eventsWebsocketUrl
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigFlowUseCase
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.sync.events.treeChanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ObserveTreeChangesUseCase @Inject constructor(
    private val getCurrentBackendConfigFlowUseCase: GetCurrentBackendConfigFlowUseCase,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val authRepository: AuthRepository,
) {
    /** Emits whenever the tree of the selected server may have changed. Each new session connects again. */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke(): Flow<Unit> = combine(
        getCurrentBackendConfigFlowUseCase(),
        isOfflineModeEnabledFlowUseCase(),
        authRepository.sessionChanges.map { }.onStart { emit(Unit) },
    ) { config, offline, _ -> config.takeUnless { offline } }
        .flatMapLatest { config ->
            val server = config?.serverConfig ?: return@flatMapLatest emptyFlow()
            treeChanges(
                url = server.eventsWebsocketUrl,
                authorizationProvider = AuthorizationProvider.bearer { authRepository.token(config.id) },
                onRefused = { authRepository.onUnauthenticated(config.id) },
            )
        }
}
