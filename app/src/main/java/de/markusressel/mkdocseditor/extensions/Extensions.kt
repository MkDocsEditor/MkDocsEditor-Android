package de.markusressel.mkdocseditor.extensions

import android.content.Context
import android.net.wifi.WifiManager
import android.os.AsyncTask
import de.markusressel.mkdocseditor.R
import java.util.*


/**
 * Created by Markus on 15.02.2018.
 */
fun ClosedRange<Int>.random() = Random().nextInt(endInclusive - start) + start

/**
 * Returns true if the current device is considered a tablet
 */
fun Context.isTablet(): Boolean {
    return resources
            .getBoolean(R.bool.is_tablet)
}

fun Any.doAsync(handler: () -> Unit) {
    object : AsyncTask<Void, Void, Void?>() {
        override fun doInBackground(vararg p0: Void?): Void? {
            handler()
            return null
        }
    }
            .execute()
}

fun Throwable.prettyPrint(): String {
    val message = "${this.message}:\n" + "${this.stackTrace.joinToString(separator = "\n")}}"

    return message
}

/**
 * Checks if WiFi is currently enabled on the device
 */
fun Context.isWifiEnabled(): Boolean {
    val wifiManager: WifiManager = this.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wifiManager
            .isWifiEnabled
}