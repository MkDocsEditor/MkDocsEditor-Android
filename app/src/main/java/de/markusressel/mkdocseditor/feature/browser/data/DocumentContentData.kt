package de.markusressel.mkdocseditor.feature.browser.data

data class DocumentContentData(
    val id: Long,
    val date: Long,
    val text: String,
    val selection: Int,
    val zoomLevel: Float,
    val panX: Float,
    val panY: Float,
)