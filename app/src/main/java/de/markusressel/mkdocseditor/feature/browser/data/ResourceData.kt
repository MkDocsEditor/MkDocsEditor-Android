package de.markusressel.mkdocseditor.feature.browser.data

import java.util.Date

data class ResourceData(
    val entityId: Long,
    val id: String,
    val name: String,
    val filesize: Long,
    val modtime: Date,
    val isOfflineAvailable: Boolean
)