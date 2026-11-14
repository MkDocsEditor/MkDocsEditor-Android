package de.markusressel.mkdocseditor.feature.history.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.github.kittinunf.result.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import de.markusressel.mkdocseditor.feature.editor.domain.ChangeLocation
import de.markusressel.mkdocseditor.feature.history.domain.changeLocation
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.history.DiffHunkModel
import de.markusressel.mkdocsrestclient.history.HistoryEntryModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

/**
 * The revision history of a document: who changed what and when, newest first. An entry opens to the diff of its
 * commit, a hunk of it opens the editor there, and editors can bring back the text of an entry.
 */
@HiltViewModel
internal class HistoryViewModel @Inject constructor(
    private val restClient: IMkDocsRestClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private var documentId: String? = null

    fun initialize(documentId: String, canRestore: Boolean) {
        if (this.documentId != null) return
        this.documentId = documentId
        _uiState.update { it.copy(canRestore = canRestore) }
        launch { load() }
    }

    fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.RetryClicked -> launch { load() }
            is UiEvent.EntryClicked -> onEntryClicked(event.revision)
            is UiEvent.HunkClicked -> launch { _events.send(HistoryEvent.OpenInEditor(event.hunk.changeLocation())) }
            is UiEvent.RestoreClicked -> _uiState.update { it.copy(confirmRestore = event.entry) }
            is UiEvent.RestoreDismissed -> _uiState.update { it.copy(confirmRestore = null) }
            is UiEvent.RestoreConfirmed -> launch { restore() }
            is UiEvent.BackClicked -> launch { _events.send(HistoryEvent.Close) }
        }
    }

    private suspend fun load() {
        val documentId = documentId ?: return
        _uiState.update { it.copy(loading = true, problem = null) }
        when (val result = restClient.getDocumentHistory(documentId)) {
            // diffs stay: a revision never changes, but a new entry moves the parent of none of the old ones
            is Result.Success -> _uiState.update { it.copy(loading = false, entries = result.value) }
            is Result.Failure -> {
                Timber.w(result.error, "history of %s", documentId)
                _uiState.update { it.copy(loading = false, problem = result.error.userMessage()) }
            }
        }
    }

    private fun onEntryClicked(revision: String) {
        if (uiState.value.expanded == revision) {
            _uiState.update { it.copy(expanded = null) }
            return
        }
        _uiState.update { it.copy(expanded = revision) }
        if (uiState.value.diffs[revision] !is DiffState.Loaded) launch { loadDiff(revision) }
    }

    /** the diff of a commit is the one from the entry below it, the commit before on this file */
    private suspend fun loadDiff(revision: String) {
        val documentId = documentId ?: return
        val entries = uiState.value.entries
        val index = entries.indexOfFirst { it.revision == revision }
        if (index < 0) return
        val parent = entries.getOrNull(index + 1)
        if (parent == null) {
            setDiff(revision, DiffState.FirstVersion)
            return
        }
        setDiff(revision, DiffState.Loading)
        when (val result = restClient.getDocumentRevisionDiffHunks(documentId, from = parent.revision, to = revision)) {
            is Result.Success -> setDiff(revision, DiffState.Loaded(result.value.hunks))
            is Result.Failure -> {
                Timber.w(result.error, "diff of %s at %s", documentId, revision)
                setDiff(revision, DiffState.Failed(result.error.userMessage()))
            }
        }
    }

    private fun setDiff(revision: String, diff: DiffState) = _uiState.update { it.copy(diffs = it.diffs + (revision to diff)) }

    private suspend fun restore() {
        val documentId = documentId ?: return
        val entry = uiState.value.confirmRestore ?: return
        _uiState.update { it.copy(confirmRestore = null, restoring = true) }
        val result = restClient.restoreDocumentRevision(documentId, entry.revision)
        _uiState.update { it.copy(restoring = false) }
        when (result) {
            is Result.Success -> {
                _events.send(HistoryEvent.Message(R.string.history_restored))
                load()
            }
            is Result.Failure -> {
                Timber.w(result.error, "restore of %s to %s", documentId, entry.revision)
                _events.send(HistoryEvent.Message(result.error.userMessage()))
            }
        }
    }

    internal sealed interface DiffState {
        data object Loading : DiffState
        data class Loaded(val hunks: List<DiffHunkModel>) : DiffState
        data class Failed(@param:StringRes val message: Int) : DiffState

        /** the commit that added the file has nothing to compare with */
        data object FirstVersion : DiffState
    }

    /**
     * @param expanded the revision whose diff is shown
     * @param diffs by revision
     * @param confirmRestore the entry whose text is about to come back, while the user confirms it
     */
    internal data class UiState(
        val loading: Boolean = true,
        val entries: List<HistoryEntryModel> = emptyList(),
        @param:StringRes val problem: Int? = null,
        val expanded: String? = null,
        val diffs: Map<String, DiffState> = emptyMap(),
        val canRestore: Boolean = false,
        val confirmRestore: HistoryEntryModel? = null,
        val restoring: Boolean = false,
    )

    internal sealed interface UiEvent {
        data object RetryClicked : UiEvent
        data class EntryClicked(val revision: String) : UiEvent
        data class HunkClicked(val hunk: DiffHunkModel) : UiEvent
        data class RestoreClicked(val entry: HistoryEntryModel) : UiEvent
        data object RestoreDismissed : UiEvent
        data object RestoreConfirmed : UiEvent
        data object BackClicked : UiEvent
    }

    internal sealed interface HistoryEvent {
        data class OpenInEditor(val change: ChangeLocation) : HistoryEvent
        data class Message(@param:StringRes val text: Int) : HistoryEvent
        data object Close : HistoryEvent
    }
}
