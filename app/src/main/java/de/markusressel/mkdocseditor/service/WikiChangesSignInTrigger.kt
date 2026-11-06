package de.markusressel.mkdocseditor.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells which session changes are a sign-in. A stored session also changes when its token is renewed, and that is no
 * reason to check.
 */
internal class SignIns(private val isSignedIn: (backendId: Long) -> Boolean) {
    private val signedIn = mutableSetOf<Long>()

    /** @return true if [backendId] has a session now and had none before */
    fun onSessionChanged(backendId: Long): Boolean =
        if (isSignedIn(backendId)) signedIn.add(backendId) else {
            signedIn.remove(backendId)
            false
        }
}

/** Starts [WikiChangesWorker.checkNow] after each sign-in */
@Singleton
internal class WikiChangesSignInTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val preferencesHolder: KutePreferencesHolder,
) {
    fun start(scope: CoroutineScope) {
        val signIns = SignIns(authRepository::isSignedIn)
        scope.launch {
            authRepository.sessionChanges.collect { backendId ->
                val signedIn = signIns.onSessionChanged(backendId)
                if (signedIn && preferencesHolder.wikiChangeNotificationsPreference.persistedValue.value) {
                    WikiChangesWorker.checkNow(context)
                }
            }
        }
    }
}
