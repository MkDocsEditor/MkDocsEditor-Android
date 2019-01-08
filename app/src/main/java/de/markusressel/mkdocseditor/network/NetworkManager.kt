package de.markusressel.mkdocseditor.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import androidx.lifecycle.MutableLiveData
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class NetworkManager @Inject constructor(
        private val context: Context) {

    val connectionStatus = MutableLiveData<Int>().apply { value = getNetworkStatusType() }

    private val connectivityChangeListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            connectionStatus.value = getNetworkStatusType()
        }
    }

    init {
        val intentFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        context.registerReceiver(connectivityChangeListener, intentFilter)
    }

    fun getNetworkStatusType(): Int {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        return networkInfo.type
    }

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
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo

        val isWifiConnected = networkInfo != null && ConnectivityManager.TYPE_WIFI == networkInfo.type && networkInfo.isConnectedOrConnecting
        Timber.d("isWifiConnected: $isWifiConnected")
        return isWifiConnected
    }

    /**
     * checks if Ethernet is connected
     *
     * @return false if Ethernet is not connected
     */
    fun isEthernetConnected(): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo

        val isEthernetConnected = networkInfo != null && ConnectivityManager.TYPE_ETHERNET == networkInfo.type && networkInfo.isConnectedOrConnecting
        Timber.d("isEthernetConnected: $isEthernetConnected")
        return isEthernetConnected
    }

    /**
     * checks if GPRS is connected
     *
     * @return false if GPRS is not connected
     */
    fun isGprsConnected(): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo

        val isGprsConnected = networkInfo != null && ConnectivityManager.TYPE_MOBILE == networkInfo.type && networkInfo.isConnectedOrConnecting
        Timber.d("isGprsConnected: $isGprsConnected")
        return isGprsConnected
    }

    /**
     * checks if any kind of network connection is connected
     *
     * @return true if a network connection is connected, false otherwise
     */
    fun isNetworkConnected(): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo

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
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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