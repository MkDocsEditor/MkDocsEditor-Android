package de.markusressel.mkdocseditor.feature.editor.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The documents open in an editor. Their connection brings their changes to the server, they do
 * not need the background upload.
 */
@Singleton
internal class OpenDocuments @Inject constructor() {
    private val documents = MutableStateFlow<Map<Any, String>>(emptyMap())

    val ids: Set<String> get() = documents.value.values.toSet()
    val idsFlow = documents.map { it.values.toSet() }

    /** @param documentId the document [owner] has open, null if none */
    fun set(owner: Any, documentId: String?) {
        documents.update { old -> if (documentId != null) old + (owner to documentId) else old - owner }
    }
}
