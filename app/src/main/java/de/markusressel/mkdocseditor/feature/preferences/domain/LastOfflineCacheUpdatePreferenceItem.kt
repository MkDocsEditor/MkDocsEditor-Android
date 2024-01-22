package de.markusressel.mkdocseditor.feature.preferences.domain


import android.graphics.drawable.Drawable
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.kutepreferences.core.preference.KutePreferenceItem
import de.markusressel.kutepreferences.core.preference.KutePreferenceListItem
import de.markusressel.kutepreferences.core.search.SearchUtils.containsAnyWord
import de.markusressel.mkdocseditor.R
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Singleton

@Singleton
class LastOfflineCacheUpdatePreferenceItem(
    override val icon: Drawable? = null,
    override val title: String,
    override val dataProvider: KutePreferenceDataProvider,
    override val onPreferenceChangedListener: ((oldValue: Long, newValue: Long) -> Unit)? = null,
    override val onClick: (() -> Unit)? = null,
    override val onLongClick: (() -> Unit)? = null,
) : KutePreferenceItem<Long>, KutePreferenceListItem {

    override val key: Int
        get() = R.string.last_offline_cache_update_key

    override val persistedValue by lazy { getPersistedValueFlow() }

    override fun getDefaultValue(): Long = Long.MIN_VALUE

    override fun createDescription(currentValue: Long): String {
        return when (currentValue) {
            Long.MIN_VALUE -> "N/A"
            else -> dateFormatter.format(
                LocalDateTime.ofEpochSecond(currentValue, 0, ZoneOffset.UTC)
            )
        }
    }

    override fun search(searchTerm: String) =
        listOf(title, description).containsAnyWord(searchTerm)

    fun updateToNow() {
        persistValue(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
    }

    companion object {
        private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}