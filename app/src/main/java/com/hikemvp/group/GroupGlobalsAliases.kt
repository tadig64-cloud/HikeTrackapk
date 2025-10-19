package com.hikemvp.group

import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint

// Garde ces alias top-level (compat) — utilisés par du vieux code éventuel.
var mapView: MapView?
    get() = com.hikemvp.GroupGlobals.mapView
    set(v) { com.hikemvp.GroupGlobals.mapView = v }

var overlay: GroupOverlayApi?
    get() = com.hikemvp.GroupGlobals.overlay
    set(v) { com.hikemvp.GroupGlobals.overlay = v }

fun focusSmoothOn(gp: GeoPoint) = com.hikemvp.focusSmoothOn(gp)
fun zoomToAll() = com.hikemvp.zoomToAll()
