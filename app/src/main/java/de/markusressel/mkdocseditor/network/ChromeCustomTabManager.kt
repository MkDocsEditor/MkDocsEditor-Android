package de.markusressel.mkdocseditor.network

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.ThemeHelper
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Manager for Chrome custom tabs
 *
 * Created by Markus on 21.02.2016.
 */
@Singleton
class ChromeCustomTabManager @Inject constructor(
        private val context: Context,
        private val themeHelper: ThemeHelper) {

    /**
     * Opens a chrome custom tab with the specified URL.
     *
     * @param url the url to open
     */
    fun openChromeCustomTab(url: String) {
        val accentColor = themeHelper.getThemeAttrColor(context, R.attr.colorPrimary)

        val customTabsIntent = CustomTabsIntent.Builder()
                .setToolbarColor(accentColor)
                .build()

        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, url.toUri())
    }

}
