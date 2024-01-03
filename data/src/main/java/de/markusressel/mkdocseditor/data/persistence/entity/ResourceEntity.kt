package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne
import java.util.Date

/**
 * Created by Markus on 05.06.2018.
 */
@Entity
data class ResourceEntity(
    @Id var entityId: Long = 0,
    val type: String = TYPE,
    @Unique val id: String = "",
    val name: String = "",
    var filesize: Long = -1L,
    var modtime: Date = Date()
) {
    lateinit var parentSection: ToOne<SectionEntity>

    companion object {
        const val TYPE: String = "Resource"
    }
}
