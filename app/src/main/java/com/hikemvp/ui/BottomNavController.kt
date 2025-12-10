package com.hikemvp.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.tabs.TabLayout
import com.hikemvp.R

/**
 * Barre d'onglets "carte / groupe / traces / waypoints / plus"
 * - Créée entièrement en code pour éviter les erreurs de ressources et d'inclusion XML.
 * - Aucune dépendance à un ID de vue existant : on injecte un conteneur en bas de l'écran.
 * - Idempotent : si déjà ajoutée, on ne la duplique pas.
 */
class BottomNavController(
    private val activity: Activity,
    private val root: ViewGroup
) {
    private val hostId = View.generateViewId()

    fun attach(currentScreen: Screen = Screen.MAP) {
        // Si déjà présent, ne pas recréer
        val existing = root.findViewById<View>(hostId)
        if (existing != null) {
            // Met à jour la sélection seulement
            val tabLayout = existing.findViewWithTag<TabLayout>("tabs")
            tabLayout?.let { selectTabForScreen(it, currentScreen) }
            return
        }

        // Hôte en bas
        val host = FrameLayout(activity).apply {
            id = hostId
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                gravity = Gravity.BOTTOM
            }
            // Laissez la carte visible : le host est par-dessus mais ne masque qu'une faible hauteur
            if (this@BottomNavController.root is FrameLayout) {
                // ok
            }
        }

        val tabs = TabLayout(activity).apply {
            tag = "tabs"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            // Style simple, sans M3 custom
            setSelectedTabIndicatorColor(Color.WHITE)
            setTabTextColors(0xAAFFFFFF.toInt(), Color.WHITE)
            setBackgroundColor(0xCC000000.toInt()) // noir semi-transparent
            elevation = 8f
        }

        fun add(labelRes: Int, iconRes: Int): TabLayout.Tab {
            val t = tabs.newTab()
            t.setText(labelRes)
            t.setIcon(iconRes)
            tabs.addTab(t)
            return t
        }

        val tMap = add(R.string.tab_map, R.drawable.ic_tab_map)
        val tGroup = add(R.string.tab_group, R.drawable.ic_tab_group)
        val tTracks = add(R.string.tab_tracks, R.drawable.ic_tab_tracks)
        val tWpts = add(R.string.tab_waypoints, R.drawable.ic_tab_waypoints)
        val tMore = add(R.string.tab_more, R.drawable.ic_tab_more)

        selectTabForScreen(tabs, currentScreen)

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab) {
                    tMap -> {
                        // On est déjà sur MapActivity : no-op
                        // Si un jour on place les tabs dans d'autres écrans, on pourra revenir à MapActivity ici.
                    }
                    tGroup -> {
                        safeStart("com.hikemvp.group.GroupActivity")
                    }
                    tTracks -> {
                        safeStart("com.hikemvp.tracks.TracesActivity")
                    }
                    tWpts -> {
                        safeStart("com.hikemvp.waypoints.WaypointsActivity")
                    }
                    tMore -> {
                        safeStart("com.hikemvp.more.MoreActivity")
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // pas d'action spéciale
            }
        })

        host.addView(tabs)
        // Ajoute le host tout en bas du root
        if (root is FrameLayout) {
            root.addView(host)
        } else {
            // si racine pas FrameLayout, on l'emballe dans un FrameLayout overlay
            val overlay = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(host)
            }
            root.addView(overlay)
        }
    }

    private fun selectTabForScreen(tabs: TabLayout, current: Screen) {
        val index = when (current) {
            Screen.MAP -> 0
            Screen.GROUP -> 1
            Screen.TRACKS -> 2
            Screen.WAYPOINTS -> 3
            Screen.MORE -> 4
        }
        if (tabs.selectedTabPosition != index && index in 0 until tabs.tabCount) {
            tabs.getTabAt(index)?.select()
        }
    }

    private fun safeStart(className: String) {
        try {
            val clazz = Class.forName(className)
            val i = Intent(activity, clazz)
            if (Build.VERSION.SDK_INT >= 34) {
                activity.startActivity(i)
            } else {
                activity.startActivity(i)
            }
        } catch (_: Throwable) {
            // écran manquant → on ignore proprement
        }
    }

    enum class Screen { MAP, GROUP, TRACKS, WAYPOINTS, MORE }
}