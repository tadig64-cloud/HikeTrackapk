package com.hikemvp.elev

/** Optional interface for future DEM sampling; not used unless explicitly wired. */
interface ElevationSampler {
    /** Returns elevation in meters or null if unavailable. */
    fun sample(lat: Double, lon: Double): Double?
}
