package de.markusressel.mkdocseditor.feature.auth.passkeyprompt

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.PasskeyInfo
import de.markusressel.mkdocseditor.auth.api.PasskeySupport
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.FakeCredentialProviders
import de.markusressel.mkdocseditor.feature.auth.FakePasskeyPromptStore
import de.markusressel.mkdocseditor.feature.auth.UnusedCredentialClient
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data.PasskeyPromptPolicy
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel.PasskeySetupEvent
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel.Step
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasskeySetupViewModelTest {

    private val api = FakeAuthApi()
    private val tokens = InMemoryTokenStore()
    private val promptStore = FakePasskeyPromptStore()
    private val providers = FakeCredentialProviders()
    private val now = Instant.parse("2026-11-01T12:00:00Z")
    private lateinit var viewModel: PasskeySetupViewModel
    private val events = mutableListOf<PasskeySetupEvent>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tokens.put(1, AuthSession("mds_token", expiresAt = null, username = "markus"))
        api.status = api.status.copy(passkeys = PasskeySupport(enabled = true, rpId = "example.com"))
        api.onPasskeys = { emptyList() }
        viewModel = PasskeySetupViewModel(
            AuthRepository(tokens, { api }, deviceName = "Pixel 8"),
            FakeAuthBackends(backendConfig(1)),
            promptStore,
            providers,
            UnusedCredentialClient,
        )
        viewModel.clock = Clock.fixed(now, ZoneOffset.UTC)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start(backendId: Long = 1) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events += it }
        }
        viewModel.initialize(backendId)
    }

    @Test
    fun `offers a passkey when there is none on this device`() = runTest {
        start()
        assertEquals(Step.Offer, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.bitwardenInstalled)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `points to Bitwarden when it is installed`() = runTest {
        providers.installed += PasskeyPromptPolicy.BITWARDEN_PACKAGE
        providers.providerSettings = false
        start()
        assertTrue(viewModel.uiState.value.bitwardenInstalled)
        assertFalse(viewModel.uiState.value.canOpenProviderSettings)
    }

    @Test
    fun `goes straight on without passkeys on the server`() = runTest {
        api.status = api.status.copy(passkeys = PasskeySupport(enabled = false))
        start()
        assertEquals(listOf<PasskeySetupEvent>(PasskeySetupEvent.Finish), events)
    }

    @Test
    fun `goes straight on with a usable passkey`() = runTest {
        api.onPasskeys = { listOf(PasskeyInfo("p1", "Phone", provider = "Google Password Manager")) }
        start()
        assertEquals(listOf<PasskeySetupEvent>(PasskeySetupEvent.Finish), events)
    }

    @Test
    fun `goes straight on when loading fails`() = runTest {
        api.onPasskeys = { throw AuthApiException(500, "internal", "boom") }
        start()
        assertEquals(listOf<PasskeySetupEvent>(PasskeySetupEvent.Finish), events)
    }

    @Test
    fun `Later remembers the skip and goes on`() = runTest {
        start()
        viewModel.onUiEvent(UiEvent.LaterClicked)
        assertEquals(now, promptStore.state(1).skippedAt)
        assertEquals(listOf<PasskeySetupEvent>(PasskeySetupEvent.Finish), events)
    }

    @Test
    fun `creating a passkey shows the provider it went to`() = runTest {
        api.onFinishAddPasskey = { PasskeyInfo("new", "Passkey", provider = "Bitwarden") }
        start()
        viewModel.onUiEvent(UiEvent.CreateClicked)
        assertEquals(PasskeySetupEvent.CreatePasskey(api.ceremony.publicKey), events.single())

        viewModel.onUiEvent(UiEvent.PasskeyCreated("""{"id":"new"}"""))
        val state = viewModel.uiState.value
        assertEquals(Step.Done, state.step)
        assertEquals("Bitwarden", state.provider)
        assertEquals(setOf("new"), promptStore.state(1).registeredHere)

        viewModel.onUiEvent(UiEvent.DoneClicked)
        assertEquals(PasskeySetupEvent.Finish, events.last())
    }

    @Test
    fun `a cancelled sheet stays on the offer`() = runTest {
        start()
        viewModel.onUiEvent(UiEvent.CreateClicked)
        viewModel.onUiEvent(UiEvent.PasskeyCreated(null))
        assertEquals(Step.Offer, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.loading)
        assertFalse(api.calls.any { it.startsWith("finishAddPasskey") })
        assertNull(promptStore.state(1).skippedAt)
    }

    @Test
    fun `an older backend that wants the password again points to the account screen`() = runTest {
        api.onBeginAddPasskey = { throw AuthApiException(403, AuthErrorCode.REAUTH_REQUIRED, "reauth required") }
        start()
        viewModel.onUiEvent(UiEvent.CreateClicked)
        assertEquals(R.string.passkey_setup_error_reauth, viewModel.uiState.value.error?.resId)
        assertFalse(viewModel.uiState.value.loading)
    }
}
