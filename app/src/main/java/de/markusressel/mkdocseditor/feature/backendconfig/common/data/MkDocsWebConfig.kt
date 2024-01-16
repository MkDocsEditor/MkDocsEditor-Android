package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.MkDocsWebConfigEntity

data class MkDocsWebConfig(
    val id: Long = 0,
    val domain: String,
    val port: Int,
    val useSsl: Boolean,
)

internal fun MkDocsWebConfigEntity.toMkDocsWebConfig() = MkDocsWebConfig(
    id = entityId,
    domain = domain,
    port = port,
    useSsl = useSsl,
)
