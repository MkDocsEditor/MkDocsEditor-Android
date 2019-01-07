package de.markusressel.mkdocseditor.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.CheckResult
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.ThemeHelper
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for Chrome custom tabs
 *
 * Created by Markus on 21.02.2016.
 */
@Singleton
class ChromeCustomTabManager @Inject constructor(val context: Context, val themeHelper: ThemeHelper) {

    /**
     * Opens a chrome custom tab with the specified URL.
     *
     * @param url the url to open
     */
    fun openChromeCustomTab(url: String) {
        val intent = getIntent(url)
        context.startActivity(intent)
    }

    @CheckResult
    private fun getIntent(url: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val accentColor = themeHelper
                .getThemeAttrColor(context, R.attr.colorPrimary)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            val extras = Bundle()
            extras
                    .putBinder(EXTRA_CUSTOM_TABS_SESSION, null)
            intent
                    .putExtra(EXTRA_CUSTOM_TABS_TOOLBAR_COLOR, accentColor)
            intent
                    .putExtras(extras)

            // Optional. Use an ArrayList for specifying menu related params. There
            // should be a separate Bundle for each custom menu item.
            val menuItemBundleList = ArrayList<Bundle>()

            // For each menu item do:
            //        Bundle menuItem = new Bundle();
            //        menuItem.putString(KEY_CUSTOM_TABS_MENU_TITLE, "Share");
            //        menuItem.putParcelable(KEY_CUSTOM_TABS_PENDING_INTENT, PendingIntent.getActivity(context, 0, new Intent()));
            //        menuItemBundleList.add(menuItem);
            //
            //        intent.putParcelableArrayListExtra(EXTRA_CUSTOM_TABS_MENU_ITEMS, menuItemBundleList);
        }

        return intent
    }

    companion object {

        const val EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"
        const val EXTRA_CUSTOM_TABS_TOOLBAR_COLOR = "android.support.customtabs.extra.TOOLBAR_COLOR"

        // Key for the title string for a given custom menu item
        const val KEY_CUSTOM_TABS_MENU_TITLE = "android.support.customtabs.customaction.MENU_ITEM_TITLE"
        // Key that specifies the PendingIntent to launch when the action button
        // or menu item was tapped. Chrome will be calling PendingIntent#send() on
        // taps after adding the url as data. The client app can call
        // Intent#getDataString() to get the url.
        const val KEY_CUSTOM_TABS_PENDING_INTENT = "android.support.customtabs.customaction.PENDING_INTENT"

        const val EXTRA_CUSTOM_TABS_MENU_ITEMS = "android.support.customtabs.extra.MENU_ITEMS"
    }


}
