package de.markusressel.mkdocseditor.feature.editor.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Whether the editor and the rendered page of a split scroll together. The setting and the toggle in the editor's
 * menu are this one value.
 */
internal interface ScrollSyncSetting {
    val enabled: Flow<Boolean>

    suspend fun set(enabled: Boolean)
}

internal class PreferenceScrollSyncSetting @Inject constructor(
    private val preferencesHolder: KutePreferencesHolder,
) : ScrollSyncSetting {
    override val enabled: Flow<Boolean> get() = preferencesHolder.editorScrollSyncPreference.persistedValue

    override suspend fun set(enabled: Boolean) {
        preferencesHolder.editorScrollSyncPreference.persistValue(enabled)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ScrollSyncSettingModule {
    @Binds
    abstract fun bindScrollSyncSetting(impl: PreferenceScrollSyncSetting): ScrollSyncSetting
}
