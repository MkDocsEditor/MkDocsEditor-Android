package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.UserPasswordAuthConfigEntity

data class AuthConfig(
    val id: Long = 0,
    val username: String,
    val password: String,
)

internal fun UserPasswordAuthConfigEntity.toAuthConfig() = AuthConfig(
    id = entityId,
    username = username,
    password = password,
)
