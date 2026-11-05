package de.markusressel.mkdocseditor.feature.join.data

import cafe.adriel.voyager.core.screen.ScreenKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A join link for a document that already has an editor. That editor takes it: a second one would be a second instance
 * of this user, and the new one could end up following the one below it.
 *
 * @param screenKey the editor that takes it
 */
data class EditorJoin(
    val screenKey: ScreenKey,
    val from: String?,
    val instance: String?,
)

/** Hands [EditorJoin]s to the editors. A state and not an event: the editor may not be in composition yet. */
@Singleton
class EditorJoins @Inject constructor() {
    private val _request = MutableStateFlow<EditorJoin?>(null)
    val request: StateFlow<EditorJoin?> = _request.asStateFlow()

    fun hand(join: EditorJoin) {
        _request.value = join
    }

    /** the editor has taken [join] */
    fun taken(join: EditorJoin) {
        _request.compareAndSet(join, null)
    }
}
