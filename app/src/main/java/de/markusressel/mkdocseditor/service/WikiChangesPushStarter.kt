package de.markusressel.mkdocseditor.service

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesPushRegistration
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs [WikiChangesPushRegistration] at the start, after each sign-in, when another server is selected and when the
 * setting changes. A subscription belongs to the session that made it, so a new session subscribes again.
 */
@Singleton
internal class WikiChangesPushStarter @Inject constructor(
    private val authRepository: AuthRepository,
    private val backendConfigRepository: BackendConfigRepository,
    private val preferencesHolder: KutePreferencesHolder,
    private val registration: WikiChangesPushRegistration,
) {
    fun start(scope: CoroutineScope) {
        authRepository.doBeforeSignOut { registration.beforeSignOut(it.id) }
        scope.launch {
            combine(
                preferencesHolder.wikiChangeNotificationsPreference.persistedValue,
                backendConfigRepository.selectedBackendConfigFlow().map { it?.entityId }.distinctUntilChanged(),
                authRepository.sessionChanges.onStart { emit(NO_BACKEND) },
            ) { enabled, _, _ -> enabled }
                .collect { enabled -> registration.sync(enabled) }
        }
    }

    private companion object {
        const val NO_BACKEND = -1L
    }
}
