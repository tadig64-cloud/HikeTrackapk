package com.hikemvp.map

import android.content.Context
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Persistance simple de la caméra Osmdroid (centre, zoom, orientation).
 * - save(ctx, mapView) à appeler en onPause/onStop
 * - restore(ctx, mapView, skipIfFocusExtra = false) après init de la carte
 */
object MapCameraPrefs {
    private const val FILE = "map_camera_prefs"
    private const val K_LAT = "lat"
    private const val K_LON = "lon"
    private const val K_ZOOM = "zoom"
    private const val K_ORI = "ori"

    @JvmStatic
    fun save(ctx: Context, map: MapView) {
        val c = map.mapCenter
        val z = map.zoomLevelDouble
        val ori = map.mapOrientation
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putFloat(K_LAT, c.latitude.toFloat())
            .putFloat(K_LON, c.longitude.toFloat())
            .putFloat(K_ZOOM, z.toFloat())
            .putFloat(K_ORI, ori)
            .apply()
    }

    /**
     * Restaure la caméra si des valeurs sont présentes.
     * - Appelle setCenter + setZoom + setMapOrientation à l'intérieur d'un post{} pour éviter
     *   les surcharges de layout initial.
     * - Retourne true si une restauration a été appliquée.
     */
    @JvmStatic
    fun restore(ctx: Context, map: MapView, skipIfFocusExtra: Boolean = false): Boolean {
        if (skipIfFocusExtra) return false
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!sp.contains(K_LAT) || !sp.contains(K_LON) || !sp.contains(K_ZOOM)) return false
        val lat = sp.getFloat(K_LAT, 0f).toDouble()
        val lon = sp.getFloat(K_LON, 0f).toDouble()
        val zoom = sp.getFloat(K_ZOOM, 0f).toDouble()
        val ori = sp.getFloat(K_ORI, 0f)
        map.post {
            map.controller.setCenter(GeoPoint(lat, lon))
            map.controller.setZoom(zoom)
            map.setMapOrientation(ori, true)
        }
        return true
    }
}
