package com.hikemvp

import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import com.hikemvp.group.GroupOverlayApi

/**
 * Conteneur central : on NE SUPPRIME RIEN dans ton projet, on ajoute de quoi
 * résoudre les références globales (mapView/overlay) et helpers utilisés dans
 * MapActivity / GroupActivity / GroupLiveSim.
 */
object GroupGlobals {
    @Volatile var mapView: MapView? = null
    @Volatile var overlay: GroupOverlayApi? = null
}

// Props top‑level utilisables dans le package de base
var mapView: MapView?
    get() = GroupGlobals.mapView
    set(v) { GroupGlobals.mapView = v }

var overlay: GroupOverlayApi?
    get() = GroupGlobals.overlay
    set(v) { GroupGlobals.overlay = v }

fun focusSmoothOn(gp: GeoPoint) {
    val mv = GroupGlobals.mapView ?: return
    runCatching { mv.controller.animateTo(gp) }.onFailure {
        runCatching { mv.controller.setCenter(gp) }
    }
}

fun zoomToAll() {
    val ov = GroupGlobals.overlay ?: return
    runCatching { ov.zoomToAll() }
}
