package de.markusressel.mkdocseditor.feature.main.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.join.data.EditorJoin
import de.markusressel.mkdocseditor.feature.join.data.EditorJoins
import de.markusressel.mkdocseditor.feature.join.data.PendingJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests from flows outside the tabs (onboarding, connect and invite links, login) to show
 * a tab of the main screen
 */
@Singleton
class MainNavigationRequests @Inject constructor() {

    private val _showFileBrowser = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showFileBrowser: SharedFlow<Unit> = _showFileBrowser.asSharedFlow()

    private val _authFlowsFinished = MutableStateFlow(0)

    /**
     * Counts the finished sign-ins. A state, not an event: the server tab may be out of
     * composition when it happens, and still has to leave its auth screens afterwards.
     */
    val authFlowsFinished: StateFlow<Int> = _authFlowsFinished.asStateFlow()

    /** after signing in to a server: its documents are what the user wants to see */
    fun showFileBrowser() {
        _authFlowsFinished.update { it + 1 }
        _showFileBrowser.tryEmit(Unit)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Accessor {
        fun mainNavigationRequests(): MainNavigationRequests
        fun pendingJoin(): PendingJoin
        fun editorJoins(): EditorJoins
    }
}

/** opens the document of a join link in the editors on top of this file browser */
@Composable
internal fun OpenJoinedDocument(navigator: Navigator) {
    val context = LocalContext.current.applicationContext
    val accessor = remember(context) { EntryPointAccessors.fromApplication(context, MainNavigationRequests.Accessor::class.java) }
    val pendingJoin = remember(accessor) { accessor.pendingJoin() }
    val target by pendingJoin.open.collectAsState()
    LaunchedEffect(target) {
        target?.let {
            pendingJoin.opened()
            val open = navigator.items.editorOf(it.documentId)
            if (open != null) {
                navigator.popUntil { screen -> screen === open }
                accessor.editorJoins().hand(EditorJoin(open.key, it.from, it.instance))
            } else {
                navigator.push(CodeEditorScreen(documentId = it.documentId, joinLink = true, joinFrom = it.from, joinInstance = it.instance))
            }
        }
    }
}

/** @return the editor of [documentId] nearest to the top, null if there is none */
internal fun List<Screen>.editorOf(documentId: String): CodeEditorScreen? =
    filterIsInstance<CodeEditorScreen>().lastOrNull { it.documentId == documentId }

@Composable
fun rememberMainNavigationRequests(): MainNavigationRequests {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, MainNavigationRequests.Accessor::class.java).mainNavigationRequests()
    }
}
