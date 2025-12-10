package com.hikemvp.group

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class GroupWifiP2pHelper(private val activity: Activity) {

    companion object {
        const val REQ_PERM_WIFI = 10045
        const val ERR_NO_PERMISSION = -50
        const val ERR_CALL_FAILED   = -51
        const val ERR_INVALID_ARG   = -52
    }

    private val ctx: Context = activity.applicationContext

    private val manager: WifiP2pManager? =
        ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(ctx, Looper.getMainLooper(), null)

    private var isStarted = false

    private var lastPeers: WifiP2pDeviceList? = null
    private var peersCallback: ((WifiP2pDeviceList?) -> Unit)? = null

    private var pendingOnSuccess: ((WifiP2pInfo?) -> Unit)? = null
    private var pendingOnFailure: ((Int) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun hasWifiP2pPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, "android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun ensureWifiP2pPermission(requestCode: Int = REQ_PERM_WIFI): Boolean {
        if (hasWifiP2pPermission()) return true
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(activity, arrayOf("android.permission.NEARBY_WIFI_DEVICES"), requestCode)
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), requestCode)
        }
        return false
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (!hasWifiP2pPermission()) {
                        peersCallback?.invoke(null); peersCallback = null; return
                    }
                    try {
                        manager?.requestPeers(channel) { list ->
                            lastPeers = list
                            peersCallback?.let { cb -> peersCallback = null; cb(list) }
                        }
                    } catch (_: SecurityException) {
                        peersCallback?.invoke(null); peersCallback = null
                    } catch (_: Throwable) {
                        peersCallback?.invoke(lastPeers); peersCallback = null
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (!hasWifiP2pPermission()) { pendingOnFailure?.invoke(ERR_NO_PERMISSION); clearPending(); return }
                    try {
                        val netInfo: NetworkInfo? = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }
                        if (netInfo?.isConnected == true) {
                            try {
                                manager?.requestConnectionInfo(channel) { info ->
                                    pendingOnSuccess?.invoke(info); clearPending()
                                }
                            } catch (_: SecurityException) {
                                pendingOnFailure?.invoke(ERR_NO_PERMISSION); clearPending()
                            } catch (_: Throwable) {
                                pendingOnFailure?.invoke(ERR_CALL_FAILED); clearPending()
                            }
                        } else {
                            pendingOnFailure?.invoke(ERR_CALL_FAILED); clearPending()
                        }
                    } catch (_: SecurityException) {
                        pendingOnFailure?.invoke(ERR_NO_PERMISSION); clearPending()
                    } catch (_: Throwable) {
                        pendingOnFailure?.invoke(ERR_CALL_FAILED); clearPending()
                    }
                }
            }
        }
    }

    private fun clearPending() { pendingOnSuccess = null; pendingOnFailure = null }

    fun start() {
        if (isStarted) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                activity.registerReceiver(receiver, filter)
            }
            isStarted = true
        } catch (_: Throwable) { }
    }

    fun stop() {
        if (!isStarted) return
        try { activity.unregisterReceiver(receiver) } catch (_: Throwable) { }
        isStarted = false
    }

    fun discoverOnce(onPeers: (WifiP2pDeviceList?) -> Unit) {
        if (!hasWifiP2pPermission()) { onPeers(null); return }
        peersCallback = onPeers
        try {
            manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { requestPeers { list -> peersCallback?.invoke(list); peersCallback = null } }
                override fun onFailure(reason: Int) { peersCallback?.invoke(lastPeers); peersCallback = null }
            })
        } catch (_: SecurityException) {
            peersCallback?.invoke(null); peersCallback = null
        } catch (_: Throwable) {
            peersCallback?.invoke(lastPeers); peersCallback = null
        }
        Handler(Looper.getMainLooper()).postDelayed({
            peersCallback?.let { cb -> cb(lastPeers); peersCallback = null }
        }, 1200L)
    }

    fun requestPeers(onPeers: (WifiP2pDeviceList?) -> Unit) {
        if (!hasWifiP2pPermission()) { onPeers(null); return }
        try {
            manager?.requestPeers(channel) { list -> lastPeers = list; onPeers(list) } ?: onPeers(lastPeers)
        } catch (_: SecurityException) { onPeers(null) }
        catch (_: Throwable) { onPeers(lastPeers) }
    }

    fun connectTo(device: WifiP2pDevice, onConnected: (WifiP2pInfo?) -> Unit = { _ -> }, onError: (Int) -> Unit = { _ -> }) {
        connectToInternal(device.deviceAddress ?: "", onConnected, onError)
    }

    fun connectTo(mac: String, onConnected: (WifiP2pInfo?) -> Unit = { _ -> }, onError: (Int) -> Unit = { _ -> }) {
        connectToInternal(mac, onConnected, onError)
    }

    fun connectTo(device: WifiP2pDevice, onResult: (Boolean, WifiP2pInfo?) -> Unit) {
        connectToInternal(device.deviceAddress ?: "", { info -> onResult(true, info) }, { _ -> onResult(false, null) })
    }

    fun connectTo(mac: String, onResult: (Boolean, WifiP2pInfo?) -> Unit) {
        connectToInternal(mac, { info -> onResult(true, info) }, { _ -> onResult(false, null) })
    }

    private fun connectToInternal(mac: String, onConnected: (WifiP2pInfo?) -> Unit, onError: (Int) -> Unit) {
        if (mac.isBlank()) { onError(ERR_INVALID_ARG); return }
        if (!hasWifiP2pPermission()) { onError(ERR_NO_PERMISSION); return }
        try {
            val cfg = WifiP2pConfig().apply { deviceAddress = mac }
            pendingOnSuccess = onConnected
            pendingOnFailure = onError
            manager?.connect(channel, cfg, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { /* via receiver */ }
                override fun onFailure(reason: Int) { pendingOnFailure?.invoke(reason); clearPending() }
            }) ?: run { onError(ERR_CALL_FAILED); clearPending() }
        } catch (_: SecurityException) { onError(ERR_NO_PERMISSION); clearPending() }
        catch (_: Throwable) { onError(ERR_CALL_FAILED); clearPending() }
    }

    fun disconnect(onDone: () -> Unit) {
        if (!hasWifiP2pPermission()) { onDone(); return }
        try {
            manager?.requestGroupInfo(channel) { group ->
                if (group != null) {
                    try {
                        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() { onDone() }
                            override fun onFailure(reason: Int) { onDone() }
                        }) ?: onDone()
                    } catch (_: SecurityException) { onDone() }
                    catch (_: Throwable) { onDone() }
                } else { onDone() }
            } ?: onDone()
        } catch (_: SecurityException) { onDone() }
        catch (_: Throwable) { onDone() }
    }
}
