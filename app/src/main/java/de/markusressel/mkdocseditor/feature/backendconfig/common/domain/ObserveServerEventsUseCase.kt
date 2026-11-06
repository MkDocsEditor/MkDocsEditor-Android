package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.eventsWebsocketUrl
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.sync.events.ServerEvent
import de.markusressel.mkdocsrestclient.sync.events.serverEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ObserveServerEventsUseCase @Inject constructor(
    private val getCurrentBackendConfigFlowUseCase: GetCurrentBackendConfigFlowUseCase,
    private val authRepository: AuthRepository,
) {
    /**
     * Emits whenever something on the selected server may have changed, see [ServerEvent]. Each new session connects
     * again.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke(): Flow<ServerEvent> = combine(
        getCurrentBackendConfigFlowUseCase(),
        authRepository.sessionChanges.map { }.onStart { emit(Unit) },
    ) { config, _ -> config }
        .flatMapLatest { config ->
            val server = config?.serverConfig ?: return@flatMapLatest emptyFlow()
            serverEvents(
                url = server.eventsWebsocketUrl,
                authorizationProvider = AuthorizationProvider.bearer { authRepository.token(config.id) },
                onRefused = { authRepository.onUnauthenticated(config.id) },
            )
        }

    /** Emits whenever the tree of the selected server may have changed */
    suspend fun treeChanges(): Flow<Unit> =
        invoke().filter { it == ServerEvent.Connected || it == ServerEvent.TreeChanged }.map { }
}
