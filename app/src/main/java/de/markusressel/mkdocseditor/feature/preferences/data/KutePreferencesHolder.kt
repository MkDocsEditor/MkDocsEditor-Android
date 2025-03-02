@file:OptIn(DelicateCoroutinesApi::class)

package de.markusressel.mkdocseditor.feature.preferences.data

import android.content.Context
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.commons.android.material.toast
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.kutepreferences.core.preference.action.KuteAction
import de.markusressel.kutepreferences.core.preference.bool.KuteBooleanPreference
import de.markusressel.kutepreferences.core.preference.category.KuteCategory
import de.markusressel.kutepreferences.core.preference.number.KuteNumberPreference
import de.markusressel.kutepreferences.core.preference.section.KuteSection
import de.markusressel.kutepreferences.core.preference.select.KuteSingleSelectStringPreference
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.application.triggerAppRebirth
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.ResourcePersistenceManager
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.preferences.domain.LastOfflineCacheUpdatePreferenceItem
import de.markusressel.mkdocseditor.ui.IconHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Holder for KutePreference items for easy access to preference values across the application
 */
@Singleton
class KutePreferencesHolder @Inject constructor(
    private val context: Context,
    private val iconHelper: IconHandler,
    private val dataProvider: KutePreferenceDataProvider,
    private val sectionPersistenceManager: SectionPersistenceManager,
    private val documentPersistenceManager: DocumentPersistenceManager,
    private val documentContentPersistenceManager: DocumentContentPersistenceManager,
    private val resourcePersistenceManager: ResourcePersistenceManager,
    private val eventBusManager: EventBusManager,
) {

    val offlineCacheCategory by lazy {
        KuteCategory(
            key = R.string.category_offline_cache_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_airplane),
            title = context.getString(R.string.category_offline_cache_title),
            description = context.getString(R.string.category_offline_cache_description),
            children = listOf(
                KuteSection(
                    key = R.string.section_background_sync_key,
                    title = context.getString(R.string.section_background_sync_title),
                    children = listOf(
                        lastOfflineCacheUpdate,
                        forceOfflineCacheUpdatePreference,
                        clearOfflineCache
                    )
                )
            )
        )
    }

    private val forceOfflineCacheUpdatePreference = KuteAction(
        key = R.string.action_force_offline_cache_update_key,
        icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_refresh_sync),
        title = context.getString(R.string.action_force_offline_cache_update_title),
        description = context.getString(R.string.action_force_offline_cache_update_description),
        onClick = {
            eventBusManager.send(BusEvent.SettingsEvent.ScheduleOfflineCacheUpdateRequestEvent)
        }
    )

    private val clearOfflineCache by lazy {
        KuteAction(key = R.string.clear_offline_cache_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_delete),
            title = context.getString(R.string.clear_offline_cache_title),
            description = context.getString(R.string.clear_offline_cache_description),
            onClick = {
                documentContentPersistenceManager.standardOperation().removeAll()
                resourcePersistenceManager.standardOperation().removeAll()
                documentPersistenceManager.standardOperation().removeAll()
                sectionPersistenceManager.standardOperation().removeAll()
                context.toast("DB cleared")
            })
    }

    val lastOfflineCacheUpdate by lazy {
        LastOfflineCacheUpdatePreferenceItem(
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_time),
            title = context.getString(R.string.category_offline_cache_title),
            dataProvider = dataProvider,
        )
    }

    val themePreference by lazy {
        KuteSingleSelectStringPreference(context = context,
            key = R.string.theme_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_colorize),
            title = context.getString(R.string.theme_title),
            possibleValues = mapOf(
                R.string.theme_dark_value to R.string.theme_dark_value_name,
                R.string.theme_light_value to R.string.theme_light_value_name
            ),
            defaultValue = R.string.theme_dark_value,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
                eventBusManager.send(BusEvent.SettingsEvent.ThemeChangedEvent(new))
            })
    }

    val offlineModePreference by lazy {
        KuteBooleanPreference(key = R.string.offline_mode_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_colorize),
            title = context.getString(R.string.theme_title),
            defaultValue = false,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
                eventBusManager.send(BusEvent.SettingsEvent.OfflineModeChangedEvent(new))
            })
    }

    val uxCategory by lazy {
        KuteCategory(
            key = R.string.category_ux_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_compass),
            title = context.getString(R.string.category_ux_title),
            description = context.getString(R.string.category_ux_description),
            children = listOf(
                KuteSection(
                    key = R.string.section_code_editor_key,
                    title = context.getString(R.string.section_code_editor_title),
                    children = listOf(
                        codeEditorAlwaysOpenEditModePreference,
                        codeEditorSyncIntervalPreference
                    )
                )
            )
        )
    }

    private val generalUiSection by lazy {
        KuteSection(
            key = R.string.section_general_ui_key,
            title = context.getString(R.string.section_general_ui_title),
            children = listOf(
                themePreference,
            )
        )
    }

    val uiCategory by lazy {
        KuteCategory(
            key = R.string.category_ui_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_colorize),
            title = context.getString(R.string.category_ui_title),
            description = context.getString(R.string.category_ui_description),
            children = listOf(
                generalUiSection,
            )
        )
    }

    val codeEditorSyncIntervalPreference by lazy {
        KuteNumberPreference(
            key = R.string.code_editor_sync_interval_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_time),
            title = context.getString(R.string.code_editor_sync_interval_title),
            defaultValue = 200,
            minimum = 100,
            maximum = 1000,
            unit = "ms",
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
            })
    }

    val codeEditorAlwaysOpenEditModePreference by lazy {
        KuteBooleanPreference(key = R.string.code_editor_always_edit_mode_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_edit),
            title = context.getString(R.string.code_editor_always_edit_mode_title),
            defaultValue = false,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
            })
    }

    val debugCategory by lazy {
        KuteCategory(
            key = R.string.category_debug_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_developer_board),
            title = context.getString(R.string.category_debug_title),
            description = context.getString(R.string.category_debug_description),
            children = listOf(
                debugCommonSection,
                debugLoggingSection,
            )
        )
    }

    val debugCommonSection by lazy {
        KuteSection(
            key = R.string.section_debug_global_key,
            title = context.getString(R.string.section_debug_global_title),
            children = listOf(
                demoMode,
            )
        )
    }

    val debugLoggingSection by lazy {
        KuteSection(
            key = R.string.section_debug_logging_key,
            title = context.getString(R.string.section_debug_logging_title),
            children = listOf(
                logNetworkRequests,
            )
        )
    }

    val demoMode by lazy {
        KuteBooleanPreference(key = R.string.demo_mode_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_present_to_all),
            title = context.getString(R.string.demo_mode_title),
            defaultValue = false,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
                context.toast("Restarting App...")
                MainScope().launch {
                    delay(1000)
                    context.triggerAppRebirth()
                }
            })
    }

    val logNetworkRequests by lazy {
        KuteBooleanPreference(key = R.string.log_network_requests_key,
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_network),
            title = context.getString(R.string.log_network_requests_title),
            defaultValue = false,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
                eventBusManager.send(BusEvent.DebugEvent.LogNetworkRequestsChangedEvent(new))
            })
    }

}

