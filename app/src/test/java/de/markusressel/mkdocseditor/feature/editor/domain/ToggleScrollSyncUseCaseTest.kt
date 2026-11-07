package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.feature.editor.data.ScrollSyncSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToggleScrollSyncUseCaseTest {

    private class FakeScrollSyncSetting(initial: Boolean) : ScrollSyncSetting {
        val stored = MutableStateFlow(initial)
        val writes = mutableListOf<Boolean>()
        override val enabled get() = stored

        override suspend fun set(enabled: Boolean) {
            writes += enabled
            stored.value = enabled
        }
    }

    @Test
    fun `the toggle writes the preference`() = runTest {
        val setting = FakeScrollSyncSetting(initial = true)
        val toggle = ToggleScrollSyncUseCase(setting)

        assertFalse(toggle())
        assertTrue(toggle())
        assertEquals(listOf(false, true), setting.writes)
    }

    @Test
    fun `the toggle starts from the stored value, also when the settings changed it`() = runTest {
        val setting = FakeScrollSyncSetting(initial = true)
        val toggle = ToggleScrollSyncUseCase(setting)
        // turned off in the settings
        setting.stored.value = false

        assertTrue(toggle())
        assertEquals(true, setting.stored.value)
    }
}
