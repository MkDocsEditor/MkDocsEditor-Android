package de.markusressel.mkdocseditor.extensions.common.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import de.markusressel.mkdocseditor.R


/**
 * Returns true if the current device is considered a tablet
 */
fun Context.isTablet(): Boolean {
    return resources
            .getBoolean(R.bool.is_tablet)
}

fun Fragment.context(): Context {
    return this.context as Context
}

fun Float.pxToSp(context: Context): Float {
    return this / context.resources.displayMetrics.scaledDensity
}

/**
 * Create a layout inflater from this context
 */
fun Context.layoutInflater(): LayoutInflater {
    return LayoutInflater
            .from(this)
}

/**
 * Computes a URL to a resource file
 */
fun resourceToURL(context: Context, resID: Int): String {
    return ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.resources.getResourcePackageName(resID) + '/'.toString() + context.resources.getResourceTypeName(resID) + '/'.toString() + context.resources.getResourceEntryName(resID)
}

/**
 * Computes the URI to a resource file
 */
fun resourceToUri(context: Context, resID: Int): Uri {
    return Uri
            .parse(resourceToURL(context, resID))
}