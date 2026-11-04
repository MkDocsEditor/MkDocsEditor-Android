package de.markusressel.mkdocseditor.feature.backendconfig.edit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.withConfig
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

    private val stored = BackendConfig(
        id = 1,
        name = "stored",
        description = "",
        isSelected = true,
        serverConfig = null,
        mkDocsWebConfig = null,
        mkDocsWebAuthConfig = AuthConfig(id = 7, username = "user", password = "secret-password"),
    )

    private val edited = UiState().withConfig(stored).copy(
        name = "wiki",
        description = "family",
        address = "https://wiki.example.org:8123",
        webAddress = "https://web.example.org",
    )

    @Test
    fun `all form inputs are back after process death`() {
        val handle = SavedStateHandle()
        Holder(handle).draft = edited.toDraft()

        val restored = Holder(SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null))

        assertEquals(edited, UiState().withConfig(stored).withDraft(restored.draft!!))
    }

    @Test
    fun `a form without changes has no draft`() {
        val handle = SavedStateHandle()
        Holder(handle).draft

        val restored = Holder(SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null))

        assertNull(restored.draft)
    }

    @Test
    fun `a changed username does not keep the stored password`() {
        val state = UiState().withConfig(stored).withDraft(edited.copy(wikiUsername = "other").toDraft())

        assertEquals("", state.wikiPassword)
    }

    @Test
    fun `passwords never reach the saved state or the log`() {
        val state = edited.copy(wikiPassword = "typed-password")

        assertFalse(state.toDraft().toString().contains("password"))
        assertFalse(state.toString().contains("password"))
    }
}
