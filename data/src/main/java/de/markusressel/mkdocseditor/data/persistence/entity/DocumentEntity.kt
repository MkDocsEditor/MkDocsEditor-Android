package de.markusressel.mkdocseditor.data.persistence.entity

import android.view.View
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocsrestclient.document.DocumentModel
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne
import java.util.*

fun DocumentModel.asEntity(parentSection: SectionEntity, contentEntity: DocumentContentEntity? = null): DocumentEntity {
    val d = DocumentEntity(0, this.type, this.id, this.name, this.filesize, this.modtime, this.url)
    d.parentSection.target = parentSection
    contentEntity?.let {
        d.content.target = it
    }
    return d
}

/**
 * Created by Markus on 04.06.2018.
 */
@Entity
data class DocumentEntity(
        @Id var entityId: Long = 0,
        val type: String = "Document",
        @Unique val id: String = "",
        val name: String = "",
        var filesize: Long = -1L,
        var modtime: Date = Date(),
        val url: String = "") : IdentifiableListItem {
    override fun getItemId(): String = id

    lateinit var parentSection: ToOne<SectionEntity>
    lateinit var content: ToOne<DocumentContentEntity?>

    val offlineAvailableVisibility: Int
        get() {
            return if (content.target != null) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

}
