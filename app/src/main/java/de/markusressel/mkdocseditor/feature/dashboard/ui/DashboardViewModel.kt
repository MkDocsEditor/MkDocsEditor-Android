package de.markusressel.mkdocseditor.feature.dashboard.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.SyncStatusStore
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigFlowUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.ObserveServerEventsUseCase
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.dashboard.data.DashboardStore
import de.markusressel.mkdocseditor.feature.dashboard.domain.FamilyPinPermission
import de.markusressel.mkdocseditor.feature.dashboard.domain.LoadDashboardUseCase
import de.markusressel.mkdocseditor.feature.dashboard.domain.LoadHomePageUseCase
import de.markusressel.mkdocseditor.feature.dashboard.domain.PinOutcome
import de.markusressel.mkdocseditor.feature.dashboard.domain.SetPinUseCase
import de.markusressel.mkdocseditor.feature.dashboard.domain.homePage
import de.markusressel.mkdocseditor.feature.editor.domain.EditorResources
import de.markusressel.mkdocseditor.feature.editor.domain.ExternalLinkOpener
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLinkTarget
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLinks
import de.markusressel.mkdocseditor.feature.editor.domain.WikiTreeHolder
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DownloadResourceUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.ShareFileUseCase
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocsrestclient.dashboard.DashboardSnapshotModel
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.PinModel
import de.markusressel.mkdocsrestclient.dashboard.PinScope
import de.markusressel.mkdocsrestclient.dashboard.PinsModel
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import de.markusressel.mkdocsrestclient.sync.events.ServerEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

/**
 * The start page: the home page of the wiki, the pinned pages and what changed lately. It shows what is stored at once
 * and updates it in the background.
 */
