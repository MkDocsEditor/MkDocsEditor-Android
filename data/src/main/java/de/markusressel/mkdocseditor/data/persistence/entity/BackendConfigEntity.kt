package de.markusressel.mkdocseditor.data.persistence.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne


@Entity
data class UserPasswordAuthConfigEntity(
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
)

@Entity
data class MkDocsWebConfigEntity(
    @Id var entityId: Long = 0,
    var domain: String = "",
    var port: Int = 0,
    var useSsl: Boolean = false,
)

@Entity
data class BackendConfigEntity(
    @Id var entityId: Long = 0,
    @Unique var name: String = "",
    var description: String = "",
    var isSelected: Boolean = false,
) {
    lateinit var serverConfig: ToOne<BackendServerConfigEntity>
    lateinit var authConfig: ToOne<UserPasswordAuthConfigEntity>
    lateinit var mkDocsWebConfig: ToOne<MkDocsWebConfigEntity>
    lateinit var mkDocsWebAuthConfig: ToOne<UserPasswordAuthConfigEntity>
}
