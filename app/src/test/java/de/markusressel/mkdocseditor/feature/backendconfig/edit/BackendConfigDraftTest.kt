package de.markusressel.mkdocseditor.feature.backendconfig.edit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigDraft
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel.UiState
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.toDraft
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.withDraft
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Saved state is a Bundle, so this needs Robolectric */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class BackendConfigDraftTest {

    private class Holder(handle: SavedStateHandle) {
        var draft by handle.saved<BackendConfigDraft?> { null }
    }

    private val authConfig = AuthConfig(id = 7, username = "user", password = "secret-password")

    private val edited = UiState(
        authConfigs = listOf(authConfig),
        name = "wiki",
        description = "family",
        currentDomain = "wiki.example.org",
        currentPort = "8123",
        currentUseSsl = true,
        currentMkDocsWebDomain = "web.example.org",
        currentMkDocsWebPort = "443",
        currentMkDocsWebUseSsl = true,
        currentMkDocsWebAuthConfig = authConfig,
    )

    @Test
    fun `all form inputs are back after process death`() {
        val handle = SavedStateHandle()
        Holder(handle).draft = edited.toDraft()

        val restored = Holder(SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null))

        assertEquals(edited, UiState(authConfigs = listOf(authConfig)).withDraft(restored.draft!!))
    }

    @Test
    fun `a form without changes has no draft`() {
        val handle = SavedStateHandle()
        Holder(handle).draft

        val restored = Holder(SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null))

        assertNull(restored.draft)
    }

    @Test
    fun `an auth config deleted in the meantime is not selected again`() {
        val state = UiState(authConfigs = emptyList()).withDraft(edited.toDraft())

        assertNull(state.currentMkDocsWebAuthConfig)
    }

    @Test
    fun `passwords never reach the saved state`() {
        val draft = edited.copy(currentAuthConfigPassword = "typed-password").toDraft()

        assertFalse(draft.toString().contains("password"))
    }
}
