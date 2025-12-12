package com.hikemvp.group

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Comparator
import java.util.HashMap
import java.util.LinkedHashMap

class GroupActivity : AppCompatActivity() {

    // --- Views ---
    private lateinit var mapView: MapView
    private lateinit var listView: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnGroupAdmin: Button
    private lateinit var btnZoomAll: Button
    private lateinit var btnLabels: Button
    private lateinit var btnCluster: Button
    private lateinit var btnSim: Button
    private lateinit var btnP2P: Button

    // --- State / helpers ---
    private var overlay: GroupOverlayApi? = null
    private var wifiBridge: GroupWifiBridge? = null
    private var wifiP2p: GroupWifiP2pHelper? = null

    private val REQ_P2P_PERMS = 10042
    private val REQ_LOC_PERMS = 10043
    private var pendingCreateGroupAtMyLoc = false

    // prefs
    private val prefs by lazy { getSharedPreferences("group_prefs", MODE_PRIVATE) }

    // toggles
    private var labelsEnabled = true
    private var clusterEnabled = false
    private var simEnabled = false
    private var autoZoomTap = true
    private var dotSizeDp = 24

    // overlays additionnels
    private var labelOverlay: MemberLabelsOverlay? = null
    private var gridCluster: GridClusterOverlay? = null
    private var highlight: MemberHighlightOverlay? = null
    private var selectedIdLocal: String? = null
    private var selfCenteredOnce: Boolean = false
    private var selfMarker: Marker? = null

    // mesh + sim
    private var mesh: GroupMesh? = null
    private var simHandler: Handler? = null
    private var simRunnable: Runnable? = null

    // live position receiver
    private var posReceiver: BroadcastReceiver? = null

    // --- Model for member list ---
    private data class UiMember(
        val id: String,
        val point: GeoPoint,
        val color: Int
    )

