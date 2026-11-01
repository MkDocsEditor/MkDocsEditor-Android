package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.document.DocumentApi
import de.markusressel.mkdocsrestclient.mkdocs.MkDocsApi
import de.markusressel.mkdocsrestclient.reference.ReferenceApi
import de.markusressel.mkdocsrestclient.resource.ResourceApi
import de.markusressel.mkdocsrestclient.section.SectionApi
import de.markusressel.mkdocsrestclient.section.SectionModel

interface IMkDocsRestClient : MkDocsApi, SectionApi, DocumentApi, ResourceApi, ReferenceApi {

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