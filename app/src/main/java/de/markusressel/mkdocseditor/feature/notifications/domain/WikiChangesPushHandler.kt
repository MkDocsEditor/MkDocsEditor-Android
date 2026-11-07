package de.markusressel.mkdocseditor.feature.notifications.domain

import de.markusressel.mkdocseditor.auth.api.PushMessages
import timber.log.Timber
import javax.inject.Inject

/** Runs [CheckWikiChangesUseCase] soon, the same check as the polling, so its mark keeps a change from notifying twice */
internal fun interface WikiChangesCheck {
    fun checkSoon()
}

/** What to do with a push message of the backend */
internal class WikiChangesPushHandler @Inject constructor(
    private val check: WikiChangesCheck,
) {
    /** @param decrypted false if the connector could not decrypt it, so it is not from the backend */
    fun onMessage(payload: ByteArray, decrypted: Boolean) {
        if (!decrypted) {
            Timber.w("Dropped a push message that could not be decrypted")
            return
        }
        // other types are for newer clients
        if (PushMessages.type(payload) == PushMessages.FEED_CHANGED) check.checkSoon()
    }
}
