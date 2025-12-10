package com.hikemvp.group

import android.content.Context
import android.net.*
import android.os.Build
import android.util.Log

class GroupWifiBridge(
    private val context: Context,
    private val onStatusChanged: (Boolean) -> Unit
) {

    private val cm: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var isStarted = false
    private var lastIsWifi = false

    private val request: NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { update(true, "onAvailable") }
        override fun onLost(network: Network) { update(false, "onLost") }
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        try { cm.registerNetworkCallback(request, cb) } catch (t: Throwable) {
            Log.w("GroupWifi", "registerNetworkCallback failed: ${t.message}")
        }
        val now = isOnWifi()
        lastIsWifi = now
        onStatusChanged.invoke(now)
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        try { cm.unregisterNetworkCallback(cb) } catch (_: Throwable) {}
    }

    fun isOnWifi(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 23) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected && info.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (_: Throwable) { false }
    }

    private fun update(nowWifi: Boolean, tag: String) {
        if (nowWifi != lastIsWifi) {
            lastIsWifi = nowWifi
            Log.d("GroupWifi", "Wi-Fi status changed ($tag): $nowWifi")
            onStatusChanged.invoke(nowWifi)
        }
    }
}
