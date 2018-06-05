package de.markusressel.mkdocseditor.data.persistence.entity

import de.markusressel.mkdocsrestclient.section.SectionModel
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * Created by Markus on 05.06.2018.
 */
@Entity
data class SectionEntity(@Id var entityId: Long, val id: String, val name: String, val subsections: List<SectionEntity>, val documents: List<DocumentEntity>, val resources: List<ResourceEntity>)

fun SectionModel.asEntity(): SectionEntity {
    return SectionEntity(0, this.id, this.name, this.subsections.map {
        it
                .asEntity()
    }, this.documents.map {
        it
                .asEntity()
    }, this.resources.map {
        it
                .asEntity()
    })
}