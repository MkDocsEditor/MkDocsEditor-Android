package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.Method
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
     */
    fun setHostname(hostname: String) {
        requestManager
                .hostname = hostname
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
     * Get the complete item tree
     */
    fun getItemTree(): Single<SectionModel> {
        return requestManager
                .doRequest("/tree/", Method.GET, SectionModel.SingleDeserializer())
    }

}