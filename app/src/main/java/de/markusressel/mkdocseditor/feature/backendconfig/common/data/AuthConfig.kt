package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.BackendAuthConfigEntity

data class AuthConfig(
    val id: Long = 0,
    val username: String,
    val password: String,
)

internal fun BackendAuthConfigEntity.toBackendAuthConfig() = AuthConfig(
    id = entityId,
    username = username,
    password = password,
)