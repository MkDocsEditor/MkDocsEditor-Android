package de.markusressel.rest

class MkDocsRestApiClient(private val requestManager: RequestManager = RequestManager()) {

    /**
     * Set the hostname for this client
     */
    fun setHostname(hostname: String) {
        requestManager
                .hostname = hostname
    }

    /**
     * Set the api resource for this client (in case it is not the default "/api/")
     */
    fun setApiResource(apiResource: String) {
        requestManager
                .apiResource = apiResource
    }

    /**
     * Set the api version for this client
     */
    fun setapiVersion(apiVersion: String) {
        requestManager
                .apiVersion = apiVersion
    }

    /**
     * Set the BasicAuthConfig for this client
     */
    fun setBasicAuthConfig(basicAuthConfig: BasicAuthConfig) {
        requestManager
                .basicAuthConfig = basicAuthConfig
    }


}