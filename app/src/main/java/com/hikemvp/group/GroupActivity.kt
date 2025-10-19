package com.hikemvp.group

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hikemvp.R
import com.hikemvp.group.ui.GroupAdapter
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import com.hikemvp.group.GroupAdminSheet
import android.provider.Settings
import android.content.Intent

class GroupActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var adapter: GroupAdapter
    private var overlay: GroupOverlayApi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)
        findViewById<Button>(R.id.btnGroupAdmin).setOnClickListener {
            GroupAdminSheet().show(supportFragmentManager, "group_admin")
        }
        intent?.getStringExtra("extra_join_code")?.let {
            GroupDeepLink.pendingCode = it
            intent?.removeExtra("extra_join_code")
        }

        // Map
        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // osmdroid storage config (simple)
        Configuration.getInstance().userAgentValue = packageName
        try {
            val base = File(getExternalFilesDir(null), "osmdroid")
            val tiles = File(base, "tiles")
            if (!tiles.exists()) tiles.mkdirs()
            Configuration.getInstance().osmdroidBasePath = base
            Configuration.getInstance().osmdroidTileCache = tiles
        } catch (_: Throwable) {}

        // Overlay
        val currentOverlay = GroupBridge.overlay ?: SimpleGroupOverlay()
        overlay = currentOverlay
        currentOverlay.attachTo(mapView)
        GroupBridge.attach(mapView, currentOverlay)
        GroupActions.autoCenterOnTap(true, 17.0)

        // Recycler
        val rv = findViewById<RecyclerView>(R.id.listMembers)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = GroupAdapter(mutableListOf()) { m ->
            overlay?.let { ov ->
                ov.focusSmoothOn(m.id, mapView, 17.0)
                (ov as? GroupOverlay)?.setSelected(m.id)
            }
        }
        rv.adapter = adapter

        // Seed demo data if empty
        if (overlay?.members?.isEmpty() != false) {
            val center = GeoPoint(43.6045, 1.4440) // Toulouse
            val list = GroupSeeder.seedAround(center, 10)
            overlay?.setMembers(list, mapView)
            mapView.controller.setZoom(14.0)
            mapView.controller.setCenter(center)
        } else {
            GroupActions.zoomAll()
        }

        // Buttons
        findViewById<Button>(R.id.btnZoomAll).setOnClickListener { GroupActions.zoomAll() }
        findViewById<Button>(R.id.btnLabels).setOnClickListener {
            val on = GroupActions.toggleLabels()
            (it as Button).text = if (on) "Labels ON" else "Labels"
        }
        findViewById<Button>(R.id.btnCluster).setOnClickListener {
            val on = GroupActions.toggleClustering()
            (it as Button).text = if (on) "Cluster ON" else "Cluster"
        }
        findViewById<Button>(R.id.btnSim).setOnClickListener {
            if (!GroupLiveSim.isRunning()) {
                GroupActions.startSim(1200, 6.0, GroupActions.selectedId())
                (it as Button).text = "Sim OFF"
            } else {
                GroupActions.stopSim()
                (it as Button).text = "Sim ON"
            }
        }

        // Search
        val search = findViewById<EditText>(R.id.searchInput)
        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val q = search.text?.toString()
            val id = GroupSearch.selectAndFocus(q)
        }

        refreshList()
    }

    private fun refreshList() {
        val ov = overlay ?: return
        val list = ov.members.map { (id, gp) ->
            val m = try {
                val f = ov.javaClass.getDeclaredField("membersData"); f.isAccessible = true
                val map = f.get(ov) as? Map<*,*>
                val mm = map?.get(id)
                if (mm is GroupMember) mm else GroupMember(id, id, gp)
            } catch (_: Throwable) { GroupMember(id, id, gp) }
            m
        }
        adapter.setData(list)
    }

    override fun onStart() {
        super.onStart()
        overlay?.attachTo(mapView)
        GroupBridge.attach(mapView, overlay)

        // Applique un éventuel code deeplink une fois la carte prête
        window.decorView.post {
            GroupDeepLink.handleIfPresent(this, GroupBridge.mapView)
        }

        // Appui long = précharger les tuiles de la zone visible (dialog avec progression)
        findViewById<Button>(R.id.btnLabels).setOnLongClickListener {
            TilePreloaderDialog.show(this)  // this = FragmentActivity
            true
        }
    }

    override fun onStop() {
        super.onStop()
        overlay?.let {
            it.detachFrom(mapView)
        }
    }

    override fun onResume() {
        super.onResume()
        // Si un code d'invitation a été reçu pendant que l'activité était en arrière-plan,
        // traite-le ici (anime la carte et lance la jonction de groupe).
        GroupDeepLink.handleIfPresent(this, mapView)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Gère un deep link reçu pendant que l'activité est visible
        GroupDeepLink.handleIfPresent(this, mapView)
    }
}
