package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity


data class BackendConfig(
    val id: Long = 0L,
    val name: String,
    val description: String,
    val serverConfig: BackendServerConfig?,
    val mkDocsWebConfig: MkDocsWebConfig?,
    val mkDocsWebAuthConfig: AuthConfig?,
    val isSelected: Boolean,
)

internal fun BackendConfigEntity.toBackendConfig() = BackendConfig(
    id = entityId,
    name = name,
    description = description,
    isSelected = isSelected,
    serverConfig = serverConfig.target.toServerConfig(),
    mkDocsWebConfig = mkDocsWebConfig.target?.toMkDocsWebConfig(),
    mkDocsWebAuthConfig = mkDocsWebAuthConfig.target?.toAuthConfig(),
)

/**
 * @return the backend as the auth layer sees it, or null without server config
 */
fun BackendConfig.toBackendRef(): BackendRef? = serverConfig?.let { BackendRef(id = id, baseUrl = it.baseUrl) }
