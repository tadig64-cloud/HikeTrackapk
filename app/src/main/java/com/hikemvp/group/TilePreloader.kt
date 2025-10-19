package com.hikemvp.group

import android.content.Context
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

/**
 * Préchargement de tuiles (hors-ligne) via osmdroid CacheManager.
 * Utilisation:
 *   TilePreloader.preload(context, mapView, bbox, 12, 17, onDone = { ok -> ... })
 */
object TilePreloader {
    fun preload(
        context: Context,
        mapView: MapView,
        bbox: BoundingBox,
        zoomMin: Int,
        zoomMax: Int,
        onProgress: ((Int, Int) -> Unit)? = null,
        onDone: ((Boolean) -> Unit)? = null
    ) {
        val cm = CacheManager(mapView)
        cm.downloadAreaAsync(context, bbox, zoomMin, zoomMax, object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() { onDone?.invoke(true) }
            override fun onTaskFailed(errors: Int) { onDone?.invoke(false) }
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                onProgress?.invoke(progress, currentZoomLevel)
            }
            override fun downloadStarted() {}
            override fun setPossibleTilesInArea(total: Int) {}
        })
    }
}