    private val items: MutableList<UiMember> = mutableListOf()
    private lateinit var adapter: UiMemberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)

        // Views
        mapView = findViewById(R.id.map)
        listView = findViewById(R.id.listMembers)
        searchInput = findViewById(R.id.searchInput)
        btnSearch = findViewById(R.id.btnSearch)
        btnGroupAdmin = findViewById(R.id.btnGroupAdmin)
        btnZoomAll = findViewById(R.id.btnZoomAll)
        btnLabels = findViewById(R.id.btnLabels)
        btnCluster = findViewById(R.id.btnCluster)
        btnSim = findViewById(R.id.btnSim)
        btnP2P = findViewById(R.id.btnP2P)

        // Z-order: garde la liste devant la carte
        try {
            listView.setHasFixedSize(false)
            listView.elevation = 12f
            listView.translationZ = 12f
            (listView.parent as? ViewGroup)?.bringChildToFront(listView)
            listView.bringToFront()
        } catch (_: Throwable) {
        }

        // Map de base + cache osmdroid
        Configuration.getInstance().userAgentValue = packageName
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        try {
            val base = File(getExternalFilesDir(null), "osmdroid")
            val tiles = File(base, "tiles")
            if (!tiles.exists()) tiles.mkdirs()
            Configuration.getInstance().osmdroidBasePath = base
            Configuration.getInstance().osmdroidTileCache = tiles
        } catch (_: Throwable) {
        }

        // Overlay (reuse ou défaut)
        val currentOverlay = GroupBridge.overlay ?: SimpleGroupOverlay()
        overlay = currentOverlay
        currentOverlay.attachTo(mapView)
        GroupBridge.attach(mapView, currentOverlay)
        GroupActions.autoCenterOnTap(true, 17.0)

        // Persistance: reload si présent, sinon seed
        val persisted = GroupStore.load(this)
        if (persisted.isNotEmpty()) {
            overlay?.setMembers(persisted, true)
            try {
                GroupActions.zoomAll()
            } catch (_: Throwable) {
            }
        } else if (overlay?.members?.isEmpty() != false) {
            val center = GeoPoint(43.6045, 1.4440) // Toulouse
            val list = GroupSeeder.seedAround(center, 10)
            overlay?.setMembers(list, mapView)
            mapView.controller.setZoom(14.0)
            mapView.controller.setCenter(center)
        } else {
            try {
                GroupActions.zoomAll()
            } catch (_: Throwable) {
            }
        }

        // Liste membres
        listView.layoutManager = LinearLayoutManager(this)
        adapter = UiMemberAdapter(
            data = items,
            myIdProvider = { myMemberId() },
            onClick = { member ->
                if (autoZoomTap) focusOn(member.point)
                // si l'impl concrète supporte setSelected
                (overlay as? GroupOverlay)?.setSelected(member.id)
                selectedIdLocal = member.id
                ensureHighlightOverlay()
            },
            onLongClick = { member ->
                showColorPicker(member.id)
            }
        )
        listView.adapter = adapter
        refreshMembersFromOverlay()
        loadPrefs()

        // Boutons
        btnGroupAdmin.setOnClickListener {
            GroupAdminSheet().show(supportFragmentManager, "group_admin")
        }
        btnGroupAdmin.setOnLongClickListener {
            startActivity(Intent(this, GroupSettingsActivity::class.java)); true
        }
        btnZoomAll.setOnClickListener {
            try {
                GroupActions.zoomAll()
            } catch (_: Throwable) {
            }
        }
        btnLabels.setOnClickListener {
            labelsEnabled = !labelsEnabled
            loadPrefsTextsOnly()
            ensureLabelsOverlay()
        }
        btnCluster.setOnClickListener {
            clusterEnabled = !clusterEnabled
            loadPrefsTextsOnly()
            setClusterIfSupported(clusterEnabled)
        }
        btnSim.setOnClickListener {
            simEnabled = !simEnabled
            loadPrefsTextsOnly()
            Toast.makeText(
                this,
                if (simEnabled) "Simulation activée" else "Simulation arrêtée",
                Toast.LENGTH_SHORT
            ).show()
            if (simEnabled) startSimTicker() else stopSimTicker()
            bringMembersListToFront()
        }
        btnP2P.setOnClickListener {
            launchPeerDiscovery()
        }
        btnP2P.setOnLongClickListener {
            showP2PMenu()
            true
        }
        btnSearch.setOnClickListener {
            val q = searchInput.text?.toString()?.trim().orEmpty()
            if (q.isEmpty()) {
                Toast.makeText(this, "Saisis un id/nom", Toast.LENGTH_SHORT).show()
            } else {
                searchAndFocus(q)
            }
        }

        // Bridges
        wifiBridge = GroupWifiBridge(this) { onWifi ->
            val msg = if (onWifi) "Wi-Fi groupe: ON" else "Wi-Fi groupe: OFF"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        wifiP2p = GroupWifiP2pHelper(this)

        // Mesh
        try {
            mesh = GroupMesh.get(this)
            mesh?.onPeerPos = { id, gp ->
                runOnUiThread {
                    Log.d("GroupActivity", "onPeerPos: $id @ ${gp.latitude},${gp.longitude}")
                    updateOwnPosition(id, gp)
                    if (id == myMemberId()) {
                        ensureSelfMarkerAt(gp)
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // Assurer mon membre à la dernière position connue
        try {
            ensureSelfMemberFromLocation(centerIfNew = true)
        } catch (_: Throwable) {
        }

        // Extra deeplink (legacy)
        intent?.getStringExtra("extra_join_code")?.let {
            GroupDeepLink.pendingCode = it
            intent?.removeExtra("extra_join_code")
        }
    }

    override fun onStart() {
        super.onStart()
        wifiBridge?.start()
        registerPositionReceiver()
    }

    override fun onStop() {
        super.onStop()
        wifiBridge?.stop()
        mesh?.stop()
        posReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            GroupStore.save(this, overlay?.members)
        } catch (_: Throwable) {
        }
    }

    override fun onResume() {
        super.onResume()
        loadPrefs()
        ensureSelfMemberFromLocation(centerIfNew = true)
        try {
            GroupDeepLink.handleIfPresent { code ->
                GroupJoin.joinByLinkCode(this, code) { ok ->
                    Toast.makeText(
                        this,
                        if (ok) "Rejoint via lien: $code" else "Échec du lien: $code",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            GroupDeepLink.handleIfPresent { code ->
                GroupJoin.joinByLinkCode(this, code) { ok ->
                    Toast.makeText(
                        this,
                        if (ok) "Rejoint via lien: $code" else "Échec du lien: $code",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (_: Throwable) {
        }
    }

    // --- Helpers UI/données ---

    private fun myMemberId(): String {
        return try {
            // Aligné avec GroupNearbyService : il stocke son id dans "group_nearby"
            val svcPrefs = getSharedPreferences("group_nearby", Context.MODE_PRIVATE)
            val storedId = svcPrefs.getString("myId", null)
            if (!storedId.isNullOrBlank()) {
                storedId.trim()
            } else {
                val pseudo = GroupMemberName.localDisplayName(this, "")
                if (!pseudo.isNullOrBlank()) {
                    pseudo.trim()
                } else {
                    "Me-" + (android.os.Build.MODEL ?: "Android")
                }
            }
        } catch (_: Throwable) {
            "Me-" + (android.os.Build.MODEL ?: "Android")
        }
    }

    // Centre la mini-map sur ma position
    fun centerOnMe(minZoom: Double = 15.0) {
        val loc = lastKnownLocation() ?: run {
            Log.w("GroupActivity", "No last known location to center")
            return
        }
        val gp = GeoPoint(loc.latitude, loc.longitude)
        mapView.controller.animateTo(gp)
        val z = mapView.zoomLevelDouble
        if (z < minZoom) mapView.controller.setZoom(minZoom)
    }

    private fun lastKnownLocation(): Location? {
        // 1) Si MyLocationNewOverlay présent, on utilise son lastFix
        try {
            val myOverlay = mapView.overlays.firstOrNull {
                it is org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
            } as? org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
            val fix = myOverlay?.lastFix
            if (fix != null) return fix
        } catch (_: Throwable) {
        }

        // 2) Sinon on prend le meilleur lastKnownLocation Android
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = try {
            lm.getProviders(true)
        } catch (_: Throwable) {
            emptyList<String>()
        }
        var best: Location? = null
        for (p in providers) {
            val l = try {
                lm.getLastKnownLocation(p)
            } catch (_: SecurityException) {
                null
            } catch (_: Throwable) {
                null
            }
            if (l != null && (best == null || l.accuracy < best!!.accuracy)) best = l
        }
        return best
    }

    private fun ensureSelfMarkerAt(gp: GeoPoint) {
        try {
            if (selfMarker == null) {
                selfMarker = Marker(mapView).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Moi"
                }
                mapView.overlays.add(selfMarker)
            }
            selfMarker?.position = gp
            mapView.invalidate()
        } catch (_: Throwable) {
        }
    }

    private fun ensureSelfMemberFromLocation(centerIfNew: Boolean) {
        val loc = lastKnownLocation() ?: return
        val gp = GeoPoint(loc.latitude, loc.longitude)
        val meId = myMemberId()
        val already = overlay?.members?.containsKey(meId) == true
        updateOwnPosition(meId, gp)
        ensureSelfMarkerAt(gp)
        if (centerIfNew && !already && !selfCenteredOnce) {
            selfCenteredOnce = true
            try {
                mapView.controller.setZoom(16.0)
                mapView.controller.setCenter(gp)
            } catch (_: Throwable) {
            }
        }
    }

    private fun registerPositionReceiver() {
        if (posReceiver == null) {
            posReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    intent ?: return
                    if (intent.action != "com.hikemvp.group.POSITION") return
                    val id = intent.getStringExtra("id") ?: return
                    val lat = intent.getDoubleExtra("lat", Double.NaN)
                    val lon = intent.getDoubleExtra("lon", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) return
                    val gp = GeoPoint(lat, lon)
                    try {
                        updateOwnPosition(id, gp)
                        if (id == myMemberId()) {
                            ensureSelfMarkerAt(gp)
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        val filter = IntentFilter("com.hikemvp.group.POSITION")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(posReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(posReceiver, filter)
        }
    }

    private fun bringMembersListToFront() {
        try {
            listView.visibility = View.VISIBLE
            listView.alpha = 1f
            listView.elevation = 12f
            listView.translationZ = 12f
            (listView.parent as? ViewGroup)?.bringChildToFront(listView)
            listView.bringToFront()
            listView.requestLayout()
            listView.invalidate()
        } catch (_: Throwable) {
        }
    }

    private fun refreshMembersFromOverlay() {
        val map: Map<String, GeoPoint> = overlay?.members ?: emptyMap()
        val meId = myMemberId()
        val entries = map.entries.toMutableList()
        entries.sortWith(object : Comparator<Map.Entry<String, GeoPoint>> {
            override fun compare(a: Map.Entry<String, GeoPoint>, b: Map.Entry<String, GeoPoint>): Int {
                val ra = if (a.key == meId) 0 else 1
                val rb = if (b.key == meId) 0 else 1
                return if (ra != rb) ra - rb else a.key.compareTo(b.key)
            }
        })
        val newList = entries.map { e -> UiMember(e.key, e.value, colorFromId(e.key)) }
        items.clear()
        items.addAll(newList)
        adapter.dotSizePx =
            (dotSizeDp * resources.displayMetrics.density).toInt().coerceAtLeast(8)
        adapter.notifyDataSetChanged()
        bringMembersListToFront()
        if (labelOverlay != null || gridCluster != null || highlight != null) mapView.invalidate()
    }

    private fun loadPrefs() {
        labelsEnabled = prefs.getBoolean("labels_default", true)
        clusterEnabled = prefs.getBoolean("cluster_default", false)
        simEnabled = prefs.getBoolean("sim_default", false)
        autoZoomTap = prefs.getBoolean("auto_zoom_tap", true)
        dotSizeDp = prefs.getInt("dot_size_dp", 24)
        loadPrefsTextsOnly()
        ensureLabelsOverlay()
        setClusterIfSupported(clusterEnabled)
    }

    private fun loadPrefsTextsOnly() {
        btnLabels.text = if (labelsEnabled) "🏷️  Labels" else "🏷️  Labels OFF"
        btnCluster.text = if (clusterEnabled) "🧩  Cluster ON" else "🧩  Cluster"
        btnSim.text = if (simEnabled) "▶  Sim ON" else "▶  Sim OFF"
        btnZoomAll.text = "🔎  Zoom"
        btnP2P.text =
            if (btnP2P.text?.contains("✓") == true) "📶  P2P ✓" else "📶  P2P"
        btnGroupAdmin.text = "👤  Groupe"
        btnSearch.text = "🔍"
    }

    private fun ensureLabelsOverlay() {
        if (!labelsEnabled) {
            removeLabelsOverlay()
            return
        }
        if (labelOverlay == null) {
            labelOverlay = MemberLabelsOverlay(provider = { overlay?.members })
            mapView.overlays.add(labelOverlay)
        }
        mapView.invalidate()
    }

    private fun removeLabelsOverlay() {
        labelOverlay?.let { ol -> mapView.overlays.remove(ol) }
        labelOverlay = null
        mapView.invalidate()
    }

    private fun ensureHighlightOverlay() {
        if (highlight == null) {
            highlight = MemberHighlightOverlay { Pair(selectedIdLocal, overlay?.members) }
            mapView.overlays.add(highlight)
        }
        mapView.invalidate()
    }

    private fun removeHighlightOverlay() {
        highlight?.let { mapView.overlays.remove(it) }
        highlight = null
        mapView.invalidate()
    }

    private fun ensureGridClusterOverlay() {
        if (gridCluster == null) {
            gridCluster = GridClusterOverlay(provider = { overlay?.members })
            mapView.overlays.add(gridCluster)
        }
        mapView.invalidate()
    }

    private fun removeGridClusterOverlay() {
        gridCluster?.let { mapView.overlays.remove(it) }
        gridCluster = null
        mapView.invalidate()
    }

    private fun setClusterIfSupported(enabled: Boolean) {
        val o = overlay
        var handled = false
        if (o != null) {
            try {
                val m = o.javaClass.methods.firstOrNull {
                    it.name == "setClusterEnabled" && it.parameterTypes.size == 1
                }
                if (m != null) {
                    m.invoke(o, enabled)
                    handled = true
                }
            } catch (_: Throwable) {
            }
        }
        if (!handled) {
            if (enabled) ensureGridClusterOverlay() else removeGridClusterOverlay()
        } else {
            mapView.invalidate()
        }
    }

    private fun searchAndFocus(query: String) {
        val hit = items.firstOrNull { it.id.contains(query, ignoreCase = true) }
            ?: items.firstOrNull { it.id.startsWith(query, ignoreCase = true) }
        if (hit != null) {
            focusOn(hit.point)
            listView.smoothScrollToPosition(items.indexOf(hit))
        } else {
            Toast.makeText(this, "Membre introuvable: $query", Toast.LENGTH_SHORT).show()
        }
    }

    private fun focusOn(p: GeoPoint) {
        try {
            mapView.controller.setZoom(17.0)
            mapView.controller.animateTo(p)
        } catch (_: Throwable) {
        }
    }

    private fun colorFromId(id: String): Int {
        val customMap = GroupColors.all(this)
        val custom = customMap[id]
        if (custom != null) return custom
        val hue = (id.hashCode() and 0x7fffffff) % 360
        val s = 0.72f
        val v = 0.95f
        val hsv = floatArrayOf(hue.toFloat(), s, v)
        return Color.HSVToColor(hsv)
    }

    private fun updateOwnPosition(id: String, gp: GeoPoint) {
        // 1) Overlay map update
        val cur = overlay?.members
        if (cur is MutableMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (cur as MutableMap<String, GeoPoint>)[id] = gp
            overlay?.setMembers(cur, true)
        } else {
            val newMap = HashMap<String, GeoPoint>(cur ?: emptyMap<String, GeoPoint>())
            newMap[id] = gp
            overlay?.setMembers(newMap, true)
        }

        // 2) UI list update
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(point = gp)
            adapter.notifyItemChanged(idx)
        } else {
            val color = colorFromId(id)
            items.add(UiMember(id, gp, color))
            items.sortBy { it.id }
            adapter.notifyDataSetChanged()
        }
        bringMembersListToFront()
    }

    // --- P2P / Mesh / Wi-Fi Direct ---

    private fun showPeersDialog(list: WifiP2pDeviceList?) {
        val devices = list?.deviceList?.toList().orEmpty()
        if (devices.isEmpty()) {
            Toast.makeText(this, "Aucun pair Wi-Fi Direct trouvé", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = devices.map { d ->
            val name = d.deviceName ?: "(sans nom)"
            val addr = d.deviceAddress ?: "??:??:??:??:??:??"
            "$name  •  $addr"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Appareils à proximité")
            .setItems(labels) { _, which ->
                val chosen = devices[which]
                Toast.makeText(
                    this,
                    "Connexion à ${chosen.deviceName}…",
                    Toast.LENGTH_SHORT
                ).show()
                wifiP2p?.connectTo(chosen.deviceAddress ?: "") { ok, info: WifiP2pInfo? ->
                    if (ok) {
                        btnP2P.text = "📶  P2P ✓"
                        try {
                            val m = GroupMesh.get(this)
                            m.onPeerPos = { peerId, gp ->
                                runOnUiThread {
                                    val current: Map<String, GeoPoint> =
                                        overlay?.members ?: emptyMap()
                                    val newMap = HashMap<String, GeoPoint>(current)
                                    newMap[peerId] = gp
                                    overlay?.setMembers(newMap, true)
                                    updateOwnPosition(peerId, gp)
                                }
                            }
                            m.start(info)
                            mesh = m
                        } catch (_: Throwable) {
                        }
                        Toast.makeText(
                            this,
                            "Connecté à ${chosen.deviceName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this, "Échec de connexion", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun showHelpDialog() {
        val msg = "• Zoom : Ajuste la carte pour voir tous les membres.\n" +
                "• Labels : Affiche/Masque les libellés des membres.\n" +
                "• Cluster : Regroupe les membres proches.\n" +
                "• Sim : Active/désactive la simulation locale.\n" +
                "• P2P : Découvre les appareils Wi-Fi Direct (tap), menu long-press = Scanner / Déconnecter / État / Aide."
        AlertDialog.Builder(this)
            .setTitle("Aide — Module Groupe")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showP2PMenu() {
        val items = arrayOf("Scanner", "Déconnecter", "État", "Aide")
        AlertDialog.Builder(this)
            .setTitle("Wi-Fi Direct")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> launchPeerDiscovery()
                    1 -> wifiP2p?.disconnect {
                        btnP2P.text = "📶  P2P"
                        Toast.makeText(this, "Déconnecté", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        val onWifi = wifiBridge?.isOnWifi() == true
                        wifiP2p?.requestPeers { lst ->
                            val n = lst?.deviceList?.size ?: 0
                            AlertDialog.Builder(this)
                                .setTitle("État P2P")
                                .setMessage("Wi-Fi actif: $onWifi\nPairs visibles: $n")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                    3 -> showHelpDialog()
                }
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun ensureP2pPermissions(): Boolean {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        return if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_P2P_PERMS)
            false
        } else true
    }

    private fun ensureLocationPermission(): Boolean {
        val perm = Manifest.permission.ACCESS_FINE_LOCATION
        return if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_LOC_PERMS)
            false
        }
    }

    fun createGroupAtMyLocation() {
        // appelé depuis GroupAdminSheet
        pendingCreateGroupAtMyLoc = false
        if (!ensureLocationPermission()) {
            pendingCreateGroupAtMyLoc = true
            return
        }

        val best = lastKnownLocation()
        if (best == null) {
            Toast.makeText(this, "Position indisponible (active la localisation)", Toast.LENGTH_SHORT).show()
            return
        }

        val meId = myMemberId()
        val gp = GeoPoint(best.latitude, best.longitude)
        val roster = LinkedHashMap<String, GeoPoint>(1)
        roster[meId] = gp
        overlay?.setMembers(roster, true)
        mapView.controller.setZoom(17.0)
        mapView.controller.setCenter(gp)
        selfCenteredOnce = true
        refreshMembersFromOverlay()
        ensureSelfMarkerAt(gp)
        try {
            GroupStore.save(this, roster)
        } catch (_: Throwable) {
        }
        Toast.makeText(this, "Groupe créé à ta position", Toast.LENGTH_SHORT).show()

        // Retour automatique sur la carte principale avec le groupe actif
        val intent = Intent(this, com.hikemvp.MapActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    fun onResetRoster() {
        overlay?.setMembers(emptyMap<String, GeoPoint>(), true)
        refreshMembersFromOverlay()
        selfMarker?.let { marker -> mapView.overlays.remove(marker) }
        selfMarker = null
        mapView.invalidate()
        Toast.makeText(this, "Liste réinitialisée", Toast.LENGTH_SHORT).show()
    }

    private fun launchPeerDiscovery() {
        if (!ensureP2pPermissions()) return
        try {
            wifiP2p?.start()
            wifiP2p?.discoverOnce { list ->
                showPeersDialog(list)
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Wi-Fi P2P indisponible: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_P2P_PERMS) {
            val granted =
                grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) launchPeerDiscovery()
            else Toast.makeText(this, "Permission requise pour la découverte P2P", Toast.LENGTH_SHORT).show()
        } else if (requestCode == REQ_LOC_PERMS) {
            val granted =
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted && pendingCreateGroupAtMyLoc) {
                createGroupAtMyLocation()
            } else if (!granted && pendingCreateGroupAtMyLoc) {
                Toast.makeText(this, "Permission localisation requise", Toast.LENGTH_SHORT).show()
            }
            pendingCreateGroupAtMyLoc = false
        }
    }

    // --- Picker couleur ---
    private fun showColorPicker(memberId: String) {
        val names = arrayOf(
            "Rouge", "Orange", "Jaune", "Vert",
            "Bleu", "Violet", "Rose", "Gris",
            "Supprimer couleur perso"
        )
        val values = intArrayOf(
            Color.parseColor("#E53935"),
            Color.parseColor("#FB8C00"),
            Color.parseColor("#FDD835"),
            Color.parseColor("#43A047"),
            Color.parseColor("#1E88E5"),
            Color.parseColor("#8E24AA"),
            Color.parseColor("#D81B60"),
            Color.parseColor("#757575"),
            -1 // remove custom
        )
        AlertDialog.Builder(this)
            .setTitle("Couleur pour $memberId")
            .setItems(names) { _, which ->
                val chosen = values[which]
                if (chosen == -1) {
                    GroupColors.remove(this, memberId)
                } else {
                    GroupColors.set(this, memberId, chosen)
                }
                refreshMembersFromOverlay()
                Toast.makeText(this, "Couleur mise à jour", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // --- Simulation ---
    private fun startSimTicker() {
        val meId = myMemberId()
        val base = overlay?.members?.values?.firstOrNull() ?: GeoPoint(43.6045, 1.4440)
        var angle = 0.0
        val h = Handler(mainLooper)
        val r = object : Runnable {
            override fun run() {
                if (!simEnabled) return
                angle += 8.0
                val rad = Math.toRadians(angle)
                val lat = base.latitude + 0.0015 * Math.sin(rad)
                val lon = base.longitude + 0.0015 * Math.cos(rad)
                val gp = GeoPoint(lat, lon)
                updateOwnPosition(meId, gp)
                mesh?.broadcastPosition(meId, gp)
                mapView.postInvalidateOnAnimation()
                h.postDelayed(this, 1200)
            }
        }
        simHandler = h
        simRunnable = r
        h.post(r)
    }

    private fun stopSimTicker() {
        simRunnable = null
        simHandler = null
    }

    // --- Adapter liste membres ---
    private class UiMemberAdapter(
        private val data: List<UiMember>,
        private val myIdProvider: () -> String,
        private val onClick: (UiMember) -> Unit,
        private val onLongClick: (UiMember) -> Unit
    ) : RecyclerView.Adapter<UiMemberAdapter.Holder>() {

        var dotSizePx: Int = 24

        class Holder(val root: View) : RecyclerView.ViewHolder(root) {
            val colorDot: View = (root as ViewGroup).getChildAt(0)
            val title: TextView = (root as ViewGroup).getChildAt(1) as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 12, 16, 12)
                gravity = Gravity.CENTER_VERTICAL
            }
            val colorView = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dotSizePx, dotSizePx).apply {
                    (this as LinearLayout.LayoutParams).marginEnd = 16
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.LTGRAY)
                }
            }
            val title = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                textSize = 16f
            }
            row.addView(colorView)
            row.addView(title)
            return Holder(row)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val m = data[position]
            val meId = myIdProvider()
            holder.title.text = if (m.id == meId) {
                m.id + " (moi)"
            } else {
                m.id
            }

            val lp = holder.colorDot.layoutParams as LinearLayout.LayoutParams
            lp.width = dotSizePx
            lp.height = dotSizePx
            holder.colorDot.layoutParams = lp

            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(m.color)
            }
            holder.colorDot.background = dot

            holder.itemView.setOnClickListener { onClick(m) }
            holder.itemView.setOnLongClickListener {
                onLongClick(m)
                true
            }
        }

        override fun getItemCount(): Int = data.size
    }
}
