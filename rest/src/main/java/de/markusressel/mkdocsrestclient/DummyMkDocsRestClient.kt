@file:Suppress("UNCHECKED_CAST")

package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.document.DocumentModel
import de.markusressel.mkdocsrestclient.mkdocs.MkDocsConfigModel
import de.markusressel.mkdocsrestclient.mkdocs.MkDocsConfigThemeModel
import de.markusressel.mkdocsrestclient.mkdocs.MkDocsConfigThemePalette
import de.markusressel.mkdocsrestclient.resource.ResourceModel
import de.markusressel.mkdocsrestclient.section.SectionModel
import java.util.Date
import kotlin.reflect.KClass

object DemoData {

    val Document11 = DocumentModel(
        id = "document11",
        name = "Document 11",
        type = "Document",
        filesize = 1024,
        modtime = Date(),
        url = "",
    )
    val Document12 = DocumentModel(
        id = "document12",
        name = "Document 12",
        type = "Document",
        filesize = 1024,
        modtime = Date(),
        url = "",
    )
    val Resource11 = ResourceModel(
        id = "resource11",
        name = "Resource 11",
        type = "Resource",
        filesize = 1024,
        modtime = Date()
    )
    val Section1 = SectionModel(
        id = "Section1",
        name = "Folder 1",
        subsections = emptyList(),
        documents = listOf(
            Document11,
            Document12,
        ),
        resources = listOf(
            Resource11
        ),
    )


    val Document1 = DocumentModel(
        id = "document1",
        name = "Document 1",
        type = "Document",
        filesize = 1024,
        modtime = Date(),
        url = "",
    )
    val Resource1 = ResourceModel(
        id = "resource1",
        name = "Resource 1",
        type = "Resource",
        filesize = 1024,
        modtime = Date()
    )
    val RootSection = SectionModel(
        id = "root",
        name = "ROOT",
        subsections = listOf(Section1),
        documents = listOf(
            Document1
        ),
        resources = listOf(
            Resource1
        )
    )
}

class DummyMkDocsRestClient : IMkDocsRestClient {

    override fun setHostname(hostname: String) {}

    override fun getHostname(): String = "localhost"

    override fun setUseSSL(enabled: Boolean) {}

    override fun setPort(port: Int) {}

    override fun getBasicAuthConfig(): BasicAuthConfig? = null

    override fun setBasicAuthConfig(basicAuthConfig: BasicAuthConfig?) {}

    override suspend fun isHostAlive(): Result<String, FuelError> {
        return Result.success("")
    }

    override suspend fun getItemTree(): Result<SectionModel, FuelError> {
        return Result.success(DemoData.RootSection)
    }

    override suspend fun getSection(id: String): Result<SectionModel, FuelError> {
        return Result.success(
            DemoData.RootSection.findRecursive<SectionModel> {
                it.id == id
            } ?: return Result.error(FuelError.wrap(Exception("Section not found")))
        )
    }

    override suspend fun createSection(
        parentId: String,
        name: String
    ): Result<SectionModel, FuelError> {
        return Result.success(
            SectionModel(
                id = "newSection",
                name = name,
                subsections = emptyList(),
                documents = emptyList(),
                resources = emptyList(),
            )
        )
    }

    override suspend fun renameSection(id: String, name: String): Result<SectionModel, FuelError> {
        return Result.success(
            SectionModel(
                id = id,
                name = name,
                subsections = emptyList(),
                documents = emptyList(),
                resources = emptyList(),
            )
        )
    }

    override suspend fun deleteSection(id: String): Result<String, FuelError> {
        return Result.success("")
    }

    override suspend fun getDocument(id: String): Result<DocumentModel, FuelError> {
        return DemoData.RootSection.findRecursive<DocumentModel> {
            it.id == id
        }?.let {
            Result.success(it)
        } ?: Result.error(FuelError.wrap(Exception("Document not found")))
    }

