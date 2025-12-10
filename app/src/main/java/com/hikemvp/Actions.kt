package com.hikemvp

import android.app.Activity
import android.view.View

/**
 * Shim de compat (anti-régression) :
 * - Pas de fonctions top-level nav()/mbtiles() ici (pour éviter les "Conflicting overloads")
 * - On délègue vers les handlers officiels dans NavActions.kt
 */
object Actions {

    @JvmStatic
    fun openInfoHub(view: View) = nav(view)

    @JvmStatic
    fun openInfoHub(activity: Activity) = nav(activity)

    @JvmStatic
    fun openOfflineMaps(view: View) = mbtiles(view)

    @JvmStatic
    fun openOfflineMaps(activity: Activity) = mbtiles(activity)
}
