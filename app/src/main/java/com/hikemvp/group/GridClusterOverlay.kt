package com.hikemvp.group

import android.graphics.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.floor

/**
 * Fallback de clustering par grille écran.
 * Il superpose des "bulles" avec le nombre d'éléments par cellule.
 */
class GridClusterOverlay(
    private val provider: () -> Map<String, GeoPoint>?,
    private val binPx: Int = 64
) : Overlay() {

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(170, 33, 150, 243) // bleu semi-transparent
    }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 14f // ajustée dynamiquement avec le scale dans draw()
    }
    private val rect = RectF()
    private val p = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val members = provider.invoke() ?: return
        if (members.isEmpty()) return
        val proj = mapView.projection

        // Ajuster la taille de texte selon la densité de l'écran courant
        val scale = mapView.context.resources.displayMetrics.scaledDensity
        paintText.textSize = 14f * scale

        // Binning écran
        val bins = HashMap<Long, Int>()
        for ((_, gp) in members) {
            proj.toPixels(gp, p)
            val bx = floor(p.x / binPx.toDouble()).toInt()
            val by = floor(p.y / binPx.toDouble()).toInt()
            val key = (bx.toLong() shl 32) or (by.toLong() and 0xffffffffL)
            bins[key] = (bins[key] ?: 0) + 1
        }

        // Dessin
        for ((key, count) in bins) {
            if (count <= 1) continue // on laisse l'overlay d'origine gérer les points solos
            val bx = (key shr 32).toInt()
            val by = (key and 0xffffffffL).toInt()
            val cx = bx * binPx + binPx / 2f
            val cy = by * binPx + binPx / 2f
            val radius = (binPx * 0.42f).coerceAtLeast(18f)

            rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawRoundRect(rect, radius, radius, paintBg)
            canvas.drawRoundRect(rect, radius, radius, paintStroke)
            canvas.drawText(count.toString(), cx, cy + (paintText.textSize * 0.35f), paintText)
        }
    }
}
