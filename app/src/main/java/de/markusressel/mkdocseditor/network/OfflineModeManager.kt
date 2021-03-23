package de.markusressel.mkdocseditor.network

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.github.ajalt.timberkt.Timber
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.DocumentPersistenceManager
import de.markusressel.mkdocseditor.service.OfflineCacheSyncService
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

    var isEnabled = MutableLiveData<Boolean>().apply { value = preferenceDataProvider.getValueUnsafe(R.string.offline_mode_key, false) }

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
        val jobScheduler = ContextCompat.getSystemService(context, JobScheduler::class.java)
        if (jobScheduler == null) {
            Timber.w { "JobScheduler service is not available, background offline cache update will not work!" }
            return
        }

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

        val serviceComponent = ComponentName(context, OfflineCacheSyncService::class.java)
        val builder = JobInfo.Builder(0, serviceComponent).apply {
            setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // require any network connection
//                setRequiresDeviceIdle(true) // device should be idle
            setRequiresCharging(false) // we don't care if the device is charging or not
            setExtras(PersistableBundle().apply {
                putStringArray(OfflineCacheSyncService.DOCUMENT_IDS_KEY, documentsToUpdate.toSet().toTypedArray())
            })
        }

        jobScheduler.schedule(builder.build())
    }

}
