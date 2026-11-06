package de.markusressel.mkdocseditor.feature.dashboard.domain

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.dashboard.PinScope
import de.markusressel.mkdocsrestclient.dashboard.PinsModel
import timber.log.Timber
import javax.inject.Inject

/**
 * What the pin dialog of a page needs
 */
internal class PinActions @Inject constructor(
    private val restClient: IMkDocsRestClient,
    private val setPin: SetPinUseCase,
    val familyPermission: FamilyPinPermission,
) {
    /** @return the pins, null when they could not be loaded */
    suspend fun current(): PinsModel? = restClient.getPins().fold(
        success = { it },
        failure = {
            Timber.w(it, "Could not load the pins")
            null
        },
    )

    suspend fun set(scope: PinScope, documentId: String, pinned: Boolean): PinOutcome = setPin(scope, documentId, pinned)
}
