package com.hikemvp.ui

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hikemvp.R

/**
 * Câblage "safe" des vues principales de MapActivity.
 *
 * Objectif : éviter les erreurs de compilation dues à des IDs inexistants.
 * Ici on utilise UNIQUEMENT des IDs qui existent dans ton projet :
 * - toolbar (activity_map.xml)
 * - tvCoordsAlt (activity_map.xml / hud_box.xml)
 * - tvWeather  (activity_map.xml / hud_box.xml)
 * - hud_box    (activity_map.xml)
 * - bottomTabs (include_bottom_tabs.xml) -> optionnel
 *
 * Utilisation (les 2 styles sont supportés) :
 *   val ui = MapUiWiring.wire(this)
 *   // ou
 *   val ui = MapUiWiring(this).ui()
 *
 * Et tu peux aussi garder ton import legacy :
 *   import com.hikemvp.ui.wire
 *   val ui = wire(this)
 */
class MapUiWiring(private val activity: Activity) {

    data class MapUi(
        val toolbar: MaterialToolbar,
        val tvCoordsAlt: TextView,
        val tvWeather: TextView,
        val hudBox: View?,
        val bottomNav: BottomNavigationView?
    )

    fun ui(): MapUi = wire(activity)

    fun toolbar(): MaterialToolbar = ui().toolbar
    fun hudCoords(): TextView = ui().tvCoordsAlt
    fun hudWeather(): TextView = ui().tvWeather
    fun hudBox(): View? = ui().hudBox
    fun bottomNav(): BottomNavigationView? = ui().bottomNav

    companion object {
        @JvmStatic
        fun wire(activity: Activity): MapUi {
            val toolbar: MaterialToolbar = activity.findViewById(R.id.toolbar)
            val tvCoordsAlt: TextView = activity.findViewById(R.id.tvCoordsAlt)
            val tvWeather: TextView = activity.findViewById(R.id.tvWeather)

            val hudBox: View? = runCatching { activity.findViewById<View>(R.id.hud_box) }.getOrNull()

            // Optionnel (souvent absent de activity_map.xml) : bottom nav incluse par layout
            val bottomNav: BottomNavigationView? = runCatching {
                activity.findViewById<BottomNavigationView>(R.id.bottomTabs)
            }.getOrNull()

            return MapUi(
                toolbar = toolbar,
                tvCoordsAlt = tvCoordsAlt,
                tvWeather = tvWeather,
                hudBox = hudBox,
                bottomNav = bottomNav
            )
        }
    }
}

/**
 * Compat : MapActivity avait encore `import com.hikemvp.ui.wire`
 */
fun wire(activity: Activity): MapUiWiring.MapUi = MapUiWiring.wire(activity)
