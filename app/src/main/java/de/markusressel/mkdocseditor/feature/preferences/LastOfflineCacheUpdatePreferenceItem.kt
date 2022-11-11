package de.markusressel.mkdocseditor.feature.preferences


import de.markusressel.kutepreferences.core.preference.KutePreferenceListItem
import de.markusressel.mkdocseditor.R

class LastOfflineCacheUpdatePreferenceItem(
    override val onClick: (() -> Unit)? = null,
    override val onLongClick: (() -> Unit)? = null,
) : KutePreferenceListItem {

    override val key: Int
        get() = R.string.last_offline_cache_update_key

    data class DataModel(val lastUpdated: String)

    override fun search(searchTerm: String): Boolean {
        // TODO: implement search
        return false
    }

}