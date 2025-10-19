package com.hikemvp.group

/**
 * Petit helper de recherche – add-only.
 * Permet de trouver un membre par id/nom (contains, insensible à la casse) et de le sélectionner/centrer.
 */
object GroupSearch {

    /** Retourne l'id du premier membre dont id ou name contient la requête (case-insensitive). */
    fun findIdLike(query: String?): String? {
        val ov = GroupBridge.overlay as? GroupOverlay ?: return null
        if (query.isNullOrBlank()) return null
        val q = query.trim().lowercase()
        // on itère sur membersData via réflexion (pour ne pas exposer la structure interne publiquement)
        return try {
            val f = GroupOverlay::class.java.getDeclaredField("membersData")
            f.isAccessible = true
            val map = f.get(ov) as? Map<*, *>
            val entries = map?.entries ?: return null
            entries.firstOrNull { e ->
                val m = e.value
                val id = m?.javaClass?.getField("id")?.get(m)?.toString() ?: ""
                val name = m?.javaClass?.getField("name")?.get(m)?.toString() ?: ""
                id.lowercase().contains(q) || name.lowercase().contains(q)
            }?.key?.toString()
        } catch (_: Throwable) { null }
    }

    /** Sélectionne et centre sur le premier match (retourne l'id sélectionné ou null). */
    fun selectAndFocus(query: String?): String? {
        val id = findIdLike(query) ?: return null
        GroupActions.select(id)
        return id
    }
}
