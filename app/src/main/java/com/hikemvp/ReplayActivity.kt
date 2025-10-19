package com.hikemvp

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class ReplayActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var seek: SeekBar
    private lateinit var btnPlay: ImageButton
    private lateinit var btnSpeed: TextView
    private lateinit var hud: TextView

    private var cursor: Marker? = null
    private var polyline: Polyline? = null
    private var progressLine: Polyline? = null

    private val points = mutableListOf<GeoPoint>()

    private var isPlaying = false
    private var index = 0
    private val speeds = doubleArrayOf(0.5, 1.0, 2.0, 4.0)
    private var speedIdx = 1

    private var tickHandler: android.os.Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_replay)
        map = findViewById(R.id.replay_map)
        seek = findViewById(R.id.seek)
        btnPlay = findViewById(R.id.btn_play)
        btnSpeed = findViewById(R.id.btn_speed)
        hud = findViewById(R.id.hud)

        setupMap()
        loadPointsOrFinish()
        setupUi()
        updateUiForIndex()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        if (isPlaying) startTicker()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        stopTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTicker()
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(6.0)
        map.controller.setCenter(GeoPoint(46.5, 2.5))
    }

    private fun loadPointsOrFinish() {
        val pts = TrackStore.snapshot()
        if (pts.size < 2) {
            Toast.makeText(this, "Aucun tracé à rejouer.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        points.clear()
        points.addAll(pts)

        polyline = Polyline(map).apply {
            outlinePaint.strokeWidth = 6f
            outlinePaint.color = Color.LTGRAY
            setPoints(points) // pas d'accès à la propriété dépréciée
        }
        map.overlays.add(polyline)

        progressLine = Polyline(map).apply {
            outlinePaint.strokeWidth = 6f
            outlinePaint.color = Color.CYAN
            setPoints(emptyList())
        }
        map.overlays.add(progressLine)

        cursor = Marker(map).apply {
            icon = ContextCompat.getDrawable(this@ReplayActivity, R.drawable.ic_gps_fixed)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Position de relecture"
        }
        map.overlays.add(cursor)

        map.controller.setZoom(15.0)
        map.controller.animateTo(points.first())

        seek.max = points.lastIndex
        index = 0
    }

    private fun setupUi() {
        btnPlay.setOnClickListener { if (isPlaying) pause() else play() }
        btnSpeed.setOnClickListener { speedIdx = (speedIdx + 1) % speeds.size; updateSpeedLabel() }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) { index = value.coerceIn(0, points.lastIndex); updateUiForIndex() }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { pause() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        updateSpeedLabel()
        updatePlayIcon()
    }

    private fun play() { if (points.size >= 2) { isPlaying = true; updatePlayIcon(); startTicker() } }
    private fun pause() { isPlaying = false; updatePlayIcon(); stopTicker() }

    private fun startTicker() {
        stopTicker()
        val baseMs = 500L
        tickHandler = android.os.Handler(mainLooper).also { h ->
            h.post(object : Runnable {
                override fun run() {
                    if (!isPlaying) return
                    stepForward()
                    val delay = (baseMs / speeds[speedIdx]).toLong().coerceAtLeast(50L)
                    h.postDelayed(this, delay)
                }
            })
        }
    }

    private fun stopTicker() {
        tickHandler?.removeCallbacksAndMessages(null)
        tickHandler = null
    }

    private fun stepForward() {
        if (index >= points.lastIndex) { pause(); return }
        index++
        seek.progress = index
        updateUiForIndex()
    }

    private fun updateUiForIndex() {
        if (points.isEmpty()) return
        val p = points[index]
        cursor?.position = p
        if (index > 0) progressLine?.setPoints(points.subList(0, index + 1))
        else progressLine?.setPoints(emptyList())

        val lat = "%.5f".format(p.latitude)
        val lon = "%.5f".format(p.longitude)
        val alt = if (!p.altitude.isNaN()) "${p.altitude.toInt()} m" else "—"
        hud.text = "Relecture • ${index + 1}/${points.size}\nLat: $lat  Lon: $lon  Alt: $alt"

        map.controller.animateTo(p)
        map.invalidate()
    }

    private fun updateSpeedLabel() { btnSpeed.text = "${speeds[speedIdx]}×" }

    private fun updatePlayIcon() {
        btnPlay.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }
}
