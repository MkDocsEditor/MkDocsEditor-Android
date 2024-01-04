package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.document.DocumentApi
import de.markusressel.mkdocsrestclient.resource.ResourceApi
import de.markusressel.mkdocsrestclient.section.SectionApi
import de.markusressel.mkdocsrestclient.section.SectionModel

interface IMkDocsRestClient : SectionApi, DocumentApi, ResourceApi {

    /**
     * Set the url for this client
     *
     * @param hostname the new url
     */
    fun setHostname(hostname: String)

    /**
     * @return the url for this client
     */
    fun getHostname(): String

    /**
     * Specify whether to use SSL
     *
     * @param enabled true enables ssl, false disables it
     */
    fun setUseSSL(enabled: Boolean)

    /**
     * Specify the port to use
     *
     * @param port the port number
     */
    fun setPort(port: Int)

    /**
     * Set the BasicAuthConfig for this client
     */
    fun getBasicAuthConfig(): BasicAuthConfig?

    /**
     * Set the BasicAuthConfig for this client
     */
    fun setBasicAuthConfig(basicAuthConfig: BasicAuthConfig)

    /**
     * Check if the server is alive and reachable
     */
    suspend fun isHostAlive(): Result<String, FuelError>

    /**
     * Get the complete item tree
     */
    suspend fun getItemTree(): Result<SectionModel, FuelError>

    /**
     * Enable logging
     */
    fun enableLogging()

    /**
     * Disable logging
     */
    fun disableLogging()
}