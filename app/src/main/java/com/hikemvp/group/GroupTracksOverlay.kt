package com.hikemvp.group

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Overlay qui dessine les TRACES des membres du groupe (une polyline par membre).
 * Les données viennent de GroupTrackStore.snapshot().
 */
class GroupTracksOverlay(
    private val context: Context
) : Overlay() {

    private val paint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val path = Path()
    private val reusePoint = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val snapshot = GroupTrackStore.snapshot()
        if (snapshot.isEmpty()) return

        // Couleurs personnalisées (même logique que GroupColors)
        val customColors = try {
            GroupColors.all(context)
        } catch (_: Throwable) {
            emptyMap<String, Int>()
        }

        for ((memberId, points) in snapshot) {
            if (points.size < 2) continue

            val color = customColors[memberId] ?: colorFromIdFallback(memberId)
            paint.color = color

            path.reset()
            var first = true
            for (p in points) {
                toPixels(mapView, p.geo, reusePoint)
                if (first) {
                    path.moveTo(reusePoint.x.toFloat(), reusePoint.y.toFloat())
                    first = false
                } else {
                    path.lineTo(reusePoint.x.toFloat(), reusePoint.y.toFloat())
                }
            }

            canvas.drawPath(path, paint)
        }
    }

    private fun toPixels(mapView: MapView, gp: GeoPoint, out: Point) {
        mapView.projection.toPixels(gp, out)
    }

    private fun colorFromIdFallback(id: String): Int {
        val hue = (id.hashCode() and 0x7fffffff) % 360
        val s = 0.72f
        val v = 0.95f
        val hsv = floatArrayOf(hue.toFloat(), s, v)
        return android.graphics.Color.HSVToColor(hsv)
    }
}
