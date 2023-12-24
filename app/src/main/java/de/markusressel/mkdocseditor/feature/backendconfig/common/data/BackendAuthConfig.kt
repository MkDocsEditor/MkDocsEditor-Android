package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.BackendAuthConfigEntity

data class BackendAuthConfig(
    val username: String,
    val password: String,
)

internal fun BackendAuthConfigEntity.toBackendAuthConfig() = BackendAuthConfig(
    username = username,
    password = password,
)