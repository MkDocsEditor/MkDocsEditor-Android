package de.markusressel.mkdocseditor.feature.notifications.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.notifications.domain.PushDistributor
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.ResolvedDistributor
import timber.log.Timber
import javax.inject.Inject

internal class UnifiedPushDistributor @Inject constructor(
    @ApplicationContext private val context: Context,
) : PushDistributor {

    override fun select(): Boolean {
        if (UnifiedPush.getSavedDistributor(context) != null) return true
        val distributor = when (val resolved = UnifiedPush.resolveDefaultDistributor(context)) {
            is ResolvedDistributor.Found -> resolved.packageName
            // the system asks which app to use only from an activity, so this runs without asking
            else -> pick(UnifiedPush.getDistributors(context).filter { it != context.packageName })
        } ?: return false
        UnifiedPush.saveDistributor(context, distributor)
        return true
    }

    override fun register(vapidKey: String) {
        try {
            UnifiedPush.register(context, messageForDistributor = context.getString(R.string.app_name), vapid = vapidKey)
        } catch (e: UnifiedPush.VapidNotValidException) {
            Timber.w(e, "The server has a VAPID key UnifiedPush does not take")
        }
    }

    override fun unregister() = UnifiedPush.unregister(context)

    private companion object {
        /** ntfy, which the family's server is set up for */
        const val NTFY = "io.heckel.ntfy"

        fun pick(distributors: List<String>): String? = distributors.firstOrNull { it == NTFY } ?: distributors.firstOrNull()
    }
}
