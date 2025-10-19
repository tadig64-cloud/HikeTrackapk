package com.hikemvp.group

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Conserve le fichier pour éviter toute suppression, mais on encapsule
 * dans un objet pour éliminer les *Conflicting declarations*.
 * Les alias top-level restent disponibles via GroupGlobalsAliases.kt.
 */
object GroupAliasesLegacy {
    var mapView: MapView?
        get() = com.hikemvp.GroupGlobals.mapView
        set(v) { com.hikemvp.GroupGlobals.mapView = v }

    var overlay: GroupOverlayApi?
        get() = com.hikemvp.GroupGlobals.overlay
        set(v) { com.hikemvp.GroupGlobals.overlay = v }

    fun focusSmoothOn(gp: GeoPoint) = com.hikemvp.focusSmoothOn(gp)
    fun zoomToAll() = com.hikemvp.zoomToAll()
}
