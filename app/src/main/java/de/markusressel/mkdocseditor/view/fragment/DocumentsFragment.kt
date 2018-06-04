package de.markusressel.mkdocseditor.view.fragment

import com.github.nitrico.lastadapter.LastAdapter
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import de.markusressel.mkdocseditor.BR
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.asEntity
import de.markusressel.mkdocseditor.databinding.ListItemDocumentBinding
import de.markusressel.mkdocseditor.view.fragment.base.FabConfig
import de.markusressel.mkdocseditor.view.fragment.base.ListFragmentBase
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.document.DocumentModel
import io.reactivex.Single
import kotlinx.android.synthetic.main.fragment_recyclerview.*
import java.util.*
import javax.inject.Inject


/**
 * Server Status fragment
 *
 * Created by Markus on 07.01.2018.
 */
class DocumentsFragment : ListFragmentBase<DocumentModel, DocumentEntity>() {

    @Inject
    lateinit var persistenceManager: DocumentPersistenceManager

    @Inject
    lateinit var restClient: MkDocsRestClient

    override fun getPersistenceHandler(): PersistenceManagerBase<DocumentEntity> = persistenceManager

    override fun createAdapter(): LastAdapter {
        return LastAdapter(listValues, BR.item)
                .map<DocumentEntity, ListItemDocumentBinding>(R.layout.list_item_document) {
                    onCreate {
                        it
                                .binding
                                .presenter = this@DocumentsFragment
                    }
                    onClick {
                        openDocumentEditor(listValues[it.adapterPosition])
                    }
                }
                .into(recyclerView)
    }

    override fun loadListDataFromSource(): Single<List<DocumentModel>> {
        return Single
                .just(listOf(DocumentModel("document", "1", "Test", 10, Date())))
        //        return restClient.getDocument() getGroups ()
    }

    override fun mapToEntity(it: DocumentModel): DocumentEntity {
        return it
                .asEntity()
    }

    override fun getRightFabs(): List<FabConfig.Fab> {
        return listOf(FabConfig.Fab(description = "Add", icon = MaterialDesignIconic.Icon.gmi_plus, onClick = {
            openAddDialog()
        }))
    }

    private fun openAddDialog() {

    }

    private fun openDocumentEditor(group: DocumentEntity) {
        // TODO:

        //        context
        //                ?.let {
        //                    val intent = DetailActivityBase
        //                            .newInstanceIntent(GroupDetailActivity::class.java, it, group.entityId)
        //                    startActivity(intent)
        //                }
    }

}