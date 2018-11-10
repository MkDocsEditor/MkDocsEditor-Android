package de.markusressel.mkdocseditor.extensions.common.android

import android.content.Context
import de.markusressel.mkdocseditor.R


/**
 * Returns true if the current device is considered a tablet
 */
fun Context.isTablet(): Boolean {
    return resources
            .getBoolean(R.bool.is_tablet)
}

fun androidx.fragment.app.Fragment.context(): Context {
    return this.context as Context
}