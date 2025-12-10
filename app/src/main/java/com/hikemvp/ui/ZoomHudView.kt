
package com.hikemvp.ui

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView

/**
 * Zoom HUD vertical, collé au bord gauche, centré verticalement.
 * - Hauteur ≈ 1/3 de l’écran
 * - Deux boutons (+ en haut, – en bas) et un slider vertical au milieu
 * - Synchronisé avec le zoom de la MapView
 *
 * Intégration côté Activity après setContentView(...):
 *   ZoomHudView.ensure(this)
 */
object ZoomHudView {

    private const val CONTAINER_TAG = "zoom_hud_container"
    private const val SEEKBAR_MAX = 1000
    private const val DEFAULT_MIN_ZOOM = 2.0
    private const val DEFAULT_MAX_ZOOM = 20.0

    fun ensure(activity: Activity, mapViewId: Int = getMapId()): Unit {
        val map = activity.findViewById<MapView>(mapViewId) ?: return
        // Désactiver les contrôles natifs pour éviter le doublon
        try {
            map.setBuiltInZoomControls(false)
        } catch (_: Throwable) { /* ok */ }

        val root = activity.findViewById<ViewGroup>(android.R.id.content) as FrameLayout

        // Si déjà en place, ne rien faire
        val existing = root.findViewWithTag<View>(CONTAINER_TAG)
        if (existing != null) return

        val ctx = activity

        // Conteneur vertical
        val containerHeightPx = (ctx.resources.displayMetrics.heightPixels * 0.33f).toInt()
        val containerWidthPx = dp(ctx, 56)

        val container = FrameLayout(ctx).apply {
            tag = CONTAINER_TAG
            layoutParams = FrameLayout.LayoutParams(containerWidthPx, containerHeightPx).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = dp(ctx, 8)
            }
            elevation = dp(ctx, 8).toFloat()
        }

        // Fond du conteneur
        container.background = roundedRectDrawable(
            color = 0x7F000000.toInt(), // noir semi-transparent
            radiusDp = 16f
        )

        val pad = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Bouton +
        val plusBtn = smallRoundButton(ctx, text = "+")
        // Slider vertical (SeekBar) – on le tourne
        val sb = SeekBar(ctx).apply {
            max = SEEKBAR_MAX
            rotation = -90f
            // on va donner une marge pour ne pas coller aux bords
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                val m = dp(ctx, 8)
                setMargins(m, m, m, m)
            }
        }
        // Bouton –
        val minusBtn = smallRoundButton(ctx, text = "–")

        // Ajouter sous-views
        pad.addView(plusBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(ctx, 8)
            }
        )
        pad.addView(sb)
        pad.addView(minusBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(ctx, 8)
            }
        )

        container.addView(pad)
        root.addView(container)

        // Mapping zoom <-> progress
        val minZ = DEFAULT_MIN_ZOOM
        val maxZ = DEFAULT_MAX_ZOOM
        fun zoomToProgress(z: Double): Int {
            val clamped = z.coerceIn(minZ, maxZ)
            val ratio = (clamped - minZ) / (maxZ - minZ)
            return (ratio * SEEKBAR_MAX).toInt().coerceIn(0, SEEKBAR_MAX)
        }
        fun progressToZoom(p: Int): Double {
            val ratio = (p.toDouble() / SEEKBAR_MAX.toDouble()).coerceIn(0.0, 1.0)
            return minZ + (maxZ - minZ) * ratio
        }

        // Init progress selon zoom actuel
        sb.progress = zoomToProgress(readZoom(map))

        // Listeners
        plusBtn.setOnClickListener {
            try {
                map.controller.zoomIn()
            } catch (_: Throwable) {
                val z = readZoom(map) + 1.0
                setZoom(map, z)
            }
            sb.progress = zoomToProgress(readZoom(map))
        }
        minusBtn.setOnClickListener {
            try {
                map.controller.zoomOut()
            } catch (_: Throwable) {
                val z = readZoom(map) - 1.0
                setZoom(map, z)
            }
            sb.progress = zoomToProgress(readZoom(map))
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val z = progressToZoom(progress)
                    setZoom(map, z)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Écoute les changements de zoom de la carte (pinch, etc.)
        try {
            map.addMapListener(object : MapListener {
                override fun onZoom(event: ZoomEvent?): Boolean {
                    event ?: return false
                    sb.progress = zoomToProgress(readZoom(map))
                    return false
                }
                override fun onScroll(event: ScrollEvent?): Boolean = false
            })
        } catch (_: Throwable) { /* ok */ }
    }

    // ===== Utils

    private fun smallRoundButton(ctx: Context, text: String): Button {
        return Button(ctx).apply {
            this.text = text
            textSize = 18f
            isAllCaps = false
            val pad = dp(ctx, 6)
            setPadding(pad, pad, pad, pad)
            minWidth = dp(ctx, 40)
            minHeight = dp(ctx, 40)
            background = roundedRectDrawable(
                color = 0xFFFFFFFF.toInt(),
                radiusDp = 20f,
                strokeColor = 0x33000000,
                strokeWidthDp = 1f
            )
        }
    }

    private fun roundedRectDrawable(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
        strokeWidthDp: Float = 0f,
        ctx: Context? = null
    ): GradientDrawable {
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.cornerRadius = (ctx?.resources?.displayMetrics?.density ?: 1f) * radiusDp
        gd.setColor(color)
        if (strokeColor != null && strokeWidthDp > 0f) {
            val px = ((ctx?.resources?.displayMetrics?.density ?: 1f) * strokeWidthDp).toInt()
            gd.setStroke(px, strokeColor)
        }
        return gd
    }

    private fun dp(ctx: Context, dp: Int): Int {
        val d = ctx.resources.displayMetrics.density
        return (dp * d).toInt()
    }

    private fun readZoom(map: MapView): Double {
        return try {
            map.zoomLevelDouble
        } catch (_: Throwable) {
            try { map.zoomLevel.toDouble() } catch (_: Throwable) { 10.0 }
        }
    }

    private fun setZoom(map: MapView, zoom: Double) {
        try {
            map.controller.setZoom(zoom)
        } catch (_: Throwable) {
            map.controller.setZoom(zoom.toInt())
        }
    }

    // Si ton id de MapView n'est pas 'mapView', ajuste ici.
    private fun getMapId(): Int = getIdentifierSafe("mapView")

    private fun getIdentifierSafe(name: String): Int {
        return try {
            val r = Class.forName("com.hikemvp.R\$id")
            val field = r.getDeclaredField(name)
            field.getInt(null)
        } catch (_: Throwable) {
            android.R.id.empty // valeur fallback, mais ensure() retournera directement si la MapView est introuvable
        }
    }
}
