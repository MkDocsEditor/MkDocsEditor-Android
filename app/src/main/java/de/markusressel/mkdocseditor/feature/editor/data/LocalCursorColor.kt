package de.markusressel.mkdocseditor.feature.editor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The color of the own cursor and selection, as long as the app runs. In memory on purpose: it is meant for now, and the
 * profile keeps the color of the account.
 */
@Singleton
internal class LocalCursorColor @Inject constructor() {
    private val _color = MutableStateFlow<String?>(null)
    val color: StateFlow<String?> = _color.asStateFlow()

    /** @param color `#rrggbb`, null for the color of the account */
    fun set(color: String?) {
        _color.value = color
    }
}
