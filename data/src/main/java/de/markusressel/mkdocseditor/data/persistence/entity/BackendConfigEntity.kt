package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne


@Entity
data class BackendAuthConfigEntity(
    @Id var entityId: Long = 0,
    @Unique val username: String = "",
    val password: String = "",
)

@Entity
data class BackendServerConfigEntity(
    @Id var entityId: Long = 0,
    val domain: String = "",
    val port: Int = 0,
    val useSsl: Boolean = false,
    val webBaseUri: String = "",
)

@Entity
data class BackendConfigEntity(
    @Id var entityId: Long = 0,
    @Unique val name: String = "",
    val description: String = "",
) {
    lateinit var serverConfig: ToOne<BackendServerConfigEntity>
    lateinit var authConfig: ToOne<BackendAuthConfigEntity>
}
