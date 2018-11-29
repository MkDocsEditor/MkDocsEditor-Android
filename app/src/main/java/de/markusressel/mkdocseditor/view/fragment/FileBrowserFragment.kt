package de.markusressel.mkdocseditor.view.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.Typed3EpoxyController
import com.airbnb.epoxy.paging.PagedListEpoxyController
import com.github.ajalt.timberkt.Timber
import de.markusressel.commons.core.filterByExpectedType
import de.markusressel.mkdocseditor.ListItemDocumentBindingModel_
import de.markusressel.mkdocseditor.ListItemLoadingBindingModel_
import de.markusressel.mkdocseditor.ListItemResourceBindingModel_
import de.markusressel.mkdocseditor.ListItemSectionBindingModel_
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.ResourcePersistenceManager
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.asEntity
import de.markusressel.mkdocseditor.view.activity.EditorActivity
import de.markusressel.mkdocseditor.view.fragment.base.MultiPersistableListFragmentBase
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.reactivex.Single
import javax.inject.Inject


/**
 * Server Status fragment
 *
 * Created by Markus on 07.01.2018.
 */
class FileBrowserFragment : MultiPersistableListFragmentBase() {

    @Inject
    lateinit var sectionPersistenceManager: SectionPersistenceManager
    @Inject
    lateinit var documentPersistenceManager: DocumentPersistenceManager
    @Inject
    lateinit var resourcePersistenceManager: ResourcePersistenceManager

    override fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? {
        val viewModel = ViewModelProviders.of(this).get(SectionListViewModel::class.java)
        viewModel.getListLiveData2(sectionPersistenceManager).observe(this, Observer {
            epoxyController.submitList(it)
        })

        return super.createViewDataBinding(inflater, container, savedInstanceState)
    }

    override fun itemContainsCurrentSearchString(item: IdentifiableListItem): Boolean {
        return false
    }

    override fun sortListData(listData: List<IdentifiableListItem>): List<IdentifiableListItem> {
        // TODO: remove this
        return listData
    }

    override fun getLoadDataFromSourceFunction(): Single<Any> {
        return restClient.getItemTree() as Single<Any>
    }

    override fun mapToEntity(it: Any): IdentifiableListItem {
        return when (it) {
            is SectionModel -> it.asEntity()
            else -> throw IllegalArgumentException("Cant compare object of type ${it.javaClass}!")
        }
    }

    override fun persistListData(data: IdentifiableListItem) {
        resourcePersistenceManager
                .standardOperation()
                .removeAll()
        documentPersistenceManager
                .standardOperation()
                .removeAll()
        sectionPersistenceManager
                .standardOperation()
                .removeAll()

        val rootSection = data as SectionEntity

        sectionPersistenceManager
                .standardOperation()
                .put(rootSection)
    }

    override fun createEpoxyController(): PagedListEpoxyController<IdentifiableListItem> {
        return object : PagedListEpoxyController<IdentifiableListItem>() {
            override fun buildItemModel(currentPosition: Int, item: IdentifiableListItem?): EpoxyModel<*> {
                return when (item) {
                    is SectionEntity -> {
                        ListItemSectionBindingModel_()
                                .id(item.id)
                                .item(item)
                                .onclick { model, parentView, clickedView, position ->
                                    openSection(model.item())
                                }
                    }
                    is DocumentEntity -> {
                        ListItemDocumentBindingModel_()
                                .id(item.id)
                                .item(item)
                                .onclick { model, parentView, clickedView, position ->
                                    openDocumentEditor(model.item())
                                }
                    }
                    is ResourceEntity -> {
                        ListItemResourceBindingModel_()
                                .id(item.id)
                                .item(item)
                                .onclick { model, parentView, clickedView, position ->
                                    openResourceDetailPage(model.item())
                                }
                    }
                    else -> {
                        ListItemLoadingBindingModel_()
                                .id(-currentPosition)
                    }
                }
            }
        }
    }

    override fun updateControllerData(newData: List<IdentifiableListItem>) {
        val typed3EpoxyController = epoxyController as Typed3EpoxyController<List<SectionEntity>, List<DocumentEntity>, List<ResourceEntity>>
        typed3EpoxyController.setData(
                newData.filterByExpectedType(),
                newData.filterByExpectedType(),
                newData.filterByExpectedType()
        )
    }

    //    override fun getRightFabs(): List<FabConfig.Fab> {
    //        return listOf(FabConfig.Fab(description = R.string.add, icon = MaterialDesignIconic.Icon.gmi_plus, onClick = {
    //            openAddDialog()
    //        }))
    //    }

    private fun openDocumentEditor(document: DocumentEntity) {
        Timber
                .d { "Opening Document '${document.name}'" }

        val intent = EditorActivity
                .getNewInstanceIntent(context as Context, document.id, document.name)
        startActivity(intent)
    }

    private fun openResourceDetailPage(resource: ResourceEntity) {
        Timber
                .d { "Opening Resource '${resource.name}'" }
        Toast
                .makeText(context as Context, "Resources are not yet supported :(", Toast.LENGTH_LONG)
                .show()
    }

    private fun openAddDialog() {

    }

    /**
     * Called when the user presses the back button
     *
     * @return true, if the back button event was consumed, false otherwise
     */
    fun onBackPressed(): Boolean {
        if (backstack.size > 1) {
            backstack
                    .pop()
            openSection(backstack.peek().section, false)
            return true
        }

        return false
    }

}