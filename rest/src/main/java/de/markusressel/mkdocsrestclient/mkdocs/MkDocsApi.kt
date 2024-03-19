package de.markusressel.mkdocsrestclient.mkdocs

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result

/**
 * Created by Markus on 03.06.2018.
 */
interface MkDocsApi {

    /**
     * Get the MkDocs configuration
     */
    suspend fun getMkDocsConfig(): Result<MkDocsConfigModel, FuelError>

}