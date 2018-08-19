package de.markusressel.mkdocseditor.data.persistence.entity

import de.markusressel.mkdocsrestclient.section.SectionModel
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.relation.ToMany

/**
 * Created by Markus on 05.06.2018.
 */
@Entity
class SectionEntity(entityId: Long = 0, id: String = "", name: String = "") : SectionEntityBase(entityId, id, name) {

    lateinit var subsections: ToMany<SectionEntity>
    @Backlink
    lateinit var documents: ToMany<DocumentEntity>
    @Backlink
    lateinit var resources: ToMany<ResourceEntity>

    fun getAllSubsections(): List<SectionEntity> {
        return listOf(this) + subsections.map {
            it
                    .getAllSubsections()
        }.flatMap { it }
    }

}

fun SectionModel.asEntity(): SectionEntity {
    val s = SectionEntity(0, this.id, this.name)

    s
            .subsections
            .addAll(this.subsections.map {
                it
                        .asEntity()
            })

    s
            .documents
            .addAll(this.documents.map {
                it
                        .asEntity(s)
            })
    s
            .resources
            .addAll(this.resources.map {
                it
                        .asEntity(s)
            })

    return s
}