package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

/**
 * Created by Markus on 05.01.2019.
 */
@Entity
data class DocumentContentEntity(@Id var entityId: Long = 0,
                                 val documentId: String = "",
                                 val text: String = "") {

    lateinit var documentEntity: ToOne<DocumentEntity>

}