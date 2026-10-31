package de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The passkey providers of this device, as far as an app can tell
 */
interface CredentialProviders {
    fun isInstalled(packageName: String): Boolean

    /**
     * Android 14+ has a settings page for the credential providers; some OEM builds lack it
     */
    fun canOpenProviderSettings(): Boolean

    /** @return false when the settings page could not be opened */
    fun openProviderSettings(activityContext: Context): Boolean

    fun openStore(activityContext: Context, url: String): Boolean
}

@Singleton
internal class AndroidCredentialProviders @Inject constructor(
    @ApplicationContext private val context: Context,
) : CredentialProviders {

    override fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun canOpenProviderSettings(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            providerSettingsIntent().resolveActivity(context.packageManager) != null

    override fun openProviderSettings(activityContext: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return try {
            activityContext.startActivity(providerSettingsIntent())
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    override fun openStore(activityContext: Context, url: String): Boolean = try {
        activityContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun providerSettingsIntent(): Intent =
        Intent(Settings.ACTION_CREDENTIAL_PROVIDER).setData(Uri.parse("package:${context.packageName}"))
}
