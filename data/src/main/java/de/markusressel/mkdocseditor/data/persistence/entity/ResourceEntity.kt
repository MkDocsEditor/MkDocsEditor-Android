package de.markusressel.mkdocseditor.data.persistence.entity

import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocsrestclient.resource.ResourceModel
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne
import java.util.*

/**
 * Created by Markus on 05.06.2018.
 */
@Entity
data class ResourceEntity(@Id var entityId: Long = 0, val type: String = "Resource", @Unique val id: String = "", val name: String = "", val filesize: Long = -1L, val modtime: Date = Date()) : IdentifiableListItem {
    override fun getItemId(): String = id

    lateinit var parentSection: ToOne<SectionEntity>
}

fun ResourceModel.asEntity(parentSection: SectionEntity): ResourceEntity {
    val r = ResourceEntity(0, this.type, this.id, this.name, this.filesize, this.modtime)
    r.parentSection.target = parentSection
    return r
}