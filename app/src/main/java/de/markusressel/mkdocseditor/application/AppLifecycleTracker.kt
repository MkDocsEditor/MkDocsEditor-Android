package de.markusressel.mkdocseditor.application;

import android.app.Activity
import android.app.Application
import android.os.Bundle

class AppLifecycleTracker : Application.ActivityLifecycleCallbacks {

    private var numStarted = 0

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {}

    override fun onActivityStarted(p0: Activity) {
        if (numStarted == 0) {
            // app went to foreground
            onAppForeground()
        }
        numStarted++
    }

    private fun onAppForeground() {}

    override fun onActivityResumed(p0: Activity) {}

    override fun onActivityPaused(p0: Activity) {}

    override fun onActivityStopped(p0: Activity) {
        numStarted--
        if (numStarted == 0) {
            // app went to background
            onAppBackground()
        }
    }

    private fun onAppBackground() {}

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}

    override fun onActivityDestroyed(p0: Activity) {}

}