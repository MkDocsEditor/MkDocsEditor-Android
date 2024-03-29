package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne

/**
 * Created by Markus on 05.01.2019.
 */
@Entity
data class DocumentContentEntity(
    @Id var entityId: Long = 0,
    var date: Long = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE) val documentId: String = "",
    var text: String = "",
    var selection: Int = 0,
    var zoomLevel: Float = 1F,
    var panX: Float = 0F,
    var panY: Float = 0F
) {

    lateinit var documentEntity: ToOne<DocumentEntity>

}