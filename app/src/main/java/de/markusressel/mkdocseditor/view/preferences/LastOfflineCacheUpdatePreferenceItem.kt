package de.markusressel.mkdocseditor.view.preferences

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import de.markusressel.kutepreferences.core.KutePreferenceListItem
import de.markusressel.mkdocseditor.R

class LastOfflineCacheUpdatePreferenceItem() : KutePreferenceListItem {

    override val key: Int
        get() = R.string.last_offline_cache_update_key

    override fun inflateListLayout(layoutInflater: LayoutInflater, parent: ViewGroup): ViewGroup {
        val layout = layoutInflater.inflate(R.layout.preference_item__text_info, parent, false)
        val textView = layout.findViewById(R.id.text) as TextView

        // TODO: update this when necessary
        textView.text = "Last updated: "

        return layout as ViewGroup
    }

}