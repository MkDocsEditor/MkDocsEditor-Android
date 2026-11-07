package de.markusressel.mkdocseditor.feature.main.data

import de.markusressel.mkdocseditor.feature.editor.domain.ChangeLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
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

    private val _sectionToShow = MutableStateFlow<String?>(null)

    /** a folder for the file browser to open. A state, because the file browser may not exist yet. */
    val sectionToShow: StateFlow<String?> = _sectionToShow.asStateFlow()

    /** shows a folder in the file browser, from another tab */
    fun showSection(sectionId: String) {
        _sectionToShow.value = sectionId
        _showFileBrowser.tryEmit(Unit)
    }

    fun sectionShown(sectionId: String) {
        _sectionToShow.compareAndSet(sectionId, null)
    }

    private val _documentToOpen = MutableStateFlow<DocumentRequest?>(null)

    /**
     * A document for the file browser to open, from a notification. A state, because the app may just be starting,
     * and neither the main screen nor the file browser exist yet.
     */
    val documentToOpen: StateFlow<DocumentRequest?> = _documentToOpen.asStateFlow()

    /** opens [documentId] in the editor, on top of the file browser */
    /** @param change where the changes the notification was about are, see [CodeEditorScreen.change] */
    fun openDocument(documentId: String, change: ChangeLocation? = null) {
        _documentToOpen.value = DocumentRequest(documentId, change)
    }

    fun documentOpened(request: DocumentRequest) {
        _documentToOpen.compareAndSet(request, null)
    }

    /** not a data class: the same document asked for twice is two requests */
    class DocumentRequest(val documentId: String, val change: ChangeLocation? = null)

    private val _editorChange = MutableStateFlow<EditorChange?>(null)

    /**
     * A change for an editor that is open already, to go to. A state and not an event: the editor may not be in
     * composition yet.
     */
    val editorChange: StateFlow<EditorChange?> = _editorChange.asStateFlow()

    fun handChange(change: EditorChange) {
        _editorChange.value = change
    }

    /** the editor has taken [change] */
    fun changeTaken(change: EditorChange) {
        _editorChange.compareAndSet(change, null)
    }

    /** not a data class: the same change asked for twice is two requests */
    class EditorChange(val screenKey: ScreenKey, val change: ChangeLocation)

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

/** opens the document of a notification in the editors on top of this file browser */
@Composable
internal fun OpenRequestedDocument(navigator: Navigator) {
    val requests = rememberMainNavigationRequests()
    val request by requests.documentToOpen.collectAsState()
    LaunchedEffect(request) {
        request?.let {
            requests.documentOpened(it)
            val open = navigator.items.editorOf(it.documentId)
            if (open != null) {
                navigator.popUntil { screen -> screen === open }
                it.change?.let { change -> requests.handChange(MainNavigationRequests.EditorChange(open.key, change)) }
            } else {
                navigator.push(CodeEditorScreen(documentId = it.documentId, change = it.change))
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
