package de.markusressel.mkdocseditor.application;

import android.app.Activity
import android.app.Application
import android.os.Bundle

class AppLifecycleTracker : Application.ActivityLifecycleCallbacks {

    private var numStarted = 0

    override fun onActivityCreated(p0: Activity?, p1: Bundle?) {
    }

    override fun onActivitySaveInstanceState(p0: Activity?, p1: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity?) {
        if (numStarted == 0) {
            // app went to foreground
            onAppForeground()
        }
        numStarted++
    }

    private fun onAppForeground() {

    }

    override fun onActivityResumed(p0: Activity?) {
    }

    override fun onActivityPaused(p0: Activity?) {
    }

    override fun onActivityStopped(activity: Activity?) {
        numStarted--
        if (numStarted == 0) {
            // app went to background
            onAppBackground()
        }
    }

    private fun onAppBackground() {
    }

    override fun onActivityDestroyed(p0: Activity?) {
    }

}