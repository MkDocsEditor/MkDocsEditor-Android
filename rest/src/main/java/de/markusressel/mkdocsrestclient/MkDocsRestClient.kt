package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.document.DocumentApi
import de.markusressel.mkdocsrestclient.document.DocumentApiImpl
import de.markusressel.mkdocsrestclient.resource.ResourceApi
import de.markusressel.mkdocsrestclient.resource.ResourceApiImpl
import de.markusressel.mkdocsrestclient.section.SectionApi
import de.markusressel.mkdocsrestclient.section.SectionApiImpl
import de.markusressel.mkdocsrestclient.section.SectionModel

/**
 * Convenience delegation class for easy access to all api methods
 *
 * Created by Markus on 03.06.2018.
 */
class MkDocsRestClient constructor(
    private val requestManager: RequestManager = RequestManager(),
    sectionApi: SectionApi = SectionApiImpl(requestManager),
    documentApi: DocumentApi = DocumentApiImpl(requestManager),
    resourceApi: ResourceApi = ResourceApiImpl(requestManager)
) : IMkDocsRestClient,
    SectionApi by sectionApi,
    DocumentApi by documentApi,
    ResourceApi by resourceApi {

    override fun setHostname(hostname: String) {
        requestManager.hostname = hostname
    }

    override fun getHostname(): String {
        return requestManager.hostname
    }

    override fun setUseSSL(enabled: Boolean) {
        requestManager.ssl = enabled
    }

    override fun setPort(port: Int) {
        requestManager.port = port
    }

    override fun getBasicAuthConfig(): BasicAuthConfig? {
        return requestManager.basicAuthConfig
    }

    override fun setBasicAuthConfig(basicAuthConfig: BasicAuthConfig) {
        requestManager.basicAuthConfig = basicAuthConfig
    }

    override suspend fun isHostAlive(): Result<String, FuelError> {
        return requestManager.doStatusRequest("/alive/", Method.GET)
    }


    override suspend fun getItemTree(): Result<SectionModel, FuelError> {
        return requestManager.doRequest("/tree/", Method.GET)
    }

}