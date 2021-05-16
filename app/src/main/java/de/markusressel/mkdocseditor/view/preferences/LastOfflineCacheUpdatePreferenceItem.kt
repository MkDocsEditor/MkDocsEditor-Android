package de.markusressel.mkdocseditor.view.preferences

import com.airbnb.epoxy.EpoxyModel
import de.markusressel.kutepreferences.core.HighlighterFunction
import de.markusressel.kutepreferences.core.KutePreferenceListItem
import de.markusressel.mkdocseditor.PreferenceItemTextInfoBindingModel_
import de.markusressel.mkdocseditor.R
import java.util.*

class LastOfflineCacheUpdatePreferenceItem : KutePreferenceListItem {

    override fun createEpoxyModel(highlighterFunction: HighlighterFunction): EpoxyModel<*> {
        val viewModel = DataModel(
            lastUpdated = Date().toLocaleString()
        )

        return PreferenceItemTextInfoBindingModel_().viewModel(viewModel)
    }

    override fun getSearchableItems(): Set<String> {
        return setOf()
    }

    override val key: Int
        get() = R.string.last_offline_cache_update_key

    data class DataModel(val lastUpdated: String)

}