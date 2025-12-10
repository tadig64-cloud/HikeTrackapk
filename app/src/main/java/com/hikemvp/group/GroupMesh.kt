package com.hikemvp.group

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.LinkedHashSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import android.net.wifi.p2p.WifiP2pInfo

/**
 * Mesh JSON sur Wi‑Fi Direct, robuste :
 * - Ajoute origin + ts pour dédoublonner et éviter les boucles
 * - Retry auto côté client si le socket tombe (backoff)
 * - Restart auto côté owner si le ServerSocket casse
 */
class GroupMesh private constructor(private val app: Context) {

    private val TAG = "GroupMesh"
    private val PORT = 8987

    // IO
    private val io = Executors.newCachedThreadPool()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private val clients = CopyOnWriteArrayList<Socket>()

    // State
    @Volatile private var running = false
    @Volatile private var isOwner = false
    private val myOrigin: String = "dev-" + (Build.MODEL ?: "Android")
    private var lastClientHost: String? = null
    private var reconnectFuture: ScheduledFuture<*>? = null
    private var serverWatchFuture: ScheduledFuture<*>? = null

    // Dedup (clé = "$origin:$ts")
    private val seen = LinkedHashSet<String>(512)

    // Hooks
    var onPeerPos: ((id: String, p: GeoPoint) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    fun start(info: WifiP2pInfo?) {
        if (running) return
        running = true
        isOwner = info?.isGroupOwner == true
        if (isOwner) {
            startAsOwner()
        } else {
            val host = info?.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
            lastClientHost = host
            startAsClient(host)
        }
        // heartbeat + hello initial
        scheduler.schedule({ sendHello() }, 500, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        running = false
        reconnectFuture?.cancel(true); reconnectFuture = null
        serverWatchFuture?.cancel(true); serverWatchFuture = null
        try { clientSocket?.close() } catch (_: Throwable) {}
        clientSocket = null
        for (s in clients) { try { s.close() } catch (_: Throwable) {} }
        clients.clear()
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
    }

    fun broadcastPosition(id: String, p: GeoPoint) {
        val now = System.currentTimeMillis()
        val msg = JSONObject()
            .put("t", "posUpdate")
            .put("id", id)
            .put("lat", p.latitude)
            .put("lon", p.longitude)
            .put("origin", myOrigin)
            .put("ts", now)
            .toString() + "\n"
        writeAll(msg)
    }

    fun sendHello() {
        val now = System.currentTimeMillis()
        val msg = JSONObject()
            .put("t", "hello")
            .put("id", myOrigin)
            .put("origin", myOrigin)
            .put("ts", now)
            .toString() + "\n"
        writeAll(msg)
    }

    private fun startAsOwner() {
        io.execute {
            try {
                serverSocket = ServerSocket(PORT)
                log("Owner: listening on $PORT")
            } catch (t: Throwable) {
                log("Owner open error: ${t.message}")
            }
            // Watch dog: si le socket casse, on retente
            serverWatchFuture = scheduler.scheduleAtFixedRate({
                if (!running) return@scheduleAtFixedRate
                if (serverSocket == null || serverSocket!!.isClosed) {
                    try {
                        serverSocket = ServerSocket(PORT)
                        log("Owner: restarted server on $PORT")
                    } catch (t: Throwable) {
                        log("Owner restart error: ${t.message}")
                    }
                }
            }, 2000, 2000, TimeUnit.MILLISECONDS)

            while (running) {
                try {
                    val ss = serverSocket ?: break
                    val s = ss.accept()
                    clients.add(s)
                    log("Owner: client connected ${s.inetAddress?.hostAddress}")
                    handleSocketAsOwner(s)
                } catch (t: Throwable) {
                    if (!running) break
                    log("Owner accept error: ${t.message}")
                    try { Thread.sleep(500) } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun handleSocketAsOwner(s: Socket) {
        io.execute {
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                while (running) {
                    val line = reader.readLine() ?: break
                    if (shouldProcess(line)) {
                        relayToOthers(s, line + "\n")
                        parseInbound(line)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                clients.remove(s)
                try { s.close() } catch (_: Throwable) {}
                log("Owner: client disconnected")
            }
        }
    }

    private fun relayToOthers(source: Socket, msg: String) {
        for (c in clients) {
            if (c == source) continue
            try {
                val w = BufferedWriter(OutputStreamWriter(c.getOutputStream()))
                w.write(msg); w.flush()
            } catch (_: Throwable) { }
        }
    }

    private fun startAsClient(host: String) {
        io.execute {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, PORT), 4000)
                clientSocket = s
                log("Client: connected to $host:$PORT")
                // reader
                io.execute {
                    try {
                        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                        while (running) {
                            val line = reader.readLine() ?: break
                            if (shouldProcess(line)) {
                                parseInbound(line)
                            }
                        }
                    } catch (_: Throwable) {
                    } finally {
                        try { s.close() } catch (_: Throwable) {}
                        clientSocket = null
                        log("Client: disconnected")
                        scheduleReconnect()
                    }
                }
            } catch (t: Throwable) {
                log("Client connect error: ${t.message}")
                clientSocket = null
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (isOwner) return
        if (!running) return
        val host = lastClientHost ?: return
        // backoff simple: 2s, 4s, 8s, max 30s
        reconnectFuture?.cancel(false)
        var delay = 2000L
        reconnectFuture = scheduler.scheduleAtFixedRate({
            if (!running) { reconnectFuture?.cancel(false); return@scheduleAtFixedRate }
            if (clientSocket != null && clientSocket!!.isConnected) return@scheduleAtFixedRate
            log("Client: reconnecting...")
            try {
                startAsClient(host)
                delay = (delay * 2).coerceAtMost(30000L)
            } catch (_: Throwable) { }
        }, delay, delay, TimeUnit.MILLISECONDS)
    }

    private fun writeAll(msg: String) {
        if (!running) return
        if (isOwner) {
            for (c in clients) {
                try {
                    val w = BufferedWriter(OutputStreamWriter(c.getOutputStream()))
                    w.write(msg); w.flush()
                } catch (_: Throwable) {}
            }
        } else {
            val s = clientSocket ?: return
            try {
                val w = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
                w.write(msg); w.flush()
            } catch (_: Throwable) {
                clientSocket = null
                scheduleReconnect()
            }
        }
    }

    private fun shouldProcess(txt: String): Boolean {
        return try {
            val o = JSONObject(txt)
            val origin = o.optString("origin", "")
            val ts = o.optLong("ts", 0L)
            if (origin.isNotBlank() && origin == myOrigin) return false // ignore self
            if (ts != 0L) {
                val key = "$origin:$ts"
                synchronized(seen) {
                    if (seen.contains(key)) return false
                    seen.add(key)
                    // limiter la taille
                    if (seen.size > 512) {
                        val it = seen.iterator()
                        if (it.hasNext()) { it.next(); it.remove() }
                    }
                }
            }
            true
        } catch (_: Throwable) { true }
    }

    private fun parseInbound(txt: String) {
        try {
            val o = JSONObject(txt)
            when (o.optString("t")) {
                "hello" -> { /* future handshake */ }
                "posUpdate" -> {
                    val id = o.optString("id")
                    val lat = o.optDouble("lat")
                    val lon = o.optDouble("lon")
                    if (id.isNotBlank()) {
                        onPeerPos?.invoke(id, GeoPoint(lat, lon))
                    }
                }
                "bye" -> { /* optional */ }
            }
        } catch (t: Throwable) {
            log("parse error: ${t.message}")
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        onLog?.invoke(msg)
    }

    companion object {
        @Volatile private var INSTANCE: GroupMesh? = null
        fun get(context: Context): GroupMesh {
            val app = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GroupMesh(app).also { INSTANCE = it }
            }
        }
    }
}
