package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique

/**
 * How the user arranged the screen of a document on this device, and where they were in it. Not part of
 * [DocumentContentEntity], because a row there counts as cached content. Never synced.
 */
@Entity
data class DocumentLayoutEntity(
    @Id var entityId: Long = 0,
    @Unique(onConflict = ConflictStrategy.REPLACE) val documentId: String = "",
    /** the share of the code editor next to the rendered page */
    var splitRatio: Float = 0F,
    /** when the view below was saved, only the newest ones are kept */
    @Index var viewSavedAt: Long = 0,
    /** the source line at the top of the editor, with the part of the way to the next one */
    var editorTopLine: Double? = null,
    /** the source line at the top of the rendered page */
    var pageTopLine: Double? = null,
    /** the zoom of the editor, null if the editor was not shown */
    var editorZoom: Float? = null,
    var selectionStartLine: Int = 0,
    var selectionStartColumn: Int = 0,
    var selectionEndLine: Int = 0,
    var selectionEndColumn: Int = 0,
    /** a line of the text near the top of the view, to find the place again after others changed the text */
    var anchorLine: Int = 0,
    var anchorText: String? = null,
)
