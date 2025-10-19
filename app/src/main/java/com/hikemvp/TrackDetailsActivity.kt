package com.hikemvp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.hikemvp.databinding.ActivityTrackDetailsBinding
import org.osmdroid.util.GeoPoint
import kotlin.math.roundToInt

class TrackDetailsActivity : AppCompatActivity() {

    private lateinit var b: ActivityTrackDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTrackDetailsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val tb = b.root.findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(tb)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tb.setNavigationOnClickListener { finish() }
        tb.title = getString(R.string.track_detail_title)

        // thématisation cohérente avec la topbar “idle”
        val bg = ContextCompat.getColor(this, R.color.topbar_idle_bg)
        val fg = ContextCompat.getColor(this, R.color.topbar_idle_icon)
        tb.setBackgroundColor(bg)
        tb.setTitleTextColor(fg)
        tb.navigationIcon = ContextCompat.getDrawable(this, R.drawable.abc_ic_ab_back_material)?.apply {
            mutate().setTint(fg)
        }
        tb.overflowIcon?.mutate()?.setTint(fg)

        // données
        val pts: List<GeoPoint> = TrackStore.snapshot()
        val dist = distanceMeters(pts)
        val (altMin, altMax) = altRange(pts)

        b.tvDist.text = getString(R.string.ht_track_dist) + " " + formatDistance(dist)
        b.tvAltMin.text = getString(R.string.ht_track_alt_min) + " " +
                (altMin?.roundToInt()?.toString() ?: "—") + " m"
        b.tvAltMax.text = getString(R.string.ht_track_alt_max) + " " +
                (altMax?.roundToInt()?.toString() ?: "—") + " m"
        b.tvPoints.text = getString(R.string.ht_track_points) + " " + pts.size
    }

    private fun distanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var d = 0.0
        for (i in 1 until points.size) d += points[i - 1].distanceToAsDouble(points[i])
        return d
    }

    private fun formatDistance(m: Double): String {
        return if (m >= 1000.0) String.format("%.2f km", m / 1000.0) else "${m.roundToInt()} m"
    }

    private fun altRange(points: List<GeoPoint>): Pair<Double?, Double?> {
        var min: Double? = null
        var max: Double? = null
        for (p in points) {
            val a = p.altitude
            if (!a.isNaN()) {
                if (min == null || a < min!!) min = a
                if (max == null || a > max!!) max = a
            }
        }
        return min to max
    }
}
