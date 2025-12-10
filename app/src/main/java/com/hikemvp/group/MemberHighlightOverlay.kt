package com.hikemvp.group

import android.graphics.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Simple overlay qui dessine un anneau autour du membre sélectionné.
 */
class MemberHighlightOverlay(
    private val provider: () -> Pair<String?, Map<String, GeoPoint>?>
) : Overlay() {

    private val p = Point()
    private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.YELLOW
    }
    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = Color.argb(120, 255, 235, 59) // amber
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val (sel, members) = provider.invoke()
        val id = sel ?: return
        val map = members ?: return
        val gp = map[id] ?: return
        mapView.projection.toPixels(gp, p)
        val r = 28f * mapView.context.resources.displayMetrics.density
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, paintGlow)
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, paintRing)
    }
}
