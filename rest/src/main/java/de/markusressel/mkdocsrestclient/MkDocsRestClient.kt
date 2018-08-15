package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.document.DocumentApi
import de.markusressel.mkdocsrestclient.document.DocumentApiImpl
import de.markusressel.mkdocsrestclient.resource.ResourceApi
import de.markusressel.mkdocsrestclient.resource.ResourceApiImpl
import de.markusressel.mkdocsrestclient.section.SectionApi
import de.markusressel.mkdocsrestclient.section.SectionApiImpl
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.reactivex.Single

/**
 * Convenience delegation class for easy access to all api methods
 *
 * Created by Markus on 03.06.2018.
 */
class MkDocsRestClient(private val requestManager: RequestManager = RequestManager(), sectionApi: SectionApi = SectionApiImpl(requestManager), documentApi: DocumentApi = DocumentApiImpl(requestManager), resourceApi: ResourceApi = ResourceApiImpl(requestManager)) : SectionApi by sectionApi, DocumentApi by documentApi, ResourceApi by resourceApi {

    /**
     * Set the hostname for this client
     *
     * @param hostname the new hostname
     */
    fun setHostname(hostname: String) {
        requestManager
                .hostname = hostname
    }

    /**
     * @return the hostname for this client
     */
    fun getHostname(): String {
        return requestManager
                .hostname
    }

    /**
     * Set the api resource for this client (in case it is not the default "/")
     */
    fun setApiResource(apiResource: String) {
        requestManager
                .apiResource = apiResource
    }

    /**
     * Set the BasicAuthConfig for this client
     */
    fun getBasicAuthConfig(): BasicAuthConfig? {
        return requestManager
                .basicAuthConfig
    }

    /**
     * Set the BasicAuthConfig for this client
     */
    fun setBasicAuthConfig(basicAuthConfig: BasicAuthConfig) {
        requestManager
                .basicAuthConfig = basicAuthConfig
    }

    /**
     * Check if the server is alive and reachable
     */
    fun isHostAlive(): Single<Pair<Response, Result<ByteArray, FuelError>>> {
        return requestManager
                .doRequest("/alive/", Method.GET)
    }

    /**
     * Get the complete item tree
     */
    fun getItemTree(): Single<SectionModel> {
        return requestManager
                .doRequest("/tree/", Method.GET, SectionModel.SingleDeserializer())
    }

}