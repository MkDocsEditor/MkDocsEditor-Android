package de.markusressel.mkdocseditor.feature.qr.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A key of a sticker without a page, to put on a page: the editor of that page opens its QR codes with the key filled
 * in. A state, because the editor may not exist yet.
 */
@Singleton
internal class PendingQrKey @Inject constructor() {

    /** not a data class: the same key asked for twice is two requests */
    class Request(val documentId: String, val key: String)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    fun put(documentId: String, key: String) {
        _request.value = Request(documentId, key)
    }

    /** @return false if another editor took it first */
    fun take(request: Request): Boolean = _request.compareAndSet(request, null)
}
