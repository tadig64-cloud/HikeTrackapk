package com.hikemvp.group

import org.osmdroid.views.MapView

/**
 * Actions UI – version unifiée (compat + nouvelles méthodes).
 * Add-only. Restaure follow(id) pour compat avec l'Adapter.
 */
object GroupActions {

    fun zoomAll() { GroupBridge.overlay?.zoomToAll() }

    fun toggleLabels(): Boolean {
        val ov = GroupBridge.overlay as? GroupOverlay ?: return false
        return try {
            val f = GroupOverlay::class.java.getDeclaredField("labelsEnabled"); f.isAccessible = true
            val current = (f.get(ov) as? Boolean) ?: false
            GroupOverlay::class.java.getDeclaredMethod("setLabelsEnabled", java.lang.Boolean.TYPE).invoke(ov, !current)
            GroupBridge.mapView?.invalidate()
            !current
        } catch (_: Throwable) { false }
    }

    fun toggleClustering(): Boolean {
        val ov = GroupBridge.overlay ?: return false
        val mv: MapView = GroupBridge.mapView ?: return false
        return try {
            val f = ov.javaClass.getDeclaredField("clustering"); f.isAccessible = true
            val current = (f.get(ov) as? Boolean) ?: false
            ov.setClustering(!current, mv)
            !current
        } catch (_: Throwable) { false }
    }

    fun select(id: String?): Boolean {
        val ov = GroupBridge.overlay as? GroupOverlay ?: return false
        return try {
            GroupOverlay::class.java.getDeclaredMethod("setSelected", String::class.java).invoke(ov, id)
            if (id != null) {
                val mv = GroupBridge.mapView ?: return true
                ov.focusSmoothOn(id, mv, 17.0)
            }
            true
        } catch (_: Throwable) { false }
    }

    fun selectedId(): String? {
        val ov = GroupBridge.overlay as? GroupOverlay ?: return null
        return try {
            GroupOverlay::class.java.getDeclaredMethod("getSelected").invoke(ov) as? String
        } catch (_: Throwable) { null }
    }

    fun autoCenterOnTap(enabled: Boolean, zoomLevel: Double? = 17.0) {
        GroupBridge.overlay?.setAutoCenterOnTap(enabled, zoomLevel)
    }

    /** Compat: remit pour GroupAdapter (follow bouton) */
    fun follow(id: String?) {
        GroupLiveSim.setFollow(id)
    }

    /** Follow la sélection; retourne l'id suivi (ou null si arrêté). */
    fun followSelected(toggle: Boolean = true): String? {
        val id = selectedId() ?: return null
        if (toggle && GroupLiveSim.isRunning()) {
            // toggle simple: si on suivait déjà cette id, on coupe
            GroupLiveSim.setFollow(null)
            return null
        }
        GroupLiveSim.setFollow(id)
        return id
    }

    fun startSim(periodMs: Long = 1500L, amplitudeMeters: Double = 6.0, followId: String? = null) {
        GroupLiveSim.start(periodMs, amplitudeMeters, followId)
    }
    fun stopSim() { GroupLiveSim.stop() }
}
