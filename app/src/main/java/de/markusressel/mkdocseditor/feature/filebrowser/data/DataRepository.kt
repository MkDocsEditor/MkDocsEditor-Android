package de.markusressel.mkdocseditor.feature.filebrowser.data

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentLayoutPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.ResourcePersistenceManager
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel.Companion.ROOT_SECTION_ID
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.util.networkBoundResource
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.document.DocumentModel
import de.markusressel.mkdocsrestclient.resource.ResourceModel
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.objectbox.kotlin.query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.StoreBuilder
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
    private val documentLayoutPersistenceManager: DocumentLayoutPersistenceManager,
    private val resourcePersistenceManager: ResourcePersistenceManager,

    private val dataFactory: DataFactory,
) {

    /**
     * Flag indicating if a background sync is currently in progress
     *
     * @see [OfflineSyncWorker]
     */
    private val _backgroundSyncInProgress = MutableStateFlow(false)
    val backgroundSyncInProgress = _backgroundSyncInProgress.asStateFlow()

    fun setBackgroundSyncInProgress(inProgress: Boolean) {
        _backgroundSyncInProgress.value = inProgress
    }

    /**
     * Find all data that matches the given search
     */
    fun find(searchString: String): List<FoundItem> {
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

        return sections.map { FoundItem(dataFactory.toSectionData(it), it.parentSection.target.folderNames()) } +
            documents.map { FoundItem(dataFactory.toDocumentData(it), it.parentSection.target.folderNames()) } +
            resources.map { FoundItem(dataFactory.toResourceData(it), it.parentSection.target.folderNames()) }
    }

    /** the names from below the root down to this section; the root itself has no parent */
    private fun SectionEntity?.folderNames(): List<String> = generateSequence(this) { it.parentSection.target }
        .filter { it.parentSection.target != null }
        .map { it.name }
        .toList()
        .asReversed()

    /**
     * @param item the [SectionData], [DocumentData] or [ResourceData]
     * @param path the names of the folders it is in, without the root
     */
    data class FoundItem(val item: Any, val path: List<String>)


    private val sourceOfTruth = SourceOfTruth.of<String, SectionEntity, SectionData>(
        reader = { sectionId ->
            // null tells the store that there is nothing yet: without it get() waits forever on an empty cache,
            // and a folder that is gone from the server never finishes loading
            sectionPersistenceManager.findByIdFlow(sectionId).map { it?.let(dataFactory::toSectionData) }
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
            rootSectionModel.asEntity()
        },
        sourceOfTruth = sourceOfTruth,
    ).build()

    private fun SectionModel.asEntity(parentSection: SectionEntity? = null): SectionEntity {
        val s = SectionEntity(0, this.id, this.name)
        s.parentSection.target = parentSection
        s.subsections.addAll(this.subsections.map { it.asEntity(parentSection = s) })
        s.documents.addAll(this.documents.map { it.asEntity(s) })
        s.resources.addAll(this.resources.map { it.asEntity(s) })
        return s
    }

    private fun DocumentModel.asEntity(parentSection: SectionEntity): DocumentEntity {
        val d = DocumentEntity(0, this.type, this.id, this.name, this.filesize, this.modtime, this.url)
        d.parentSection.target = parentSection
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDocument(documentId: String): Flow<Resource<DocumentEntity?>> = networkBoundResource(
        query = {
            documentPersistenceManager.observeById(documentId)
        },
        fetch = {
            restClient.getItemTree()
        },
        saveFetchResult = {
            it.fold(
                success = { sectionModel ->
                    sectionPersistenceManager.insertOrUpdateRoot(sectionModel.asEntity())
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
            resourcePersistenceManager.observeById(resourceId)
        },
        fetch = {
            restClient.getItemTree()
        },
        saveFetchResult = {
            it.fold(
                success = { sectionModel ->
                    sectionPersistenceManager.insertOrUpdateRoot(sectionModel.asEntity())
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
            sectionPersistenceManager.addSubsection(parentSection, it.asEntity())
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

    suspend fun updateDocumentContentInCache(documentId: String, text: String) = withContext(Dispatchers.IO) {
        documentContentPersistenceManager.insertOrUpdate(
            documentId = documentId,
            text = text
        )
    }

    suspend fun getSplitRatio(documentId: String): Float? = withContext(Dispatchers.IO) {
        documentLayoutPersistenceManager.getSplitRatio(documentId)
    }

    suspend fun saveSplitRatio(documentId: String, ratio: Float) = withContext(Dispatchers.IO) {
        documentLayoutPersistenceManager.saveSplitRatio(documentId, ratio)
    }

    suspend fun saveEditorState(
        documentId: String,
        text: String?,
        selection: Int,
        zoomLevel: Float = 1F,
        panX: Float = 0F,
        panY: Float = 0F,
    ) = withContext(Dispatchers.IO) {
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

    fun getSectionsTo(sectionId: String): List<SectionData> {
        val result = mutableListOf<SectionData>()

        var section = sectionPersistenceManager.findById(sectionId)
        while (section != null) {
            result.add(dataFactory.toSectionData(section))
            section = section.parentSection.target
        }

        return result.reversed().toList()
    }

}

private fun SectionData.getDocumentsRecursive(): List<DocumentData> =
    documents + subsections.flatMap { it.getDocumentsRecursive() }
