package de.markusressel.mkdocseditor.feature.qr.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AccountRole
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.editor.domain.WikiTreeHolder
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import de.markusressel.mkdocseditor.feature.qr.data.PendingQrKey
import de.markusressel.mkdocseditor.feature.qr.domain.QrLink
import de.markusressel.mkdocseditor.feature.qr.domain.QrLookup
import de.markusressel.mkdocseditor.feature.qr.domain.QrPage
import de.markusressel.mkdocseditor.feature.qr.domain.ResolveQrCodeUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Follows the URL of a QR code sticker: the page with its key opens right away. Otherwise this says why not, lists the
 * pages of a key on several pages, and lets editors put a key without a page on one.
 */
@HiltViewModel
internal class QrLinkViewModel @Inject constructor(
    private val resolve: ResolveQrCodeUseCase,
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
    private val treeHolder: WikiTreeHolder,
    private val pendingQrKey: PendingQrKey,
    private val navigation: MainNavigationRequests,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<QrLinkEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private var linkText: String? = null

    fun initialize(text: String) {
        if (linkText != null) return
        linkText = text
        _uiState.update { it.copy(key = QrLink.parse(text).orEmpty()) }
        launch { load() }
    }

    fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.RetryClicked -> launch { load() }
            is UiEvent.SignInClicked -> launch { backends.selected()?.let { _events.send(QrLinkEvent.OpenLogin(it.id)) } }
            is UiEvent.PageClicked -> launch { open(event.documentId) }
            is UiEvent.PutOnPageClicked -> launch { showPages() }
            is UiEvent.QueryChanged -> _uiState.update { it.copy(query = event.text) }
            is UiEvent.TargetPageClicked -> launch {
                pendingQrKey.put(event.documentId, uiState.value.key)
                open(event.documentId)
            }
            is UiEvent.BackClicked -> onBack()
        }
    }

    private fun onBack() {
        if (uiState.value.step == Step.PickPage) {
            _uiState.update { it.copy(step = Step.NoPage) }
        } else {
            launch { _events.send(QrLinkEvent.Close) }
        }
    }

    private suspend fun load() {
        val key = uiState.value.key
        if (key.isEmpty()) return showProblem(Problem.Invalid)
        _uiState.update { it.copy(step = Step.Loading) }
        val backend = backends.selected() ?: return showProblem(Problem.NoBackend)
        _uiState.update { it.copy(serverName = backend.name) }
        when (val lookup = resolve(key, scan = true)) {
            is QrLookup.Found -> open(lookup.page.documentId)
            is QrLookup.Conflict -> _uiState.update { it.copy(step = Step.Conflict, pages = lookup.pages) }
            is QrLookup.NoPage -> _uiState.update {
                it.copy(
                    step = Step.NoPage,
                    lastPath = lookup.lastPath,
                    label = lookup.label,
                    canPutOnPage = authRepository.role(backend.id) != AccountRole.READER,
                )
            }
            is QrLookup.NoBackend -> showProblem(Problem.NoBackend)
            is QrLookup.NotSignedIn -> showProblem(Problem.NotSignedIn)
            is QrLookup.Unsupported -> showProblem(Problem.Unsupported)
            is QrLookup.Unreachable -> showProblem(Problem.Unreachable)
            is QrLookup.Failed -> _uiState.update { it.copy(step = Step.Problem, problem = Problem.Failed, failure = lookup.message) }
        }
    }

    /** the pages of the stored tree; a page that is newer than it is not there yet, so the tree is asked for again */
    private suspend fun showPages() {
        _uiState.update { it.copy(step = Step.PickPage, query = "") }
        treeHolder.loadStored()?.let { tree -> _uiState.update { it.copy(allPages = pagesOf(tree)) } }
        treeHolder.refresh()?.let { tree -> _uiState.update { it.copy(allPages = pagesOf(tree)) } }
    }

    private suspend fun open(documentId: String) {
        navigation.openDocument(documentId)
        _events.send(QrLinkEvent.Close)
    }

    private fun showProblem(problem: Problem) = _uiState.update { it.copy(step = Step.Problem, problem = problem) }

    internal enum class Step { Loading, Conflict, NoPage, PickPage, Problem }

    internal enum class Problem(@param:StringRes val message: Int, val canRetry: Boolean = false, val canSignIn: Boolean = false) {
        Invalid(R.string.qr_problem_invalid),
        NoBackend(R.string.qr_problem_no_backend),
        NotSignedIn(R.string.qr_problem_signed_out, canSignIn = true),
        Unsupported(R.string.qr_problem_unsupported),
        Unreachable(R.string.qr_problem_unreachable, canRetry = true),
        Failed(R.string.qr_problem_failed, canRetry = true),
    }

    internal data class UiState(
        val step: Step = Step.Loading,
        val key: String = "",
        val serverName: String = "",
        val problem: Problem? = null,
        /** what went wrong for [Problem.Failed] */
        @param:StringRes val failure: Int? = null,
        /** the pages of a key on several pages */
        val pages: List<QrPage> = emptyList(),
        /** where a key without a page was last */
        val lastPath: String? = null,
        val label: String? = null,
        /** readers can't change pages */
        val canPutOnPage: Boolean = false,
        val query: String = "",
        val allPages: List<QrPage> = emptyList(),
    ) {
        /** the pages to put the key on that match [query], by title or path */
        val shownPages: List<QrPage>
            get() {
                val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                return allPages.filter { page -> words.all { it in page.title.lowercase() || it in page.path.lowercase() } }
            }
    }

    internal sealed interface UiEvent {
        data object RetryClicked : UiEvent
        data object SignInClicked : UiEvent
        data class PageClicked(val documentId: String) : UiEvent
        data object PutOnPageClicked : UiEvent
        data class QueryChanged(val text: String) : UiEvent
        data class TargetPageClicked(val documentId: String) : UiEvent
        data object BackClicked : UiEvent
    }

    internal sealed interface QrLinkEvent {
        data class OpenLogin(val backendId: Long) : QrLinkEvent
        data object Close : QrLinkEvent
    }

    internal companion object {
        /** every page of [tree], by path */
        fun pagesOf(tree: WikiTree): List<QrPage> {
            val pages = mutableListOf<QrPage>()
            fun collect(section: SectionData) {
                section.documents.forEach { pages += QrPage(it.id, it.shownTitle, tree.pathOf(WikiItem.Document(it))) }
                section.subsections.forEach(::collect)
            }
            collect(tree.root)
            return pages.sortedBy { it.path }
        }
    }
}
