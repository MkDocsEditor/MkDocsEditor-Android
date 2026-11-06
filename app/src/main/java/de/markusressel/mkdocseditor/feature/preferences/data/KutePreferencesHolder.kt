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
import de.markusressel.mkdocseditor.data.persistence.SectionPersistenceManager
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.about.data.appVersionText
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
    private val eventBusManager: EventBusManager,
) {

    val offlineCacheCategory by lazy {
        KuteCategory(
            key = context.getString(R.string.category_offline_cache_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_airplane),
            title = context.getString(R.string.category_offline_cache_title),
            description = context.getString(R.string.category_offline_cache_description),
            children = listOf(
                KuteSection(
                    key = context.getString(R.string.section_background_sync_key),
                    title = context.getString(R.string.section_background_sync_title),
                    children = listOf(
                        offlineModePreference,
                        lastOfflineCacheUpdate,
                        forceOfflineCacheUpdatePreference,
                        clearOfflineCache
                    )
                )
            )
        )
    }

    private val forceOfflineCacheUpdatePreference = KuteAction(
        key = context.getString(R.string.action_force_offline_cache_update_key),
        icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_refresh_sync),
        title = context.getString(R.string.action_force_offline_cache_update_title),
        description = context.getString(R.string.action_force_offline_cache_update_description),
        onClick = {
            eventBusManager.send(BusEvent.SettingsEvent.ScheduleOfflineCacheUpdateRequestEvent)
        }
    )

    private val clearOfflineCache by lazy {
        KuteAction(key = context.getString(R.string.clear_offline_cache_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_delete),
            title = context.getString(R.string.clear_offline_cache_title),
            description = context.getString(R.string.clear_offline_cache_description),
            onClick = {
                sectionPersistenceManager.deleteTreeAndContent()
                context.toast(R.string.clear_offline_cache_done)
            })
    }

    val lastOfflineCacheUpdate by lazy {
        LastOfflineCacheUpdatePreferenceItem(
            key = context.getString(R.string.last_offline_cache_update_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_time),
            title = context.getString(R.string.last_offline_cache_update_title),
            dataProvider = dataProvider,
        )
    }

    val themePreference by lazy {
        KuteSingleSelectStringPreference(context = context,
            key = context.getString(R.string.theme_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_colorize),
            title = context.getString(R.string.theme_title),
            possibleValues = mapOf(
                R.string.theme_system_value to R.string.theme_system_value_name,
                R.string.theme_dark_value to R.string.theme_dark_value_name,
                R.string.theme_light_value to R.string.theme_light_value_name
            ),
            // the app followed the system before this setting did anything
            defaultValue = R.string.theme_system_value,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
                eventBusManager.send(BusEvent.SettingsEvent.ThemeChangedEvent(new))
            })
    }

    /** what the app shows when it starts: the dashboard or the file list */
    val startPagePreference by lazy {
        KuteSingleSelectStringPreference(context = context,
            key = context.getString(R.string.start_page_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_home),
            title = context.getString(R.string.start_page_title),
            possibleValues = mapOf(
                R.string.start_page_dashboard_value to R.string.start_page_dashboard_name,
                R.string.start_page_files_value to R.string.start_page_files_name,
            ),
            defaultValue = R.string.start_page_dashboard_value,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { _, _ -> })
    }

    val offlineModePreference by lazy {
        KuteBooleanPreference(key = context.getString(R.string.offline_mode_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_airplane),
            title = context.getString(R.string.offline_mode_title),
            defaultValue = false,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
                eventBusManager.send(BusEvent.SettingsEvent.OfflineModeChangedEvent(new))
            })
    }

    val uxCategory by lazy {
        KuteCategory(
            key = context.getString(R.string.category_ux_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_edit),
            title = context.getString(R.string.category_ux_title),
            description = context.getString(R.string.category_ux_description),
            children = listOf(
                KuteSection(
                    key = context.getString(R.string.section_code_editor_key),
                    title = context.getString(R.string.section_code_editor_title),
                    children = listOf(
                        codeEditorAlwaysOpenEditModePreference,
                        editorLineWrappingPreference,
                        editorLineNumbersPreference,
                        editorTextSizePreference,
                        editorAuthorHighlightPreference,
                    )
                )
            )
        )
    }

    private val generalUiSection by lazy {
        KuteSection(
            key = context.getString(R.string.section_general_ui_key),
            title = context.getString(R.string.section_general_ui_title),
            children = listOf(
                themePreference,
                startPagePreference,
            )
        )
    }

    val uiCategory by lazy {
        KuteCategory(
            key = context.getString(R.string.category_ui_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_colorize),
            title = context.getString(R.string.category_ui_title),
            description = context.getString(R.string.category_ui_description),
            children = listOf(
                generalUiSection,
            )
        )
    }

    val codeEditorAlwaysOpenEditModePreference by lazy {
        KuteBooleanPreference(key = context.getString(R.string.code_editor_always_edit_mode_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_edit),
            title = context.getString(R.string.code_editor_always_edit_mode_title),
            defaultValue = false,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
            })
    }

    val editorLineWrappingPreference by lazy {
        KuteBooleanPreference(key = context.getString(R.string.editor_line_wrapping_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_wrap_text),
            title = context.getString(R.string.editor_line_wrapping_title),
            defaultValue = true,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { _, _ -> })
    }

    val editorLineNumbersPreference by lazy {
        KuteBooleanPreference(key = context.getString(R.string.editor_line_numbers_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_format_list_numbered),
            title = context.getString(R.string.editor_line_numbers_title),
            defaultValue = true,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { _, _ -> })
    }

    val editorTextSizePreference by lazy {
        KuteNumberPreference(key = context.getString(R.string.editor_text_size_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_format_size),
            title = context.getString(R.string.editor_text_size_title),
            minimum = 10,
            maximum = 24,
            defaultValue = DEFAULT_EDITOR_TEXT_SIZE,
            unit = "sp",
            dataProvider = dataProvider)
    }

    val editorAuthorHighlightPreference by lazy {
        KuteSingleSelectStringPreference(context = context,
            key = context.getString(R.string.editor_author_highlight_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_accounts),
            title = context.getString(R.string.editor_author_highlight_title),
            possibleValues = mapOf(
                R.string.editor_author_highlight_marker_value to R.string.editor_author_highlight_marker_name,
                R.string.editor_author_highlight_strip_value to R.string.editor_author_highlight_strip_name,
                R.string.editor_author_highlight_off_value to R.string.editor_author_highlight_off_name,
            ),
            defaultValue = R.string.editor_author_highlight_marker_value,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { _, _ -> })
    }

    /**
     * Opens the about page. Its description shows the version, so it can be read off without opening it.
     */
    fun aboutAction(onClick: () -> Unit) = KuteAction(
        key = context.getString(R.string.about_key),
        icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_info),
        title = context.getString(R.string.about_title),
        description = context.appVersionText(),
        onClick = onClick,
    )

    val debugCategory by lazy {
        KuteCategory(
            key = context.getString(R.string.category_debug_key),
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
            key = context.getString(R.string.section_debug_global_key),
            title = context.getString(R.string.section_debug_global_title),
            children = listOf(
                demoMode,
            )
        )
    }

    val debugLoggingSection by lazy {
        KuteSection(
            key = context.getString(R.string.section_debug_logging_key),
            title = context.getString(R.string.section_debug_logging_title),
            children = listOf(
                logNetworkRequests,
            )
        )
    }

    val demoMode by lazy {
        KuteBooleanPreference(key = context.getString(R.string.demo_mode_key),
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
        KuteBooleanPreference(key = context.getString(R.string.log_network_requests_key),
            icon = iconHelper.getPreferenceIcon(MaterialDesignIconic.Icon.gmi_network),
            title = context.getString(R.string.log_network_requests_title),
            defaultValue = false,
            dataProvider = dataProvider,
            onPreferenceChangedListener = { old, new ->
                eventBusManager.send(BusEvent.DebugEvent.LogNetworkRequestsChangedEvent(new))
            })
    }

}


/** the size that the editor uses without a setting */
const val DEFAULT_EDITOR_TEXT_SIZE = 14L
