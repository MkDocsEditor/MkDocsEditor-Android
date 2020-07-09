package de.markusressel.mkdocseditor.view.activity.base

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.ajalt.timberkt.Timber
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.service.OfflineSyncWorker
import de.markusressel.mkdocseditor.service.OfflineSyncWorker.Companion.DOCUMENT_IDS_KEY
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Manages all things related to the offline mode
 */
@Singleton
class OfflineModeManager @Inject constructor(
        private val context: Context,
        private val preferenceDataProvider: KutePreferenceDataProvider,
        private val documentPersistenceManager: DocumentPersistenceManager) {

    var isEnabled = MutableLiveData<Boolean>().apply {
        value = preferenceDataProvider.getValueUnsafe(R.string.offline_mode_key, false)
    }

    private val colorOn by lazy { ContextCompat.getColor(context, R.color.md_orange_800) }
    private val colorOff by lazy { ContextCompat.getColor(context, R.color.textColorPrimary) }

    /**
     * @return true if the offline mode is active, false otherwise
     */
    fun isEnabled(): Boolean {
        return isEnabled.value!!
    }

    /**
     * Enable or disable offline mode
     *
     * @param enabled true enables it, false disables it
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
        preferenceDataProvider.storeValueUnsafe(R.string.offline_mode_key, enabled)
    }

    @ColorInt
    fun getColor(): Int {
        return if (isEnabled()) {
            colorOn
        } else {
            colorOff
        }
    }

    /**
     * Schedules an update of the offline cache of all document files
     *
     * @param documentIds optionally specify a list of document id's to update
     */
    fun scheduleOfflineCacheUpdate(documentIds: Collection<String>? = null, evenInOfflineMode: Boolean = false) {
        if (isEnabled() && !evenInOfflineMode) {
            Timber.d { "Offline mode is active, no offline cache update is scheduled." }
            return
        }

        val documentsToUpdate = documentIds
                ?: documentPersistenceManager.standardOperation().all.map { it.id }
        if (documentsToUpdate.isEmpty()) {
            Timber.d { "No documentIds provided, nothing to schedule." }
            return
        }

        val workerData: Data = workDataOf(
                DOCUMENT_IDS_KEY to documentsToUpdate.toTypedArray()
        )

        val offlineSyncWorker = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setInputData(workerData)
                .build()
        WorkManager.getInstance(context).enqueue(offlineSyncWorker)
    }

}
