package com.hikemvp.ui

import android.animation.TimeInterpolator
import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView

/**
 * Ajoute un pad de zoom vertical (+ / curseur / −) à gauche, centré verticalement.
 * Hauteur ≈ 1/3 écran, largeur compacte.
 *
 * Usage: ZoomPadBinder.attach(activity)
 */
object ZoomPadBinder {

    private const val TAG_ZOOM = "com.hikemvp.ui.ZOOM_PAD_TAG"

    fun attach(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // éviter doublon
        root.findViewWithTag<View>(TAG_ZOOM)?.let { return }

        val mapView = findMapView(root)
        if (mapView == null) {
            Toast.makeText(activity, "MapView introuvable", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(activity).apply {
            tag = TAG_ZOOM
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                dp(56, activity),
                (activity.resources.displayMetrics.heightPixels * 0.33f).toInt(),
                Gravity.CENTER_VERTICAL or Gravity.START
            ).also { lp ->
                if (lp is ViewGroup.MarginLayoutParams) {
                    lp.leftMargin = dp(6, activity)
                }
            }
            // fond doux
            setBackgroundColor(0x66FFFFFF)
            elevation = dp(6, activity).toFloat()
        }

        val btnPlus = Button(activity).apply {
            text = "+"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setOnClickListener {
                mapView.controller.zoomIn()
                syncFromMap(mapView, container)
            }
        }

        val seek = SeekBar(activity).apply {
            // On va faire un SeekBar horizontal et le mettre en vertical via rotation
            max = 1000
            rotation = -90f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                2f
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val (minZ, maxZ) = zoomBounds(mapView)
                    val z = minZ + (progress / 1000f) * (maxZ - minZ)
                    mapView.controller.setZoom(z.toDouble())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    syncFromMap(mapView, container)
                }
            })
        }

        val btnMinus = Button(activity).apply {
            text = "−"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setOnClickListener {
                mapView.controller.zoomOut()
                syncFromMap(mapView, container)
            }
        }

        container.addView(btnPlus)
        container.addView(seek)
        container.addView(btnMinus)

        // Ajouter au root (au-dessus de la carte)
        val parent = root as? FrameLayout ?: FrameLayout(activity).also { wrapper ->
            wrapper.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            while (root.childCount > 0) {
                val child = root.getChildAt(0)
                root.removeViewAt(0)
                wrapper.addView(child)
            }
            root.addView(wrapper)
        }
        parent.addView(container)

        // Synchroniser dès l’attache et écouter les events de la map
        syncFromMap(mapView, container)
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = false
            override fun onZoom(event: ZoomEvent?): Boolean {
                syncFromMap(mapView, container)
                return false
            }
        })
    }

    private fun syncFromMap(mapView: MapView, container: View) {
        val seek = (container as ViewGroup).findViewById<SeekBar>(android.R.id.progress)
            ?: container.findSeekBar()
        val (minZ, maxZ) = zoomBounds(mapView)
        val z = mapView.zoomLevelDouble.coerceIn(minZ.toDouble(), maxZ.toDouble()).toFloat()
        val p = ((z - minZ) / (maxZ - minZ) * 1000f).toInt().coerceIn(0, 1000)
        seek?.progress = p
    }

    private fun ViewGroup.findSeekBar(): SeekBar? {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c is SeekBar) return c
            if (c is ViewGroup) {
                val r = c.findSeekBar()
                if (r != null) return r
            }
        }
        return null
    }

    private fun zoomBounds(mapView: MapView): Pair<Float, Float> {
        val min = try { mapView.minZoomLevel.toFloat() } catch (_: Exception) { 2f }
        val max = try { mapView.maxZoomLevel.toFloat() } catch (_: Exception) { 20f }
        return if (max > min) min to max else 2f to 20f
    }

    private fun findMapView(root: ViewGroup): MapView? {
        fun dfs(v: View): MapView? {
            if (v is MapView) return v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val r = dfs(v.getChildAt(i))
                    if (r != null) return r
                }
            }
            return null
        }
        return dfs(root)
    }

    private fun dp(v: Int, activity: Activity): Int =
        (v * activity.resources.displayMetrics.density).toInt()
}