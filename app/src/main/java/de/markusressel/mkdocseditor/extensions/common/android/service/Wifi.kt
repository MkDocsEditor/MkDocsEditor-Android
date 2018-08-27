package de.markusressel.mkdocseditor.extensions.common.android.service

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Checks if WiFi is currently enabled on the device
 */
fun Context.isWifiEnabled(): Boolean {
    val wifiManager: WifiManager = this.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wifiManager
            .isWifiEnabled
}