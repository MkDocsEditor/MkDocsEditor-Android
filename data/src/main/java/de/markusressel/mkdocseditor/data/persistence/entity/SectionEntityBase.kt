package de.markusressel.mkdocseditor.data.persistence.entity

import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import io.objectbox.annotation.BaseEntity
import io.objectbox.annotation.Id

@BaseEntity
abstract class SectionEntityBase(@Id var entityId: Long = 0, val id: String = "", var name: String = "") : IdentifiableListItem {

    override fun getItemId(): String = id

}