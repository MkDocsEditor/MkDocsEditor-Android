package de.markusressel.mkdocseditor.feature.browser.data

import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.data.persistence.*
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.asEntity
import de.markusressel.mkdocseditor.network.OfflineModeManager
import de.markusressel.mkdocseditor.util.NetworkBoundResource
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.networkBoundResource
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import io.objectbox.kotlin.query
import io.objectbox.kotlin.toFlow
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataRepository @Inject constructor(
    private val offlineModeManager: OfflineModeManager,
    private val restClient: MkDocsRestClient,
    private val sectionPersistenceManager: SectionPersistenceManager,
    private val documentPersistenceManager: DocumentPersistenceManager,
    private val documentContentPersistenceManager: DocumentContentPersistenceManager,
    private val resourcePersistenceManager: ResourcePersistenceManager,
) {

    /**
     * Find all data that matches the given search
     */
    fun find(searchString: String): List<IdentifiableListItem> {
        val searchRegex =
            searchString.toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.LITERAL))

        // TODO:  this is pretty ugly and time/performance consuming
        //  is it, though?

        val sections = sectionPersistenceManager.standardOperation().query {
            filter { section -> searchRegex.containsMatchIn(section.name) }
        }.find()

        val documents = documentPersistenceManager.standardOperation().query {
            filter { document -> searchRegex.containsMatchIn(document.name) }
        }.find()

        val resources = resourcePersistenceManager.standardOperation().query {
            filter { resource -> searchRegex.containsMatchIn(resource.name) }
        }.find()

        return sections + documents + resources
    }

    fun getSection(sectionId: String) = NetworkBoundResource(
        query = {
            sectionPersistenceManager.findByIdFlow(sectionId)
        },
        fetch = {
            restClient.getItemTree()
        },
        saveFetchResult = {
            it.fold(
                success = { sectionModel ->
                    val entity = sectionModel.asEntity(documentContentPersistenceManager)
                    sectionPersistenceManager.insertOrUpdateRoot(entity)
                },
                failure = { error ->
                    Timber.e(error)
                    throw error
                }
            )
        },
        shouldFetch = {
            offlineModeManager.isEnabled().not()
        }
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDocumentContent(documentId: String) = networkBoundResource(
        query = {
            documentContentPersistenceManager.standardOperation().query {
                equal(DocumentContentEntity_.documentId, documentId, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            }.subscribe().toFlow().map { it.firstOrNull() }
        },
        fetch = {
            restClient.getDocumentContent(documentId)
        },
        saveFetchResult = {
            it.fold(
                success = { content ->
                    documentContentPersistenceManager.insertOrUpdate(
                        documentId = documentId,
                        text = content,
                    )
                },
                failure = { error ->
                    Timber.e(error)
                }
            )
        },
        shouldFetch = {
            !offlineModeManager.isEnabled()
        }
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDocument(documentId: String): Flow<Resource<DocumentEntity?>> = networkBoundResource(
        query = {
            documentPersistenceManager.standardOperation().query {
                equal(DocumentEntity_.id, documentId, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            }.subscribe().toFlow().map { it.firstOrNull() }
        },
        fetch = {
            restClient.getItemTree()
        },
        saveFetchResult = {
            it.fold(
                success = { sectionModel ->
                    val entity = sectionModel.asEntity(documentContentPersistenceManager)
                    sectionPersistenceManager.insertOrUpdateRoot(entity)
                },
                failure = { error ->
                    Timber.e(error)
                    throw error
                }
            )
        },
        shouldFetch = {
            !offlineModeManager.isEnabled()
        }
    )

    suspend fun createNewSection(sectionName: String, parentSectionId: String) {
        val parentSection = sectionPersistenceManager.findById(parentSectionId)
        if (parentSection == null) {
            Timber.e { "Parent section could not be found in persistence while trying to create a new section in it" }
            throw IllegalStateException("Parent section not found")
        }

        restClient.createSection(parentSectionId, sectionName).fold(success = {
            val createdSection = it.asEntity(documentContentPersistenceManager)
            parentSection.subsections.add(createdSection)
            // insert it into persistence
            sectionPersistenceManager.standardOperation().put(parentSection)
        }, failure = {
            Timber.e(it) { "Error creating section" }
            throw it
        })
    }

    suspend fun createNewDocument(documentName: String, parentSectionId: String): String {
        val parentSection = sectionPersistenceManager.findById(parentSectionId)
            ?: throw IllegalStateException(
                "Parent section could not be found in persistence" +
                    " while trying to create a new document in it"
            )

        return restClient.createDocument(parentSectionId, documentName).fold<String>(
            success = {
                // insert it into persistence
                documentPersistenceManager.standardOperation().put(
                    it.asEntity(parentSection = parentSection)
                )
                return it.id
            }, failure = {
            Timber.e(it) { "Error creating document" }
            throw it
        })
    }

    suspend fun updateDocumentContentInCache(documentId: String, text: String) {
        documentContentPersistenceManager.insertOrUpdate(
            documentId = documentId,
            text = text
        )
    }

    suspend fun saveEditorState(
        documentId: String,
        text: String?,
        selection: Int,
        zoomLevel: Float,
        panX: Float,
        panY: Float
    ) {
        documentContentPersistenceManager.insertOrUpdate(
            documentId = documentId,
            text = text,
            selection = selection,
            zoomLevel = zoomLevel,
            panX = panX,
            panY = panY
        )
    }

    suspend fun loadFileTree() {
        restClient.getItemTree().fold(
            success = { sectionModel ->
                val entity = sectionModel.asEntity(documentContentPersistenceManager)
                sectionPersistenceManager.insertOrUpdateRoot(entity)
            },
            failure = { error ->
                Timber.e(error)
                throw error
            }
        )
    }

}
