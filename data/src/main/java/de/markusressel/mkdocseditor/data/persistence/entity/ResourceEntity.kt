package de.markusressel.mkdocseditor.data.persistence.entity

import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocsrestclient.resource.ResourceModel
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import java.util.*

/**
 * Created by Markus on 05.06.2018.
 */
@Entity
data class ResourceEntity(@Id var entityId: Long, val type: String, val id: String, val name: String, val filesize: Long, val modtime: Date) : IdentifiableListItem {
    override fun getItemId(): String = id
}

fun ResourceModel.asEntity(): ResourceEntity {
    return ResourceEntity(0, this.type, this.id, this.name, this.filesize, this.modtime)
}