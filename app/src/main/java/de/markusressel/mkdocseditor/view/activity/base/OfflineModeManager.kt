package de.markusressel.mkdocseditor.view.activity.base

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.fragment.preferences.KutePreferencesHolder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all things related to the offline mode
 */
@Singleton
class OfflineModeManager @Inject constructor(val context: Context,
                                             val preferencesHolder: KutePreferencesHolder) {

    var isEnabled = MutableLiveData<Boolean>().apply { preferencesHolder.offlineModePreference.persistedValue }

    val colorOn by lazy { ContextCompat.getColor(context, R.color.md_orange_800) }
    val colorOff by lazy { ContextCompat.getColor(context, R.color.textColorPrimary) }

    /**
     * Enable or disable offline mode
     *
     * @param enabled true enables it, false disables it
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
        preferencesHolder.offlineModePreference.persistedValue = enabled
    }

    @ColorInt
    fun getColor(): Int {
        return if (isEnabled.value!!) {
            colorOn
        } else {
            colorOff
        }
    }

}
