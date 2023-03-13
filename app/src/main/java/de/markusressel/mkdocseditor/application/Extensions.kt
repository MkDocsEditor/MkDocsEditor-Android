package de.markusressel.mkdocseditor.application

import android.content.Context
import android.content.Intent

/**
 * Triggers a restart of the current application.
 */
fun Context.triggerAppRebirth() {
    val packageManager = packageManager
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val componentName = intent!!.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}