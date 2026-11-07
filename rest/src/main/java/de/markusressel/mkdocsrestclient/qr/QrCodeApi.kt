package de.markusressel.mkdocsrestclient.qr

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.awaitResponseResult
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.RequestManager
import de.markusressel.mkdocsrestclient.deserializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * QR codes on things in the house, keys in the front matter of a page (feature `qr-codes`, docs/qr.md of the backend)
 */
interface QrCodeApi {

    /**
     * The page with [key]. Fails with 409 `qr-conflict` or 410 `qr-gone` and their details (see [QrErrorModel]), or
     * with 404 for a key no page ever had.
     *
     * @param scan records the scan; without it the request only looks
     */
    suspend fun getQrCode(key: String, scan: Boolean = false): Result<QrCodeModel, FuelError>
}

class QrCodeApiImpl(
    private val requestManager: RequestManager,
) : QrCodeApi {

    override suspend fun getQrCode(key: String, scan: Boolean): Result<QrCodeModel, FuelError> =
        withContext(Dispatchers.IO) {
            requestManager.createRequest(
                url = "/qr/${URLEncoder.encode(key, Charsets.UTF_8).replace("+", "%20")}/",
                urlParameters = if (scan) listOf("scan" to "1") else emptyList(),
                method = Method.GET,
            )
                // a kept 410 would still say "no page" after the key went onto one
                .header("Cache-Control" to "no-cache")
                .awaitResponseResult(deserializer<QrCodeModel>())
                .third
        }
}
