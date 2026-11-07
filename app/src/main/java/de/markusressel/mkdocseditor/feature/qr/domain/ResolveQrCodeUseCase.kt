package de.markusressel.mkdocseditor.feature.qr.domain

import androidx.annotation.StringRes
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import de.markusressel.mkdocsrestclient.BackendNotConfiguredException
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.qr.QrErrorModel
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import javax.inject.Inject

/** A page that has a QR code key */
internal data class QrPage(val documentId: String, val title: String, val path: String)

/** What the selected server says about a QR code key */
internal sealed interface QrLookup {
    data class Found(val page: QrPage) : QrLookup

    /** more than one page has the key */
    data class Conflict(val pages: List<QrPage>) : QrLookup

    /**
     * No page has the key: it was removed from its page ([lastPath] is where it was), or no page ever had it
     *
     * @param label where the sticker is, if the key had one
     */
    data class NoPage(val lastPath: String? = null, val label: String? = null) : QrLookup

    data object NoBackend : QrLookup
    data object NotSignedIn : QrLookup

    /** a server without `qr-codes` */
    data object Unsupported : QrLookup

    /** the server could not be reached */
    data object Unreachable : QrLookup
    data class Failed(@param:StringRes val message: Int) : QrLookup
}

/**
 * Asks the selected server for the page with a QR code key (`docs/qr.md` of the backend). Stickers hold the host of
 * the web client, so the key goes to the server the app uses, whatever host the sticker names.
 */
internal class ResolveQrCodeUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    /** @param scan counts as a scan of the sticker; a check before adding a key only looks */
    suspend operator fun invoke(key: String, scan: Boolean): QrLookup =
        when (val result = restClient.getQrCode(key, scan)) {
            is Result.Success -> result.value.let { QrLookup.Found(QrPage(it.item.id, it.title, it.item.path)) }
            is Result.Failure -> failure(result.error)
        }

    private fun failure(error: FuelError): QrLookup {
        val chain = generateSequence<Throwable>(error) { it.cause }.take(MAX_CAUSES)
        if (chain.any { it is BackendNotConfiguredException }) return QrLookup.NoBackend
        val status = error.response.statusCode
        val body = QrErrorModel.parse(error.response.data)
        return when {
            status <= 0 -> QrLookup.Unreachable
            status == HTTP_UNAUTHORIZED -> QrLookup.NotSignedIn
            body?.code == QrErrorModel.CONFLICT -> QrLookup.Conflict(
                body.details?.items.orEmpty().map { QrPage(it.item.id, it.title, it.item.path) }
            )
            body?.code == QrErrorModel.GONE -> QrLookup.NoPage(body.details?.lastPath, body.details?.label)
            error.isUnsupportedEndpoint() -> QrLookup.Unsupported
            status == HTTP_NOT_FOUND -> QrLookup.NoPage()
            else -> QrLookup.Failed(error.userMessage())
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val MAX_CAUSES = 10
    }
}
