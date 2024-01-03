package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne
import java.util.Date

/**
 * Created by Markus on 04.06.2018.
 */
@Entity
data class DocumentEntity(
    @Id var entityId: Long = 0,
    val type: String = TYPE,
    @Unique val id: String = "",
    val name: String = "",
    var filesize: Long = -1L,
    var modtime: Date = Date(),
    val url: String = ""
) {

    lateinit var parentSection: ToOne<SectionEntity>
    lateinit var content: ToOne<DocumentContentEntity?>

    companion object {
        const val TYPE: String = "Document"
    }

}
