package de.markusressel.mkdocseditor.feature.auth.onboarding

import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.ServerInfo
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.okResponse
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel.ConnectLinkEvent
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel.Problem
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel.Step
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectLinkViewModelTest {

    private val api = FakeAuthApi()
    private val store = InMemoryTokenStore()
    private val backends = FakeAuthBackends()
    private lateinit var viewModel: ConnectLinkViewModel
    private val events = mutableListOf<ConnectLinkEvent>()
    private val token = "mdd_" + "x".repeat(43)

    private fun link(version: Int = 1, expiresAt: String = "2026-11-01T12:05:00Z") = "https://edit.wiki.example.com/connect#" +
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"v":$version,"backendUrl":"https://backend.wiki.example.com","username":"markus","token":"$token","expiresAt":"$expiresAt"}""".toByteArray()
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api.onServerInfo = { ServerInfo(name = "Markus' Wiki", wikiUrl = "https://wiki.example.com") }
        viewModel = ConnectLinkViewModel(AuthRepository(store, { api }, deviceName = "Pixel 8"), backends).apply {
            clock = Clock.fixed(Instant.parse("2026-11-01T12:01:00Z"), ZoneOffset.UTC)
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start(text: String = link()) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        viewModel.initialize(text)
    }

    @Test
    fun `shows server and account, then connects without typing anything`() = runTest {
        api.onRedeemDeviceLink = { _, _ -> okResponse(token = "mds_paired", username = "markus") }
        start()
        assertEquals(Step.Confirm, viewModel.uiState.value.step)
        assertEquals("Markus' Wiki", viewModel.uiState.value.serverName)
        assertEquals("markus", viewModel.uiState.value.username)
        assertTrue(backends.configs.isEmpty(), "nothing is saved before the user agrees")

        viewModel.onUiEvent(UiEvent.ConnectClicked)

        assertTrue(api.calls.contains("redeemDeviceLink:markus:Pixel 8"))
        val config = backends.configs.single()
        assertEquals("Markus' Wiki", config.name)
        assertEquals("wiki.example.com", config.mkDocsWebConfig?.domain)
        assertEquals("mds_paired", store.sessions[config.id]?.token)
        assertEquals("markus", backends.hints[config.id])
        assertEquals(config.id, backends.selectedId)
        assertEquals(ConnectLinkEvent.Connected(config.id), events.single())
    }

    @Test
    fun `an existing config for the same backend is reused`() = runTest {
        backends.configs += backendConfig(5, name = "Mine", domain = "backend.wiki.example.com")
        api.onRedeemDeviceLink = { _, _ -> okResponse() }
        start()
        viewModel.onUiEvent(UiEvent.ConnectClicked)
        assertEquals(listOf(5L), backends.configs.map { it.id })
        assertEquals("Mine", backends.configs.single().name)
        assertEquals(ConnectLinkEvent.Connected(5), events.single())
    }

    @Test
    fun `expired before asking the server, or used and expired on the server`() = runTest {
        start(link(expiresAt = "2026-11-01T12:00:00Z"))
        assertEquals(Problem.Expired, viewModel.uiState.value.problem)
        assertTrue(Problem.Expired.canSignInManually)
    }

    @Test
    fun `a used link gives the expired problem and offers the manual sign-in`() = runTest {
        api.onRedeemDeviceLink = { _, _ -> throw AuthApiException(400, AuthErrorCode.INVALID_LINK, "invalid link") }
        start()
        viewModel.onUiEvent(UiEvent.ConnectClicked)
        assertEquals(Step.Problem, viewModel.uiState.value.step)
        assertEquals(Problem.Expired, viewModel.uiState.value.problem)

        viewModel.onUiEvent(UiEvent.SignInManuallyClicked)
        val config = backends.configs.single()
        assertEquals(ConnectLinkEvent.OpenLogin(config.id), events.last())
        assertEquals("markus", backends.hints[config.id], "the login screen is prefilled")
    }

    @Test
    fun `a disabled account`() = runTest {
        api.onRedeemDeviceLink = { _, _ -> throw AuthApiException(403, AuthErrorCode.ACCOUNT_DISABLED, "disabled") }
        start()
        viewModel.onUiEvent(UiEvent.ConnectClicked)
        assertEquals(Problem.AccountDisabled, viewModel.uiState.value.problem)
    }

    @Test
    fun `a newer payload version, broken payloads`() = runTest {
        start(link(version = 2))
        assertEquals(Problem.UpdateApp, viewModel.uiState.value.problem)
        viewModel.initialize("https://edit.wiki.example.com/connect#garbage")
        assertEquals(Problem.Invalid, viewModel.uiState.value.problem)
    }

    @Test
    fun `an unreachable server can be retried, an older one pairs by its host name`() = runTest {
        var down = true
        api.onServerInfo = { if (down) throw IOException("down") else throw AuthApiException(404, null, "Not Found") }
        start()
        assertEquals(Problem.Unreachable, viewModel.uiState.value.problem)
        down = false
        viewModel.onUiEvent(UiEvent.RetryClicked)
        assertEquals(Step.Confirm, viewModel.uiState.value.step)
        assertEquals("backend.wiki.example.com", viewModel.uiState.value.serverName)
    }
}
