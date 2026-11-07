package de.markusressel.mkdocseditor.feature.preferences.domain

import android.graphics.drawable.Drawable
import de.markusressel.kutepreferences.core.preference.action.KuteAction
import de.markusressel.kutepreferences.core.preference.category.KuteCategory

/**
 * The entry that opens the about page. Its own type, so the settings can show it as a card like the categories next to
 * it. It stays an action and not a category: a search result of a category opens it, and this one has nothing inside.
 */
internal class AboutPreferenceItem(
    key: String,
    icon: Drawable?,
    title: String,
    description: String,
    onClick: () -> Unit,
) : KuteAction(
    key = key,
    icon = icon,
    title = title,
    description = description,
    onClick = onClick,
) {
    /** a category that only looks like this entry, for the card of the categories */
    fun asCategoryCard() = KuteCategory(
        key = key,
        icon = icon,
        title = title,
        description = description,
        onClick = onClick,
    )
}
