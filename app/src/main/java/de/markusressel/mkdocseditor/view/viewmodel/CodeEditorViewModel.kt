package de.markusressel.mkdocseditor.view.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import io.objectbox.android.ObjectBoxLiveData
import io.objectbox.kotlin.query

class CodeEditorViewModel : ViewModel() {

    val documentId = MutableLiveData<String>()

    var documentEntity: ObjectBoxLiveData<DocumentEntity>? = null

    fun getEntity(documentPersistenceManager: DocumentPersistenceManager, documentId: String): ObjectBoxLiveData<DocumentEntity> {
        if (documentEntity == null) {
            documentEntity = ObjectBoxLiveData(documentPersistenceManager.standardOperation().query {
                equal(DocumentEntity_.id, documentId)
            })
        }

        return documentEntity!!

    }

}
