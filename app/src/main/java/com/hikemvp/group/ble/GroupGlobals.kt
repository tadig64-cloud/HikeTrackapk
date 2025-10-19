package com.hikemvp.group

import org.osmdroid.util.GeoPoint

/**
 * État partagé minimal pour le groupe : position et "last seen".
 * Un seul endroit = plus de conflits.
 */
object GroupGlobals {
    @Volatile var lastFix: GeoPoint? = null
    @Volatile var lastSeenMillis: Long = 0L
}

/** Accès raccourci si ton code utilise latitude/longitude “globaux”. */
val latitude: Double
    get() = GroupGlobals.lastFix?.latitude ?: 0.0

val longitude: Double
    get() = GroupGlobals.lastFix?.longitude ?: 0.0
