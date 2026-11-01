package de.markusressel.mkdocseditor.feature.auth.link

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.auth.link.ui.PendingAuthLinkViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Saved state is a Bundle, so this needs Robolectric */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class PendingAuthLinkViewModelTest {

    private val invite = "mkdocseditor://invite#mdl_token"

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle.createHandle(savedStateProvider().saveState(), null)

    /** Regression: the flow of a link opened from outside the app was lost, and the inputs with it */
    @Test
    fun `an open link is still there after process death`() {
        val handle = SavedStateHandle()
        PendingAuthLinkViewModel(handle).open(invite)

        val restored = PendingAuthLinkViewModel(handle.afterProcessDeath())

        assertEquals(invite, restored.link.value)
    }

    @Test
    fun `a closed link does not come back after process death`() {
        val handle = SavedStateHandle()
        PendingAuthLinkViewModel(handle).apply {
            open(invite)
            close()
        }

        val restored = PendingAuthLinkViewModel(handle.afterProcessDeath())

        assertNull(restored.link.value)
    }
}
