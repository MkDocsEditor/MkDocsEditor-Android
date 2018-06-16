package de.markusressel.mkdocseditor.navigation

import android.content.Context
import android.content.Intent
import android.support.v4.app.Fragment

/**
 * Created by Markus on 08.01.2018.
 */
class NavigationPage(val activityClass: Class<*>? = null, val fragment: (() -> Fragment)? = null, val tag: String? = null) {

    fun createIntent(context: Context): Intent {
        return Intent(context, activityClass)
    }

}