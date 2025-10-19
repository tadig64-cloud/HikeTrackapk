package com.hikemvp.map

import org.osmdroid.util.GeoPoint

object MapCameraDefaults {
    // Centre de la France par défaut + zoom large
    @JvmField val DEFAULT_CENTER = GeoPoint(46.5, 2.5)
    @JvmField val DEFAULT_ZOOM = 6.0
}
