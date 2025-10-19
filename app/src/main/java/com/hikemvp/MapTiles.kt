package com.hikemvp

import android.content.Context
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

object MapTiles {

    data class Options(
        val zoomMin: Int,
        val zoomMax: Int
    )

    /**
     * Télécharge les tuiles pour une bbox + plage de zoom.
     *
     * onProgress: (pourcentage, zoomCourant)
     */
    fun downloadAsync(
        context: Context,
        mapView: MapView,
        bbox: BoundingBox,
        options: Options,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onFinish: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val cm = CacheManager(mapView)

        cm.downloadAreaAsync(
            context,
            bbox,
            options.zoomMin,
            options.zoomMax,
            object : CacheManager.CacheManagerCallback {

                private var possibleTiles: Int = -1

                override fun downloadStarted() {
                    // no-op
                }

                // <- méthode requise par ta version d’osmdroid
                override fun setPossibleTilesInArea(p0: Int) {
                    possibleTiles = p0
                }

                // signature à 4 paramètres (progress en %, zoom courant, min, max)
                override fun updateProgress(
                    progress: Int,
                    currentZoomLevel: Int,
                    zoomMin: Int,
                    zoomMax: Int
                ) {
                    onProgress(progress.coerceIn(0, 100), currentZoomLevel)
                }

                override fun onTaskComplete() {
                    onFinish()
                }

                override fun onTaskFailed(errors: Int) {
                    onError()
                }
            }
        )
    }
}
