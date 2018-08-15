package de.markusressel.mkdocseditor.network

import de.markusressel.mkdocsrestclient.MkDocsRestClient
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerConnectivityManager @Inject constructor(val restClient: MkDocsRestClient) {


    /**
     * Checks if Internet access is connected
     *
     * Works by pinging the Google DNS (or the provided url instead)
     *
     * @param url Custom URL to ping, Google DNS otherwise
     * @return true if connected, false otherwise
     *
     * @see [http://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-timeouts](http://google.com)
     */
    fun isInternetConnected(url: String = "8.8.8.8", timeoutInSeconds: Int = 2): Boolean {
        var isInternetconnected = false

        val runtime = Runtime
                .getRuntime()
        try {
            val ipProcess = runtime
                    .exec("/system/bin/ping -c 1 -W $timeoutInSeconds $url")
            val exitValue = ipProcess
                    .waitFor()
            isInternetconnected = exitValue == 0
        } catch (e: IOException) {
            Timber
                    .d(e)
        } catch (e: InterruptedException) {
            Timber
                    .d(e)
        }

        Timber
                .d("isInternetConnected: %s", isInternetconnected)
        return isInternetconnected
    }

}