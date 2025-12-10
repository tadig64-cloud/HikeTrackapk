package com.hikemvp.group

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Color
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Petit overlay indépendant pour afficher un label texte (id) près de chaque membre.
 * Avantages : facile à activer/désactiver, aucun couplage fort avec l'overlay existant.
 */
class MemberLabelsOverlay(
    private val provider: () -> Map<String, GeoPoint>?,
    private val color: Int = Color.BLACK,
    private val textSizeSp: Float = 12f
) : Overlay() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
        color = this@MemberLabelsOverlay.color
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val members = provider.invoke() ?: return
        val proj = mapView.projection
        val scale = mapView.context.resources.displayMetrics.scaledDensity
        paint.textSize = textSizeSp * scale
        val p = Point()
        for ((id, gp) in members) {
            proj.toPixels(gp, p)
            canvas.drawText(id, (p.x + 10).toFloat(), (p.y - 10).toFloat(), paint)
        }
    }
}
