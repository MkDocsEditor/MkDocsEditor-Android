package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.document.DocumentModel
import de.markusressel.mkdocsrestclient.resource.ResourceModel
import de.markusressel.mkdocsrestclient.section.SectionModel

class DummyMkDocsRestClient : IMkDocsRestClient {
    override fun setHostname(hostname: String) {}

    override fun getHostname(): String = ""

    override fun setUseSSL(enabled: Boolean) {}

    override fun setPort(port: Int) {}

    override fun getBasicAuthConfig(): BasicAuthConfig? = null

    override fun setBasicAuthConfig(basicAuthConfig: BasicAuthConfig) {}

    override suspend fun isHostAlive(): Result<String, FuelError> {
        return Result.success("")
    }

    override suspend fun getItemTree(): Result<SectionModel, FuelError> {
        return Result.success(
            SectionModel(
                id = "root",
                name = "ROOT",
                subsections = emptyList(),
                documents = emptyList(),
                resources = emptyList(),
            )
        )
    }

    override suspend fun getSection(id: String): Result<SectionModel, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun createSection(
        parentId: String,
        name: String
    ): Result<SectionModel, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteSection(id: String): Result<String, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun getDocument(id: String): Result<DocumentModel, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun getDocumentContent(id: String): Result<String, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun createDocument(
        parentId: String,
        name: String
    ): Result<DocumentModel, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun renameDocument(
        id: String,
        name: String
    ): Result<DocumentModel, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteDocument(id: String): Result<String, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun getResource(id: String): Result<ResourceModel, FuelError> {
        TODO("Not yet implemented")
    }

    override suspend fun getResourceContent(id: String) {
        TODO("Not yet implemented")
    }

    override suspend fun uploadResource(parentId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteResource(id: String): Result<String, FuelError> {
        TODO("Not yet implemented")
    }
}