package com.hikemvp

import android.app.Activity
import android.view.View

/**
 * Extension helpers to satisfy calls like toolbar(), hudCoords(), hudWeather(), bottomNav()
 * from within MapActivity without touching MapActivity.
 *
 * They try to find existing views by common ids. If not found, they return a dummy View
 * to keep the app from crashing while you finish wiring the UI.
 * (You can refine the id list below to match your XML IDs.)
 */
private fun Activity.findViewOrStub(vararg idNames: String): View {
    for (name in idNames) {
        val id = resources.getIdentifier(name, "id", packageName)
        if (id != 0) {
            val v = findViewById<View>(id)
            if (v != null) return v
        }
    }
    // Fallback: return an inert view so method calls compile/run even if the id isn't present yet.
    return View(this)
}

fun Activity.toolbar(): View =
    findViewOrStub("materialToolbar", "toolbar", "top_app_bar")

fun Activity.hudCoords(): View =
    findViewOrStub("hudCoords", "coords_panel", "tv_coords", "coordsText")

fun Activity.hudWeather(): View =
    findViewOrStub("hudWeather", "weather_container", "weatherPanel")

fun Activity.bottomNav(): View =
    findViewOrStub("bottomNav", "bottom_navigation", "bottom_app_bar", "bottomBar")
