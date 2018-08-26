package de.markusressel.mkdocseditor.data.persistence.entity

import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocsrestclient.document.DocumentModel
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne
import java.util.*

/**
 * Created by Markus on 04.06.2018.
 */
@Entity
data class DocumentEntity(@Id var entityId: Long = 0, val type: String = "Document", val id: String = "", val name: String = "", val filesize: Long = -1L, val modtime: Date = Date(), val url: String = "") : IdentifiableListItem {
    override fun getItemId(): String = id

    lateinit var parentSection: ToOne<SectionEntity>

}

fun DocumentModel.asEntity(parentSection: SectionEntity): DocumentEntity {
    val d = DocumentEntity(0, this.type, this.id, this.name, this.filesize, this.modtime, this.url)
    d
            .parentSection
            .target = parentSection
    return d
}