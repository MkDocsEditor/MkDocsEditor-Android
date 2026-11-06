package de.markusressel.mkdocseditor.feature.dashboard.domain

import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.dashboard.PinScope
import de.markusressel.mkdocsrestclient.dashboard.PinsModel
import javax.inject.Inject

internal sealed interface PinOutcome {
    data class Changed(val pins: PinsModel) : PinOutcome

    /** only editors and admins pin for the whole family */
    data object Forbidden : PinOutcome
}

/**
 * Pins a page, or removes its pin
 */
internal class SetPinUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    /** @throws com.github.kittinunf.fuel.core.FuelError for every other failure */
    suspend operator fun invoke(scope: PinScope, documentId: String, pinned: Boolean): PinOutcome {
        val result = if (pinned) restClient.pin(scope, documentId) else restClient.unpin(scope, documentId)
        return when (result) {
            is Result.Success -> PinOutcome.Changed(result.value)
            is Result.Failure -> if (result.error.response.statusCode == HTTP_FORBIDDEN) PinOutcome.Forbidden else throw result.error
        }
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}
