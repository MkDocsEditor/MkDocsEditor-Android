package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne
import java.util.Date

/**
 * Created by Markus on 04.06.2018.
 *
 * The page metadata ([title] to [searchExclude]) is nullable: rows stored before it existed, and trees of a backend
 * without `page-meta`, have none.
 *
 * @param title the page name as MkDocs shows it, see `DocumentModel.title`
 * @param metaTitle the title of the front matter only; [title] can also come from a heading or the file name
 * @param tags the tags of the front matter
 * @param searchExclude the page asks not to be found by search
 */
@Entity
data class DocumentEntity(
    @Id var entityId: Long = 0,
    val type: String = TYPE,
    @Unique(onConflict = ConflictStrategy.REPLACE) val id: String = "",
    val name: String = "",
    var filesize: Long = -1L,
    var modtime: Date = Date(),
    val url: String = "",
    val title: String? = null,
    val metaTitle: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val status: String? = null,
    val tags: List<String>? = null,
    val searchExclude: Boolean = false,
) {

    lateinit var parentSection: ToOne<SectionEntity>
    lateinit var content: ToOne<DocumentContentEntity?>

    companion object {
        const val TYPE: String = "Document"
    }

}
