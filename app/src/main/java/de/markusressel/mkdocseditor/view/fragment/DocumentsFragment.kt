package de.markusressel.mkdocseditor.view.fragment

import android.content.Context
import android.widget.Toast
import androidx.core.widget.toast
import com.github.nitrico.lastadapter.LastAdapter
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import de.markusressel.mkdocseditor.BR
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.asEntity
import de.markusressel.mkdocseditor.databinding.ListItemDocumentBinding
import de.markusressel.mkdocseditor.databinding.ListItemResourceBinding
import de.markusressel.mkdocseditor.databinding.ListItemSectionBinding
import de.markusressel.mkdocseditor.extensions.prettyPrint
import de.markusressel.mkdocseditor.view.activity.EditorActivity
import de.markusressel.mkdocseditor.view.fragment.base.FabConfig
import de.markusressel.mkdocseditor.view.fragment.base.ListFragmentBase
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.document.DocumentModel
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_recyclerview.*
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
                .map<SectionEntity, ListItemSectionBinding>(R.layout.list_item_section) {
                    onCreate {
                        it
                                .binding
                                .presenter = this@DocumentsFragment
                    }
                    onClick {
                        openSection(listValues[it.adapterPosition])
                    }
                }
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
                .map<ResourceEntity, ListItemResourceBinding>(R.layout.list_item_document) {
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
        return restClient
                .getItemTree()
                .map {
                    it
                            .documents
                }
        //        return Single
        //                .just(listOf(DocumentModel("document", "1", "Test", 10, Date())))
        //        return restClient.getDocument()
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

    private fun openSection(documentEntity: DocumentEntity) {


    }

    private fun openDocumentEditor(document: DocumentEntity) {
        restClient
                .getDocumentContent(document.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onSuccess = {
                    val intent = EditorActivity
                            .getNewInstanceIntent(context as Context, document.id, it)
                    startActivity(intent)
                }, onError = {
                    context
                            ?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })
    }

    private fun openAddDialog() {

    }

    override fun onStart() {
        super
                .onStart()
        reloadDataFromSource()
    }

}