@HiltViewModel
internal class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getCurrentBackendConfigFlowUseCase: GetCurrentBackendConfigFlowUseCase,
    private val observeServerEventsUseCase: ObserveServerEventsUseCase,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val authRepository: AuthRepository,
    private val dashboardStore: DashboardStore,
    private val syncStatusStore: SyncStatusStore,
    private val loadDashboardUseCase: LoadDashboardUseCase,
    private val loadHomePageUseCase: LoadHomePageUseCase,
    private val setPinUseCase: SetPinUseCase,
    private val familyPinPermission: FamilyPinPermission,
    private val wikiTreeHolder: WikiTreeHolder,
    private val localDocumentStore: LocalDocumentStore,
    private val externalLinkOpener: ExternalLinkOpener,
    private val downloadResourceUseCase: DownloadResourceUseCase,
    private val shareFileUseCase: ShareFileUseCase,
    private val mainNavigationRequests: MainNavigationRequests,
    editorResources: EditorResources,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val previewResourceLoader = editorResources.previewResourceLoader { wikiTreeHolder.tree.value }

    private var olderFeed: Job? = null

    init {
        launch {
            getCurrentBackendConfigFlowUseCase().distinctUntilChanged { old, new -> old?.id == new?.id }.collect { config ->
                olderFeed?.cancel()
                _uiState.value = UiState(lastSync = config?.let { syncStatusStore.lastSync(it.id) })
                config ?: return@collect
                showStored(config)
                refreshAll()
            }
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                UiEvent.Refresh -> refreshAll(userAsked = true)
                UiEvent.LoadOlderFeed -> loadOlderFeed()
                is UiEvent.DocumentClicked -> _events.send(DashboardEvent.OpenDocument(event.documentId))
                is UiEvent.FeedEntryClicked -> openFeedEntry(event.entry)
                is UiEvent.UnpinClicked -> unpin(event.scope, event.pin)
                is UiEvent.HomePageLinkClicked -> onLinkClicked(event.href)
                is UiEvent.TopAppBarActionClicked -> when (event.action) {
                    TopAppBarAction.FileBrowser.Search -> _events.send(DashboardEvent.OpenSearch)
                    // the account screen sends the user to the login when there is no session
                    TopAppBarAction.FileBrowser.Profile -> currentConfig()?.let { _events.send(DashboardEvent.OpenAccount(it.id)) }
                }
            }
        }
    }

    /**
     * Follows the changes on the server until cancelled. Each new connection loads everything again, because changes
     * get lost while there is none.
     */
    internal suspend fun followServerEvents() {
        observeServerEventsUseCase().collect { event ->
            when (event) {
                ServerEvent.Connected -> refreshAll()
                ServerEvent.TreeChanged -> refreshHomePage()
                ServerEvent.PinsChanged, ServerEvent.FeedChanged -> refreshDashboard()
            }
        }
    }

    private suspend fun showStored(config: BackendConfig) {
        val snapshot = withContext(Dispatchers.IO) { dashboardStore.load(config.id, username(config)) }
        snapshot?.let { show(it.pins, it.feed.entries, it.feed.next) }
        showFamilyPinPermission(familyPinPermission.known())
        wikiTreeHolder.loadStored()?.let { showStoredHomePage(it) }
    }

    private suspend fun showStoredHomePage(tree: WikiTree) {
        val page = tree.homePage()
        if (page == null) {
            _uiState.update { it.copy(homePage = HomePageState.Missing) }
            return
        }
        val text = loadHomePageUseCase.stored(page)
        _uiState.update { old ->
            // a newer text from the server stays
            if (old.homePage is HomePageState.Shown && old.homePage.documentId == page.id && old.homePage.fresh) old
            else old.copy(homePage = text?.let { HomePageState.Shown(page.id, it, fresh = false) } ?: HomePageState.Unavailable(page.id))
        }
    }

    private suspend fun refreshAll(userAsked: Boolean = false) {
        if (isOfflineModeEnabledFlowUseCase().value) return
        _uiState.update { it.copy(loading = userAsked) }
        coroutineScope {
            // once per connection, not with every change of the pins
            launch { showFamilyPinPermission(familyPinPermission.refresh()) }
            refreshDashboard()
            refreshHomePage()
        }
        _uiState.update { it.copy(loading = false) }
    }

    private suspend fun refreshDashboard() {
        val config = currentConfig() ?: return
        val content = try {
            loadDashboardUseCase()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not load the dashboard")
            _uiState.update { it.copy(lastSyncFailed = true) }
            return
        }
        if (currentConfig()?.id != config.id) return
        olderFeed?.cancel()
        _uiState.update { it.copy(pinsSupported = content.pins != null, feedSupported = content.feed != null) }
        show(content.pins ?: PinsModel(), content.feed?.entries.orEmpty(), content.feed?.next)
        onBackendAnswered(config)
        val snapshot = DashboardSnapshotModel(
            pins = content.pins ?: PinsModel(),
            feed = content.feed ?: de.markusressel.mkdocsrestclient.dashboard.FeedPageModel(),
        )
        withContext(Dispatchers.IO) { dashboardStore.save(config.id, username(config), snapshot) }
        content.pins?.let { pins ->
            val tree = wikiTreeHolder.tree.value
            loadDashboardUseCase.keepOffline(pins) { id ->
                tree?.documentById(id)?.isOfflineAvailable == true || localDocumentStore.contains(id)
            }
        }
    }

    private suspend fun refreshHomePage() {
        val tree = wikiTreeHolder.refresh() ?: return
        val page = tree.homePage()
        if (page == null) {
            _uiState.update { it.copy(homePage = HomePageState.Missing) }
            return
        }
        val text = loadHomePageUseCase.fresh(page)
        if (text == null) {
            showStoredHomePage(tree)
            return
        }
        _uiState.update { it.copy(homePage = HomePageState.Shown(page.id, text, fresh = true)) }
    }

    private fun show(pins: PinsModel, feed: List<FeedEntryModel>, next: String?) = _uiState.update {
        it.copy(familyPins = pins.family, personalPins = pins.personal, feed = feed, feedNext = next)
    }

    private fun onBackendAnswered(config: BackendConfig) {
        val now = Date()
        syncStatusStore.setLastSync(config.id, now)
        _uiState.update { it.copy(lastSync = now, lastSyncFailed = false) }
    }

    private fun loadOlderFeed() {
        val next = uiState.value.feedNext ?: return
        if (olderFeed?.isActive == true) return
        olderFeed = launch {
            _uiState.update { it.copy(loadingOlder = true) }
            try {
                val page = loadDashboardUseCase.olderFeed(next)
                _uiState.update { old ->
                    // a refresh in between started the feed over
                    if (old.feedNext != next) old
                    else old.copy(feed = (old.feed + page.entries).distinctBy { it.id }, feedNext = page.next)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not load older changes")
                _events.send(DashboardEvent.Toast(context.getString(R.string.dashboard_feed_older_failed)))
            } finally {
                _uiState.update { it.copy(loadingOlder = false) }
            }
        }
    }

    private suspend fun openFeedEntry(entry: FeedEntryModel) {
        val documentId = entry.documentId
        when {
            documentId == null -> _events.send(DashboardEvent.Toast(context.getString(R.string.dashboard_page_gone)))
            entry.itemType == FEED_ITEM_SECTION -> mainNavigationRequests.showSection(documentId)
            else -> _events.send(DashboardEvent.OpenDocument(documentId))
        }
    }

    private suspend fun unpin(scope: PinScope, pin: PinModel) {
        val outcome = try {
            setPinUseCase(scope, pin.documentId, pinned = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not remove a pin")
            _events.send(DashboardEvent.Toast(context.getString(R.string.pin_failed)))
            return
        }
        when (outcome) {
            is PinOutcome.Changed -> _uiState.update { it.copy(familyPins = outcome.pins.family, personalPins = outcome.pins.personal) }
            PinOutcome.Forbidden -> {
                _events.send(DashboardEvent.Toast(context.getString(R.string.pin_for_everyone_forbidden)))
                // the role changed since it was stored, or was not known
                showFamilyPinPermission(familyPinPermission.refresh())
            }
        }
    }

    /** an unknown role keeps the buttons, and the server decides */
    private fun showFamilyPinPermission(allowed: Boolean?) = _uiState.update { it.copy(mayPinForEveryone = allowed != false) }

    /** links of the home page open like those in the preview of the editor */
    private suspend fun onLinkClicked(href: String) {
        val homePage = uiState.value.homePage as? HomePageState.Shown
        when (val target = PreviewLinks.resolve(wikiTreeHolder.tree.value, homePage?.documentId, href)) {
            is PreviewLinkTarget.External -> if (!externalLinkOpener.open(target.href)) {
                _events.send(DashboardEvent.Toast(context.getString(R.string.preview_link_not_found, href)))
            }

            is PreviewLinkTarget.Document -> _events.send(DashboardEvent.OpenDocument(target.documentId))
            is PreviewLinkTarget.Section -> mainNavigationRequests.showSection(target.sectionId)
            is PreviewLinkTarget.Resource -> try {
                shareFileUseCase(downloadResourceUseCase(target.resourceId, target.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                _events.send(DashboardEvent.Toast(context.getString(R.string.preview_resource_failed, target.name)))
            }

            PreviewLinkTarget.NotFound -> _events.send(DashboardEvent.Toast(context.getString(R.string.preview_link_not_found, href)))
            null -> {}
        }
    }

    private suspend fun currentConfig(): BackendConfig? = getCurrentBackendConfigFlowUseCase().first()

    private fun username(config: BackendConfig): String? = authRepository.session(config.id)?.username

    /** what the dashboard shows of the home page of the wiki */
    sealed interface HomePageState {
        /** the wiki has no `index.md` in its top folder */
        data object Missing : HomePageState

        /** there is one, but its text is not on this device */
        data class Unavailable(val documentId: String) : HomePageState

        /** @param fresh the text came from the server just now */
        data class Shown(val documentId: String, val text: String, val fresh: Boolean) : HomePageState
    }

    /**
     * @param homePage null while the tree is not known yet
     * @param pinsSupported false for a server without pins
     * @param feedSupported false for a server without a feed
     * @param feedNext the cursor of the older changes, null when there are none
     * @param loading the user asked for a refresh and it still runs
     * @param lastSync when the server last answered
     * @param lastSyncFailed the server did not answer the last time
     * @param mayPinForEveryone false for a reader, who can not remove the pins for everyone
     */
    data class UiState(
        val homePage: HomePageState? = null,
        val familyPins: List<PinModel> = emptyList(),
        val personalPins: List<PinModel> = emptyList(),
        val pinsSupported: Boolean = true,
        val feed: List<FeedEntryModel> = emptyList(),
        val feedNext: String? = null,
        val feedSupported: Boolean = true,
        val loadingOlder: Boolean = false,
        val loading: Boolean = false,
        val lastSync: Date? = null,
        val lastSyncFailed: Boolean = false,
        val mayPinForEveryone: Boolean = true,
    )

    sealed interface UiEvent {
        data object Refresh : UiEvent
        data object LoadOlderFeed : UiEvent
        data class DocumentClicked(val documentId: String) : UiEvent
        data class FeedEntryClicked(val entry: FeedEntryModel) : UiEvent
        data class UnpinClicked(val scope: PinScope, val pin: PinModel) : UiEvent
        data class HomePageLinkClicked(val href: String) : UiEvent
        data class TopAppBarActionClicked(val action: TopAppBarAction.FileBrowser) : UiEvent
    }

    private companion object {
        const val FEED_ITEM_SECTION = "section"
    }
}

internal sealed interface DashboardEvent {
    data class OpenDocument(val documentId: String) : DashboardEvent
    data class OpenAccount(val backendId: Long) : DashboardEvent
    data object OpenSearch : DashboardEvent
    data class Toast(val text: String) : DashboardEvent
}
