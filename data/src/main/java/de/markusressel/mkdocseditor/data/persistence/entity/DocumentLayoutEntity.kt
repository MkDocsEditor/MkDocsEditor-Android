package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique

/**
 * How the user arranged the screen of a document on this device. Not part of [DocumentContentEntity], because a row
 * there counts as cached content.
 */
@Entity
data class DocumentLayoutEntity(
    @Id var entityId: Long = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE) val documentId: String = "",
    /** the share of the code editor next to the rendered page */
    var splitRatio: Float = 0F,
)
