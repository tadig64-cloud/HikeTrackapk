package com.hikemvp.group

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class GroupService : Service() {

    companion object {
        const val ACTION_STATE = "com.hikemvp.group.ACTION_STATE"
        const val ACTION_START_HOST = "com.hikemvp.group.ACTION_START_HOST"
        const val ACTION_START_JOIN = "com.hikemvp.group.ACTION_START_JOIN"
        const val ACTION_STOP = "com.hikemvp.group.ACTION_STOP"
        const val ACTION_PING = "com.hikemvp.group.ACTION_PING"

        const val EXTRA_ROLE = "extra_role"
        const val EXTRA_ADVERTISING = "extra_advertising"
        const val EXTRA_DISCOVERING = "extra_discovering"
        const val EXTRA_MEMBERS = "extra_members"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"

        // markers broadcast
        const val ACTION_GROUP_MARKERS = "com.hikemvp.group.ACTION_GROUP_MARKERS"
        const val EXTRA_MARKERS_JSON = "extra_markers_json"

        // deep link join
        const val EXTRA_JOIN_CODE = "extra_join_code"

        /**
         * Point d’entrée simple pour rejoindre un groupe depuis une Activity.
         * Démarre le service en ACTION_START_JOIN avec le code en extra.
         */
        fun requestJoin(ctx: Context, code: String) {
            val i = Intent(ctx, GroupService::class.java).apply {
                action = ACTION_START_JOIN
                putExtra(EXTRA_JOIN_CODE, code)
            }
            try {
                ctx.startService(i)
            } catch (t: Throwable) {
                Log.w("GroupService", "Impossible de démarrer le service pour join", t)
            }
        }
    }

    private val binder = Binder()
    private val handler = Handler(Looper.getMainLooper())

    private var role: String = "idle" // "host" | "guest" | "idle"
    private var advertising = false
    private var discovering = false
    private var members = mutableListOf<Member>()

    private var tickRunnable: Runnable? = null

    // Identité locale -> on garantit le #1 sans toucher l’UI.
    private var localId: String = "self"
    private var localName: String = "Me"

    data class Member(
        val id: String,
        var name: String,
        var lat: Double,
        var lon: Double,
        var color: Int
    )

    override fun onCreate() {
        super.onCreate()
        initLocalIdentity()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_HOST -> startHost()
            ACTION_START_JOIN -> {
                val code = intent.getStringExtra(EXTRA_JOIN_CODE)
                startGuest()
                if (!code.isNullOrBlank()) {
                    // Ici tu brancheras la logique réseau réelle (BLE/HTTP/etc.)
                    Log.d("GroupService", "Reçu code de join via deeplink: $code")
                    sendState("Join demandé (code=$code)")
                }
            }
            ACTION_STOP -> stopAll()
            ACTION_PING -> sendState("Ping envoyé")
            ACTION_STATE -> sendState(null)
        }
        return START_STICKY
    }

    private fun initLocalIdentity() {
        try {
            localId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "self"
        } catch (_: Throwable) {
            localId = "self"
        }
        localName = "Moi"
    }

    private fun ensureSelfMember() {
        val idx = members.indexOfFirst { it.id == localId }
        if (idx == -1) {
            members.add(
                0,
                Member(
                    id = localId,
                    name = localName,
                    lat = 43.6 + Random.nextDouble(-0.001, 0.001),
                    lon = 1.44 + Random.nextDouble(-0.001, 0.001),
                    color = 0xFF1E88E5.toInt()
                )
            )
        } else if (idx > 0) {
            val m = members.removeAt(idx)
            members.add(0, m)
        }
    }

    private fun startHost() {
        role = "host"
        advertising = true
        discovering = false
        ensureSelfMember()
        ensureTicker()
        sendState("Hôte prêt")
    }

    private fun startGuest() {
        role = "guest"
        advertising = false
        discovering = true
        ensureSelfMember()
        ensureTicker()
        sendState("Invité prêt")
    }

    private fun stopAll() {
        role = "idle"
        advertising = false
        discovering = false
        cancelTicker()
        sendState("Groupe arrêté")
    }

    private fun ensureTicker() {
        if (tickRunnable != null) return
        tickRunnable = object : Runnable {
            override fun run() {
                synthesizeMembers()
                broadcastMarkers()
                handler.postDelayed(this, 15_000L)
            }
        }
        handler.post(tickRunnable!!)
    }

    private fun cancelTicker() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun synthesizeMembers() {
        // Démo: membres factices. À remplacer par tes sources (GPS, réseau…)
        if (members.isEmpty()) {
            ensureSelfMember()
            repeat(3) { idx ->
                members.add(
                    Member(
                        id = "m$idx",
                        name = "Membre ${idx + 2}",
                        lat = 43.6 + Random.nextDouble(-0.01, 0.01),
                        lon = 1.44 + Random.nextDouble(-0.01, 0.01),
                        color = listOf(0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835).random().toInt()
                    )
                )
            }
        } else {
            members.forEach {
                it.lat += Random.nextDouble(-0.0005, 0.0005)
                it.lon += Random.nextDouble(-0.0005, 0.0005)
            }
        }
        ensureSelfMember()
    }

    private fun broadcastMarkers() {
        val ordered = members.sortedWith(compareBy<Member> { it.id != localId }.thenBy { it.name })
        val arr = JSONArray()
        ordered.forEach { m ->
            val o = JSONObject()
            o.put("id", m.id)
            o.put("name", m.name)
            o.put("lat", m.lat)
            o.put("lon", m.lon)
            o.put("color", m.color)
            arr.put(o)
        }
        val it = Intent(ACTION_GROUP_MARKERS)
        it.putExtra(EXTRA_MARKERS_JSON, arr.toString())
        sendBroadcast(it)
    }

    private fun sendState(message: String?) {
        val it = Intent(ACTION_STATE)
        it.putExtra(EXTRA_ROLE, role)
        it.putExtra(EXTRA_ADVERTISING, advertising)
        it.putExtra(EXTRA_DISCOVERING, discovering)
        it.putExtra(EXTRA_MEMBERS, members.size)
        if (message != null) it.putExtra(EXTRA_MESSAGE, message)
        sendBroadcast(it)
    }
}
