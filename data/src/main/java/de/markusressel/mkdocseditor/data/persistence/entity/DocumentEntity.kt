package de.markusressel.mkdocseditor.data.persistence.entity

import android.content.Context
import android.text.format.Formatter
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

    /**
     * Human readable representation of file size
     */
    fun formattedDocumentSize(context: Context): String {
        return Formatter.formatFileSize(context, filesize)
    }

    companion object {
        const val TYPE: String = "Document"
    }

}
