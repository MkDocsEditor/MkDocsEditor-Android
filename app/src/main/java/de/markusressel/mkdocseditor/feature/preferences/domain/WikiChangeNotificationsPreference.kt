package de.markusressel.mkdocseditor.feature.preferences.domain

import android.graphics.drawable.Drawable
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.kutepreferences.core.preference.bool.KuteBooleanPreference

/**
 * The switch for notifications about changes in the wiki. Its own type, so the settings can show it with the state of
 * the permission and ask for it.
 */
internal class WikiChangeNotificationsPreference(
    key: String,
    icon: Drawable?,
    title: String,
    description: String,
    dataProvider: KutePreferenceDataProvider,
    onPreferenceChangedListener: (oldValue: Boolean, newValue: Boolean) -> Unit,
) : KuteBooleanPreference(
    key = key,
    icon = icon,
    title = title,
    descriptionFunction = { description },
    defaultValue = true,
    dataProvider = dataProvider,
    onPreferenceChangedListener = onPreferenceChangedListener,
)
