package de.markusressel.mkdocseditor.data.persistence.entity

import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocsrestclient.document.DocumentModel
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import java.util.*

/**
 * Created by Markus on 04.06.2018.
 */
@Entity
data class DocumentEntity(@Id var entityId: Long, val type: String, val id: String, val name: String, val filesize: Long, val modtime: Date) : IdentifiableListItem {
    override fun getItemId(): String = id
}

fun DocumentModel.asEntity(): DocumentEntity {
    return DocumentEntity(0, this.type, this.id, this.name, this.filesize, this.modtime)
}