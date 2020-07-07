package de.markusressel.mkdocseditor.view.viewmodel

import android.view.View
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import io.objectbox.android.ObjectBoxLiveData
import io.objectbox.kotlin.query


class CodeEditorViewModel @ViewModelInject constructor(
        val documentPersistenceManager: DocumentPersistenceManager,
        @Assisted private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val documentId = MutableLiveData<String>()

    var documentEntity: ObjectBoxLiveData<DocumentEntity>? = null

    fun getEntity(documentId: String): ObjectBoxLiveData<DocumentEntity> {
        if (documentEntity == null) {
            documentEntity = ObjectBoxLiveData(documentPersistenceManager.standardOperation().query {
                equal(DocumentEntity_.id, documentId)
            })
        }
        return documentEntity!!
    }

    val offlineModeEnabled = MutableLiveData<Boolean>()

    val offlineModeBannerVisibility = MediatorLiveData<Int>().apply {
        addSource(offlineModeEnabled) { value ->
            when (value) {
                true -> this.setValue(View.VISIBLE)
                else -> this.setValue(View.GONE)
            }
        }
    }

}
