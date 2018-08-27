package de.markusressel.mkdocseditor.extensions.common.android

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper

fun Any.doAsync(handler: () -> Unit) {
    object : AsyncTask<Void, Void, Void?>() {
        override fun doInBackground(vararg p0: Void?): Void? {
            handler()
            return null
        }
    }
            .execute()
}

fun runOnUiThread(action: () -> Unit) {
    if (isMainLooperAlive()) {
        action()
    } else {
        Handler(Looper.getMainLooper())
                .post(Runnable(action))
    }
}

fun runDelayed(delayMillis: Long, action: () -> Unit) = Handler().postDelayed(Runnable(action), delayMillis)

fun runDelayedOnUiThread(delayMillis: Long, action: () -> Unit) = Handler(Looper.getMainLooper()).postDelayed(Runnable(action), delayMillis)

private fun isMainLooperAlive() = Looper.myLooper() == Looper.getMainLooper()