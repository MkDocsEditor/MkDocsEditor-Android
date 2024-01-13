package de.markusressel.mkdocseditor.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NetworkManager @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager,
) {

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

        val runtime = Runtime.getRuntime()
        try {
            val ipProcess = runtime.exec("/system/bin/ping -c 1 -W $timeoutInSeconds $url")
            val exitValue = ipProcess.waitFor()
            isInternetconnected = exitValue == 0
        } catch (e: IOException) {
            Timber.d(e)
        } catch (e: InterruptedException) {
            Timber.d(e)
        }

        Timber.d("isInternetConnected: %s", isInternetconnected)
        return isInternetconnected
    }

    /**
     * checks if WLAN is connected
     *
     * @return false if WLAN is not connected
     */
    fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val isWifiConnected = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        Timber.d("isWifiConnected: $isWifiConnected")
        return isWifiConnected
    }

    /**
     * checks if Ethernet is connected
     *
     * @return false if Ethernet is not connected
     */
    fun isEthernetConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val isEthernetConnected = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        Timber.d("isEthernetConnected: $isEthernetConnected")
        return isEthernetConnected
    }

    /**
     * checks if Cellular is connected
     *
     * @return false if Cellular is not connected
     */
    fun isCellularConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val isCellularConnected = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        Timber.d("isCellularConnected: $isCellularConnected")
        return isCellularConnected
    }

    /**
     * checks if any kind of network connection is connected
     *
     * @return true if a network connection is connected, false otherwise
     */
    fun isNetworkConnected(): Boolean {
        val networkInfo = connectivityManager.activeNetworkInfo

        val isNetworkConnected = networkInfo != null && networkInfo.isConnectedOrConnecting
        Timber.d("isNetworkConnected: $isNetworkConnected")
        return isNetworkConnected
    }

    /**
     * Get SSID of connected WiFi Network
     *
     * @return SSID of connected WiFi Network, empty string if no WiFi connection
     */
    fun getConnectedWifiSSID(): String? {
        return if (isWifiConnected()) {
            val info = wifiManager.connectionInfo
            var ssid = info.ssid

            // remove unnecessary quotation marks
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }

            Timber.d("connected SSID: $ssid")
            ssid
        } else {
            null
        }
    }

}