
package com.hikemvp.group

import android.app.*
import android.content.*
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.hikemvp.R
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class GroupNearbyService : Service(), LocationListener {

    companion object {
        private const val CHANNEL_ID = "group_live"
        private const val NOTIF_ID = 42

        const val ACTION_START_HOST   = "grp.start.host"
        const val ACTION_START_MEMBER = "grp.start.member"
        const val ACTION_STOP         = "grp.stop"
        const val ACTION_ACCEPT       = "grp.accept"
        const val ACTION_REJECT       = "grp.reject"

        private const val EXTRA_MY_ID   = "my_id"
        private const val EXTRA_MY_NAME = "my_name"
        private const val EXTRA_PIN     = "pin"
        private const val EXTRA_ENDPOINT = "endpointId"
        private const val EXTRA_ENDPOINT_NAME = "endpointName"

        fun startHost(ctx: Context, myId: String, myName: String, pin: String? = null) {
            val it = Intent(ctx, GroupNearbyService::class.java).apply {
                action = ACTION_START_HOST
                putExtra(EXTRA_MY_ID, myId)
                putExtra(EXTRA_MY_NAME, myName)
                if (pin != null) putExtra(EXTRA_PIN, pin)
            }
            ContextCompat.startForegroundService(ctx, it)
        }
        fun startMember(ctx: Context, myId: String, myName: String, pin: String) {
            val it = Intent(ctx, GroupNearbyService::class.java).apply {
                action = ACTION_START_MEMBER
                putExtra(EXTRA_MY_ID, myId)
                putExtra(EXTRA_MY_NAME, myName)
                putExtra(EXTRA_PIN, pin)
            }
            ContextCompat.startForegroundService(ctx, it)
        }
        fun stop(ctx: Context) {
            val it = Intent(ctx, GroupNearbyService::class.java).apply { action = ACTION_STOP }
            ctx.startService(it)
        }
    }

    private val prefs by lazy { getSharedPreferences("group_nearby", Context.MODE_PRIVATE) }
    private val connections by lazy { Nearby.getConnectionsClient(this) }
    private val connected = java.util.concurrent.ConcurrentHashMap<String, String>() // endpointId -> name
    private var myId: String = ""
    private var myName: String = ""
    private var isHost = false
    private var pinCode: String = "0000"

    private var locMan: LocationManager? = null
    private var lastSent: Location? = null

    private val minTimeMs get() = prefs.getLong("minTimeMs", 10_000L)
    private val minDistM  get() = prefs.getFloat("minDistM", 5f)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotif(titleBase(), "—"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_HOST -> {
                isHost = true
                myId = intent.getStringExtra(EXTRA_MY_ID) ?: ""
                myName = intent.getStringExtra(EXTRA_MY_NAME) ?: ""
                pinCode = intent.getStringExtra(EXTRA_PIN) ?: com.hikemvp.group.GroupPin.generate4()
                prefs.edit().putString("role", "host").putString("myId", myId).putString("myName", myName).putString("pin", pinCode).apply()
                startAdvertising()
                updateNotif(titleBase(), "PIN " + pinCode + " · en attente…")
                requestLocation()
            }
            ACTION_START_MEMBER -> {
                isHost = false
                myId = intent.getStringExtra(EXTRA_MY_ID) ?: ""
                myName = intent.getStringExtra(EXTRA_MY_NAME) ?: ""
                pinCode = intent.getStringExtra(EXTRA_PIN) ?: ""
                prefs.edit().putString("role", "member").putString("myId", myId).putString("myName", myName).putString("pin", pinCode).apply()
                startDiscovery()
                updateNotif(titleBase(), "Recherche d’hôte…")
                requestLocation()
            }
            ACTION_ACCEPT -> {
                val ep = intent.getStringExtra(EXTRA_ENDPOINT) ?: return START_STICKY
                connections.acceptConnection(ep, payloadCallback)
            }
            ACTION_REJECT -> {
                val ep = intent.getStringExtra(EXTRA_ENDPOINT) ?: return START_STICKY
                connections.rejectConnection(ep)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { connections.stopAllEndpoints() } catch (_: Throwable) {}
        try { if (isHost) connections.stopAdvertising() else connections.stopDiscovery() } catch (_: Throwable) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        locMan?.removeUpdates(this)
    }

    private fun startAdvertising() {
        val opts = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connections.startAdvertising(
            myName + "|" + pinCode,
            packageName,
            lifecycle,
            opts
        ).addOnSuccessListener {
            updateNotif(titleBase(), "PIN " + pinCode + " · en attente…")
        }.addOnFailureListener {
            updateNotif(titleBase(), "Pub échouée: " + it.message)
        }
    }

    private fun startDiscovery() {
        val opts = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connections.startDiscovery(
            packageName,
            endpointCb,
            opts
        ).addOnSuccessListener {
            updateNotif(titleBase(), "Recherche d’hôte…")
        }.addOnFailureListener {
            updateNotif(titleBase(), "Recherche échouée: " + it.message)
        }
    }

    private val endpointCb = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val pieces = info.endpointName.split("|")
            val hostPin = if (pieces.size >= 2) pieces[1] else ""
            if (hostPin == pinCode) {
                connections.requestConnection(myName, endpointId, lifecycle)
            }
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connInfo: ConnectionInfo) {
            if (isHost) {
                showJoinPrompt(endpointId, connInfo.endpointName)
            } else {
                connections.acceptConnection(endpointId, payloadCallback)
            }
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected[endpointId] = "?"
                broadcastConnected()
                updateNotif(titleBase(), namesLine())
                if (!isHost) {
                    send(endpointId, com.hikemvp.group.GroupWire.JoinRequest(myId, myName, pinCode))
                }
            }
        }
        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            broadcastConnected()
            updateNotif(titleBase(), namesLine())
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                com.hikemvp.group.GroupWire.decode(payload.asBytes() ?: return)?.let { msg ->
                    when (msg) {
                        is com.hikemvp.group.GroupWire.JoinRequest -> {
                            val ok = (msg.pin == pinCode)
                            if (!ok) {
                                try { connections.rejectConnection(endpointId) } catch (_: Throwable) {}
                                return
                            }
                            connected[endpointId] = msg.name
                            send(endpointId, com.hikemvp.group.GroupWire.JoinAck(true, null))
                            broadcastConnected()
                            updateNotif(titleBase(), namesLine())
                        }
                        is com.hikemvp.group.GroupWire.JoinAck -> {
                            updateNotif(titleBase(), namesLine())
                        }
                        is com.hikemvp.group.GroupWire.Position -> {
                            val it = Intent("com.hikemvp.group.POSITION")
                                .putExtra("id", msg.id)
                                .putExtra("lat", msg.lat)
                                .putExtra("lon", msg.lon)
                                .putExtra("acc", msg.acc)
                                .putExtra("ts", msg.ts)
                            sendBroadcast(it)
                            pushCloud(msg)
                        }
                        is com.hikemvp.group.GroupWire.Leave -> {
                        }
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun send(endpointId: String, msg: com.hikemvp.group.GroupWire) {
        connections.sendPayload(endpointId, Payload.fromBytes(com.hikemvp.group.GroupWire.encode(msg)))
    }
    private fun sendAll(msg: com.hikemvp.group.GroupWire) {
        if (connected.isEmpty()) return
        connections.sendPayload(java.util.ArrayList(connected.keys), Payload.fromBytes(com.hikemvp.group.GroupWire.encode(msg)))
    }

    private fun requestLocation() {
        locMan = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locMan?.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMs, minDistM, this)
        } catch (_: SecurityException) {
        }
    }

    override fun onLocationChanged(location: Location) {
        val now = System.currentTimeMillis()
        val acc = if (location.hasAccuracy()) location.accuracy else 0f
        lastSent = location
        val msg = com.hikemvp.group.GroupWire.Position(
            id = myId,
            lat = location.latitude,
            lon = location.longitude,
            acc = acc,
            ts = now
        )
        sendAll(msg)
        pushCloud(msg)
        val it = Intent("com.hikemvp.group.POSITION")
            .putExtra("id", msg.id)
            .putExtra("lat", msg.lat)
            .putExtra("lon", msg.lon)
            .putExtra("acc", msg.acc)
            .putExtra("ts", msg.ts)
        sendBroadcast(it)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID,
                getString(R.string.groupx_live_channel_name),
                NotificationManager.IMPORTANCE_LOW)
            ch.description = getString(R.string.groupx_live_channel_desc)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun titleBase(): String {
        val role = if (isHost) "Hôte" else "Membre"
        return "Groupe actif — " + role
    }

    private fun namesLine(): String {
        val names = connected.values.filter { it.isNotBlank() }.sorted()
        val text = if (names.isEmpty()) "Aucun membre" else names.joinToString(", ")
        return if (text.length <= 60) text else text.take(57) + "…"
    }

    private fun buildNotif(title: String, text: String): Notification {
        val stopIntent = Intent(this, GroupNearbyService::class.java).apply { action = ACTION_STOP }
        val stopPI = PendingIntent.getService(this, 100, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.groupx_action_stop), stopPI)
            .build()
    }

    private fun updateNotif(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(title, text))
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(titleBase(), text))
    }

    private fun broadcastConnected() {
        val it = Intent("com.hikemvp.group.CONNECTED")
        val arr = connected.values.filter { it.isNotBlank() }.toTypedArray()
        it.putExtra("names", arr)
        sendBroadcast(it)
    }

    private fun showJoinPrompt(endpointId: String, endpointName: String) {
        val accept = Intent(this, GroupNearbyService::class.java).apply {
            action = ACTION_ACCEPT
            putExtra(EXTRA_ENDPOINT, endpointId)
            putExtra(EXTRA_ENDPOINT_NAME, endpointName)
        }
        val reject = Intent(this, GroupNearbyService::class.java).apply {
            action = ACTION_REJECT
            putExtra(EXTRA_ENDPOINT, endpointId)
            putExtra(EXTRA_ENDPOINT_NAME, endpointName)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.groupx_join_request_title, endpointName))
            .setContentText(getString(R.string.groupx_join_request_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, getString(R.string.groupx_action_accept),
                PendingIntent.getService(this, endpointId.hashCode(), accept, PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, getString(R.string.groupx_action_reject),
                PendingIntent.getService(this, endpointId.hashCode()+1, reject, PendingIntent.FLAG_IMMUTABLE))
            .build()
        nm.notify(endpointId.hashCode(), notif)
    }

    private fun hasInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun groupId(): String {
        val base = prefs.getString("pin", "0000") ?: "0000"
        return "grp_" + base
    }

    private fun pushCloud(msg: com.hikemvp.group.GroupWire.Position) {
        if (!hasInternet()) return
        try {
            val clazz = Class.forName("com.google.firebase.database.FirebaseDatabase")
            val getInstance = clazz.getMethod("getInstance")
            val db = getInstance.invoke(null)
            val refMethod = clazz.getMethod("getReference", String::class.java)
            val ref = refMethod.invoke(db, "groups/" + groupId() + "/members/" + msg.id)
            val map = java.util.HashMap<String, Any>()
            map["lat"] = msg.lat; map["lon"] = msg.lon; map["acc"] = msg.acc; map["ts"] = msg.ts; map["name"] = myName
            val refClass = Class.forName("com.google.firebase.database.DatabaseReference")
            val setValue = refClass.getMethod("setValue", Object::class.java)
            setValue.invoke(ref, map)
        } catch (_: Throwable) {
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
