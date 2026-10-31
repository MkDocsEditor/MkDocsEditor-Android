package de.markusressel.mkdocseditor.feature.auth.onboarding

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.ServerInfo
import de.markusressel.mkdocseditor.auth.discovery.DiscoveredServer
import de.markusressel.mkdocseditor.auth.discovery.DiscoveryResult
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectServerViewModel
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectServerViewModel.ConnectServerEvent
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectServerViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectServerViewModelTest {

    private var result: DiscoveryResult = DiscoveryResult.NotMkDocsEditor
    private val asked = mutableListOf<String>()
    private val backends = FakeAuthBackends()
    private lateinit var viewModel: ConnectServerViewModel
    private val events = mutableListOf<ConnectServerEvent>()

    private fun found(info: ServerInfo = ServerInfo(name = "Markus' Wiki")) = DiscoveryResult.Found(
        DiscoveredServer(
            backendUrl = "https://backend.wiki.example.com/",
            info = info,
            wikiUrl = "https://wiki.example.com",
            webClientUrl = "https://edit.wiki.example.com/",
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ConnectServerViewModel({ asked += it; result }, backends)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
    }

    private fun submit(address: String) {
        viewModel.onUiEvent(UiEvent.AddressChanged(address))
        viewModel.onUiEvent(UiEvent.ContinueClicked)
    }

    @Test
    fun `an address is discovered, saved with the server name and leads to the login`() = runTest {
        result = found()
        start()
        submit("edit.wiki.example.com")

        assertEquals(listOf("edit.wiki.example.com"), asked)
        val config = backends.configs.single()
        assertEquals("Markus' Wiki", config.name)
        assertEquals("wiki.example.com", config.mkDocsWebConfig?.domain)
        assertEquals(config.id, backends.selectedId)
        assertEquals(ConnectServerEvent.OpenLogin(config.id), events.single())
    }

    @Test
    fun `advanced fields are used`() = runTest {
        result = found()
        start()
        viewModel.onUiEvent(UiEvent.AdvancedToggled)
        viewModel.onUiEvent(UiEvent.NameChanged("Work"))
        viewModel.onUiEvent(UiEvent.WikiUsernameChanged("reader"))
        viewModel.onUiEvent(UiEvent.WikiPasswordChanged("secret"))
        submit("edit.wiki.example.com")
        val config = backends.configs.single()
        assertEquals("Work", config.name)
        assertEquals(AuthConfig(username = "reader", password = "secret"), config.mkDocsWebAuthConfig)
    }

    @Test
    fun `discovery errors are explained`() = runTest {
        start()
        result = DiscoveryResult.InvalidAddress
        submit("not valid")
        assertEquals(R.string.onboarding_error_address, viewModel.uiState.value.error?.resId)
        result = DiscoveryResult.Unreachable(IOException("down"))
        submit("down.example.com")
        assertEquals(R.string.onboarding_error_unreachable, viewModel.uiState.value.error?.resId)
        result = DiscoveryResult.NotMkDocsEditor
        submit("example.com")
        assertEquals(R.string.onboarding_error_not_mkdocseditor, viewModel.uiState.value.error?.resId)
        assertTrue(events.isEmpty())
        assertTrue(backends.configs.isEmpty())
    }

    @Test
    fun `a server without admin asks for the setup link`() = runTest {
        result = found(ServerInfo(name = "New", setupRequired = true))
        start()
        submit("new.example.com")
        val state = viewModel.uiState.value
        assertEquals(backends.configs.single().id, state.setupBackendId)
        viewModel.onUiEvent(UiEvent.SetupLinkChanged("https://edit.new.example.com/setup#mdl_abcdefghijklmnopqrstuvwxyz"))
        viewModel.onUiEvent(UiEvent.SetupLinkSubmitted)
        assertEquals(state.setupBackendId, assertIs<ConnectServerEvent.OpenAuthLink>(events.single()).backendId)
    }

    @Test
    fun `a server without authentication is done right away`() = runTest {
        result = found(ServerInfo(authDisabled = true))
        start()
        submit("dev.example.com")
        assertEquals(ConnectServerEvent.Done, events.single())
    }

    @Test
    fun `pasted connect and invite links skip discovery`() = runTest {
        start()
        submit("https://edit.wiki.example.com/connect#eyJ2IjoxfQ")
        assertIs<ConnectServerEvent.OpenConnectLink>(events.last())
        submit("https://edit.wiki.example.com/invite#mdl_abcdefghijklmnopqrstuvwxyz")
        assertIs<ConnectServerEvent.OpenAuthLink>(events.last())
        assertTrue(asked.isEmpty())
    }
}
