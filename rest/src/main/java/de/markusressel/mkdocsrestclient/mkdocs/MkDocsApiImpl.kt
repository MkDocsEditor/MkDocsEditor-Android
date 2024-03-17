package de.markusressel.mkdocsrestclient.mkdocs

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.RequestManager

/**
 * Created by Markus on 03.06.2018.
 */
class MkDocsApiImpl(
    private val requestManager: RequestManager
) : MkDocsApi {

    override suspend fun getMkDocsConfig(): Result<Map<String, Any?>, FuelError> {
        return requestManager.doRequest(
            url = "/mkdocs/config/",
            method = Method.GET
        )
    }

}