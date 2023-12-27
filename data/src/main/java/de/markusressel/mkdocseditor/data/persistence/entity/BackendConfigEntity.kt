package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne


@Entity
data class BackendAuthConfigEntity(
    @Id var entityId: Long = 0,
    @Unique var username: String = "",
    var password: String = "",
)

@Entity
data class BackendServerConfigEntity(
    @Id var entityId: Long = 0,
    var domain: String = "",
    var port: Int = 0,
    var useSsl: Boolean = false,
    var webBaseUri: String = "",
)

@Entity
data class BackendConfigEntity(
    @Id var entityId: Long = 0,
    @Unique var name: String = "",
    var description: String = "",
    var isSelected: Boolean = false,
) {
    lateinit var serverConfig: ToOne<BackendServerConfigEntity>
    lateinit var authConfig: ToOne<BackendAuthConfigEntity>
}
