package com.hikemvp.tiles

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * Pour l’instant : si la clé est absente, on retourne MAPNIK afin d’éviter tout crash.
 * Quand tu mettras une vraie clé IGN, on branchera l’URL WMTS ici.
 */
object IgnWmtsTileSource {
    var IGN_API_KEY: String? = null

    fun get(): ITileSource {
        if (IGN_API_KEY.isNullOrBlank() || IGN_API_KEY == "YOUR_IGN_KEY") {
            return TileSourceFactory.MAPNIK
        }
        // TODO: implémenter la vraie source IGN WMTS avec la clé.
        return TileSourceFactory.MAPNIK
    }
}