    override suspend fun getDocumentContent(id: String): Result<String, FuelError> {
        return DemoData.RootSection.findRecursive<DocumentModel> {
            it.id == id
        }?.let {
            Result.success("This is the content of document ${it.name}")
        } ?: Result.error(FuelError.wrap(Exception("Document not found")))
    }

    override suspend fun createDocument(
        parentId: String,
        name: String
    ): Result<DocumentModel, FuelError> {
        return Result.success(
            DocumentModel(
                id = "newDocument",
                name = name,
                type = "Document",
                filesize = 1024,
                modtime = Date(),
                url = "",
            )
        )
    }

    override suspend fun renameDocument(
        id: String,
        name: String
    ): Result<DocumentModel, FuelError> {
        return Result.success(
            DocumentModel(
                id = id,
                name = name,
                type = "Document",
                filesize = 1024,
                modtime = Date(),
                url = "",
            )
        )
    }

    override suspend fun deleteDocument(id: String): Result<String, FuelError> {
        return Result.success("")
    }

    override suspend fun getResource(id: String): Result<ResourceModel, FuelError> {
        return Result.success(
            DemoData.RootSection.findRecursive<ResourceModel> {
                it.id == id
            } ?: return Result.error(FuelError.wrap(Exception("Resource not found")))
        )
    }

    override suspend fun uploadResource(
        parentId: String,
        name: String,
        content: ByteArray
    ): Result<String, FuelError> {
        return Result.success("")
    }

    override suspend fun downloadResource(id: String): Result<ByteArray, FuelError> {
        return DemoData.RootSection.findRecursive<ResourceModel> {
            it.id == id
        }?.let {
            Result.success("This is the content of resource ${it.name}".encodeToByteArray())
        } ?: Result.error(FuelError.wrap(Exception("Resource not found")))
    }

    override suspend fun renameResource(
        id: String,
        name: String
    ): Result<ResourceModel, FuelError> {
        return Result.success(
            ResourceModel(
                id = id,
                name = name,
                type = "Resource",
                filesize = 1024,
                modtime = Date()
            )
        )
    }

    override suspend fun deleteResource(id: String): Result<String, FuelError> {
        return Result.success("")
    }

    override fun enableLogging() {}
    override fun disableLogging() {}

    override suspend fun getMkDocsConfig(): Result<MkDocsConfigModel, FuelError> {
        return Result.success(
            MkDocsConfigModel(
                copyright = "© 2021",

                editUri = "",

                extra = emptyMap(),
                extraCss = emptyList(),
                markdownExtensions = emptyList(),

                repoName = "mkdocs",
                repoUrl = "https://repo.domain.com",

                siteAuthor = "John Doe",
                siteDescription = "Project documentation with Markdown",
                siteDir = "/some/path/to/docs/",
                siteName = "MkDocs",
                siteUrl = "https://www.mkdocs.org/",

                theme = MkDocsConfigThemeModel(
                    name = "mkdocs",
                    palette = MkDocsConfigThemePalette(
                        Primary = "#000000",
                        Accent = "#FFFFFF"
                    ),
                    customDir = ""
                ),
            )
        )
    }
}

inline fun <reified T : Any> SectionModel.findRecursive(noinline filter: (T) -> Boolean): T? {
    return findRecursive(filter, T::class)
}

fun <T : Any> SectionModel.findRecursive(
    filter: (T) -> Boolean,
    kClass: KClass<T>
): T? {
    if (this::class == kClass && filter(this as T)) {
        return this
    }
    for (document in documents) {
        if (document::class == kClass && filter(document as T)) {
            return document
        }
    }
    for (resource in resources) {
        if (resource::class == kClass && filter(resource as T)) {
            return resource
        }
    }
    for (section in subsections) {
        val result = section.findRecursive<T>(filter, kClass)
        if (result != null) {
            return result
        }
    }
    return null
}