@file:OptIn(ExperimentalStoreApi::class)

package de.markusressel.mkdocseditor.feature.browser.data

import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.ResourcePersistenceManager
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel.Companion.ROOT_SECTION_ID
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.networkBoundResource
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.document.DocumentModel
import de.markusressel.mkdocsrestclient.resource.ResourceModel
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.objectbox.kotlin.query
import io.objectbox.kotlin.toFlow
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.mobilenativefoundation.store.store5.Bookkeeper
import org.mobilenativefoundation.store.store5.Converter
import org.mobilenativefoundation.store.store5.ExperimentalStoreApi
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.MutableStoreBuilder
import org.mobilenativefoundation.store.store5.OnUpdaterCompletion
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.Updater
import org.mobilenativefoundation.store.store5.UpdaterResult
import org.mobilenativefoundation.store.store5.impl.extensions.get
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataRepository @Inject constructor(
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val restClient: IMkDocsRestClient,
    private val sectionPersistenceManager: SectionPersistenceManager,
    private val documentPersistenceManager: DocumentPersistenceManager,
    private val documentContentPersistenceManager: DocumentContentPersistenceManager,
    private val resourcePersistenceManager: ResourcePersistenceManager,

    private val dataFactory: DataFactory,
) {

    /**
     * Find all data that matches the given search
     */
    fun find(searchString: String): List<Any> {
        val searchRegex =
            searchString.toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.LITERAL))

        // TODO:  this is pretty ugly and time/performance consuming
        //  is it, though?

        val sections = sectionPersistenceManager.standardOperation().query {
            filter { section -> searchRegex.containsMatchIn(section.name) }
        }.find()

        val documents = documentPersistenceManager.standardOperation().query {
            filter { document ->
                searchRegex.containsMatchIn(document.name)
                    || searchRegex.containsMatchIn(document.content.target?.text ?: "")
            }
        }.find()

        val resources = resourcePersistenceManager.standardOperation().query {
            filter { resource -> searchRegex.containsMatchIn(resource.name) }
        }.find()

        return sections.map(dataFactory::toSectionData) +
            documents.map(dataFactory::toDocumentData) +
            resources.map(dataFactory::toResourceData)
    }


    class SectionsUpdaterResult

    private val converter = Converter.Builder<SectionModel, SectionEntity, SectionData>()
        .fromNetworkToLocal { it.asEntity(documentContentPersistenceManager) }
        .fromOutputToLocal { it.toEntity() }
        .build()

    private val updater = Updater.by<String, SectionData, SectionsUpdaterResult>(
        post = { key: String, input: SectionData ->
            UpdaterResult.Success.Typed(SectionsUpdaterResult())
        },
        onCompletion = OnUpdaterCompletion(
            onSuccess = { success: UpdaterResult.Success ->
                Timber.d { "Successfully updated section" }
            },
            onFailure = { failure: UpdaterResult.Error ->
                when (failure) {
                    is UpdaterResult.Error.Exception -> {
                        Timber.e(failure.error) { "Error updating section" }
                    }

                    is UpdaterResult.Error.Message -> {
                        Timber.e { "Error updating section: ${failure.message}" }
                    }
                }
            }
        )
    )

    private val bookkeeper: Bookkeeper<String> = Bookkeeper.by(
        getLastFailedSync = { key: String -> null },
        setLastFailedSync = { key: String, timestamp: Long -> true },
        clear = { key: String -> true },
        clearAll = { true },
    )

    private val fetcher = Fetcher.of<String, SectionModel> {
        val rootSectionModel = restClient.getItemTree().get()
        // NOTE: this always fetches the whole tree
        rootSectionModel
    }

    private val sourceOfTruth = SourceOfTruth.of<String, SectionEntity, SectionData>(
        reader = { sectionId ->
            sectionPersistenceManager.findByIdFlow(sectionId).filterNotNull().map(dataFactory::toSectionData)
        },
        writer = { key, entity ->
            // NOTE: this always stores the whole tree
            sectionPersistenceManager.insertOrUpdateRoot(entity)
        },
        delete = { sectionId ->
            sectionPersistenceManager.delete(sectionId)
        },
        deleteAll = {
            sectionPersistenceManager.deleteAll()
        }
    )

    val sectionStore = StoreBuilder.from<String, SectionEntity, SectionData>(
        fetcher = Fetcher.of {
            val rootSectionModel = restClient.getItemTree().get()
            // NOTE: this always fetches the whole tree
            rootSectionModel.asEntity(documentContentPersistenceManager)
        },
        sourceOfTruth = sourceOfTruth,
    ).build()

    val sectionMutableStore = MutableStoreBuilder
        .from(
            fetcher = fetcher,
            sourceOfTruth = sourceOfTruth,
            converter = converter,
        )
        .build(
            updater = updater,
            bookkeeper = bookkeeper
        )


    private fun SectionModel.asEntity(documentContentPersistenceManager: DocumentContentPersistenceManager): SectionEntity {
        val s = SectionEntity(0, this.id, this.name)

        s.subsections.addAll(this.subsections.map {
            it.asEntity(documentContentPersistenceManager)
        })

        s.documents.addAll(this.documents.map {
            val contentEntity = documentContentPersistenceManager.standardOperation().query {
                equal(
                    DocumentContentEntity_.documentId,
                    it.id,
                    QueryBuilder.StringOrder.CASE_INSENSITIVE
                )
            }.findUnique()
            it.asEntity(s, contentEntity)
        })
        s.resources.addAll(this.resources.map {
            it.asEntity(s)
        })

        return s
    }

    private fun DocumentModel.asEntity(
        parentSection: SectionEntity,
        contentEntity: DocumentContentEntity? = null
    ): DocumentEntity {
        val d =
            DocumentEntity(0, this.type, this.id, this.name, this.filesize, this.modtime, this.url)
        d.parentSection.target = parentSection
        contentEntity?.let {
            d.content.target = it
        }
        return d
    }

    private fun ResourceModel.asEntity(parentSection: SectionEntity): ResourceEntity {
        val r = ResourceEntity(
            entityId = 0,
            type = type,
            id = id,
            name = name,
            filesize = filesize,
            modtime = modtime
        )
        r.parentSection.target = parentSection
        return r
    }

    private fun DocumentData.toEntity(
        parentSection: SectionEntity,
        contentEntity: DocumentContentEntity? = null
    ): DocumentEntity {
        val d = DocumentEntity(
            entityId = entityId,
            type = DocumentEntity.TYPE,
            id = id,
            name = name,
            filesize = filesize,
            modtime = modtime,
            url = url
        )
        d.parentSection.target = parentSection
        contentEntity?.let {
            d.content.target = it
        }
        return d
    }

    private fun ResourceData.toEntity(parentSection: SectionEntity): ResourceEntity {
        val r = ResourceEntity(
            entityId = entityId,
            type = ResourceEntity.TYPE,
            id = id,
            name = name,
            filesize = filesize,
            modtime = modtime
        )
        r.parentSection.target = parentSection
        return r
    }

    private fun SectionData.toEntity(): SectionEntity {
        val s = SectionEntity(
            entityId = entityId,
            id = id,
            name = name
        )

        s.subsections.addAll(this.subsections.map {
            it.toEntity()
        })

        s.documents.addAll(this.documents.map {
            val contentEntity = documentContentPersistenceManager.standardOperation().query {
                equal(
                    DocumentContentEntity_.documentId,
                    it.id,
                    QueryBuilder.StringOrder.CASE_INSENSITIVE
                )
            }.findUnique()
            it.toEntity(s, contentEntity)
        })
        s.resources.addAll(this.resources.map {
            it.toEntity(s)
        })

        return s
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDocumentContent(documentId: String) = networkBoundResource(
        query = {
            documentContentPersistenceManager.standardOperation().query {
                equal(
                    DocumentContentEntity_.documentId,
                    documentId,
                    QueryBuilder.StringOrder.CASE_INSENSITIVE
                )
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
            isOfflineModeEnabledFlowUseCase().value.not()
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
            isOfflineModeEnabledFlowUseCase().value.not()
        }
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getResource(resourceId: String): Flow<Resource<ResourceEntity?>> = networkBoundResource(
        query = {
            resourcePersistenceManager.standardOperation().query {
                equal(ResourceEntity_.id, resourceId, QueryBuilder.StringOrder.CASE_INSENSITIVE)
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
            isOfflineModeEnabledFlowUseCase().value.not()
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

    suspend fun getAllDocuments(): List<DocumentData> {
        return sectionStore.get(ROOT_SECTION_ID).getDocumentsRecursive()
    }

}

private fun SectionData.getDocumentsRecursive(): List<DocumentData> =
    documents + subsections.flatMap { it.getDocumentsRecursive() }
