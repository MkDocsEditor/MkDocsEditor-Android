package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocseditor.feature.editor.ui.ConnectionStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineBannerTest {

    private fun visible(
        offlineMode: Boolean = false,
        loadFailed: Boolean = false,
        status: ConnectionStatus? = null,
    ) = isOfflineBannerVisible(offlineMode, loadFailed, status)

    @Test
    fun `a loaded document is not offline, also when the connection is lost`() {
        assertFalse(visible())
        assertFalse(visible(status = ConnectionStatus(connected = false, errorCode = 1006)))
    }

    @Test
    fun `a document that could not be loaded is offline until the connection is back`() {
        assertTrue(visible(loadFailed = true))
        assertTrue(visible(loadFailed = true, status = ConnectionStatus(connected = false, errorCode = 1006)))
        assertFalse(visible(loadFailed = true, status = ConnectionStatus(connected = true)))
    }

    @Test
    fun `a refused session is not offline`() {
        assertFalse(visible(loadFailed = true, status = ConnectionStatus(connected = false, errorCode = 401)))
        assertFalse(visible(loadFailed = true, status = ConnectionStatus(connected = false, errorCode = 4001)))
    }

    @Test
    fun `the offline mode of the settings always shows the banner`() {
        assertTrue(visible(offlineMode = true))
        assertTrue(visible(offlineMode = true, status = ConnectionStatus(connected = true)))
    }
}
