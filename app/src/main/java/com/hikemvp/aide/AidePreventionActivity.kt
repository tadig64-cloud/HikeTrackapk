package com.hikemvp.aide

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.NestedScrollView
import com.google.android.material.tabs.TabLayout
import com.hikemvp.R

class AidePreventionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aide_prevention)

        // Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        // Si tu utilises AppCompat Toolbar en layout, on peut soit faire un "up" système, soit un simple finish()
        // Ici on garde simple : bouton retour qui ferme l'écran
        toolbar.setNavigationOnClickListener { finish() }

        // Titres issus de tes ressources (si définis)
        try {
            title = getString(R.string.aide_prevention_title)
        } catch (_: Throwable) {
            // Si la string n'existe pas, on ne casse rien
        }

        val tabs: TabLayout = findViewById(R.id.tabLayout)
        val contentText: TextView = findViewById(R.id.contentText)
        val scrollView: NestedScrollView = findViewById(R.id.scroll)

        // Si aucun onglet défini en XML, on crée les 3 par défaut
        if (tabs.tabCount == 0) {
            tabs.addTab(tabs.newTab().setText("Avant de partir"))
            tabs.addTab(tabs.newTab().setText("En rando"))
            tabs.addTab(tabs.newTab().setText("En galère"))
        }

        // IMPORTANT : on NE réécrit PAS le texte initial ici.
        // Ton layout met déjà un long texte via @string/aide_security_text pour l'onglet 1.
        // On laisse donc ce contenu tel quel au lancement.

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val index = tab.position.coerceIn(0, 2)
                setSection(index, contentText, scrollView)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                val index = tab.position.coerceIn(0, 2)
                setSection(index, contentText, scrollView)
            }
        })
    }

    private fun setSection(index: Int, contentText: TextView, scrollView: NestedScrollView) {
        // Cherche des strings ressources existantes ; sinon fallback court
        fun strOrNull(name: String): String? {
            val id = resources.getIdentifier(name, "string", packageName)
            return if (id != 0) getString(id) else null
        }

        val fallbackBefore = """
            • Itinéraire & météo à jour
            • Prévenir un proche (zone + heure de retour)
            • Cartes hors‑ligne, batterie + secours
            • Matériel de base : eau, encas, coupe‑vent, trousse, frontale
        """.trimIndent()

        val fallbackDuring = """
            • Rythme & hydratation régulière
            • Orientation : vérifier souvent sa position/waypoints
            • Météo qui tourne : demi‑tour si besoin
            • Respect des zones protégées (chiens souvent interdits)
        """.trimIndent()

        val fallbackTrouble = """
            • Perdu ? Stop, abri, observation, revenir au dernier point sûr
            • Blessure : protéger, isoler, alerter si nécessaire
            • Signaux : sifflet 6 coups/min (détresse), réponse 3
            • Appel : 112 (gratuit), 114 par SMS (sourds/malentendants)
        """.trimIndent()

        val txtAvant = strOrNull("aide_security_text")
            ?: strOrNull("aide_avant_partir")
            ?: fallbackBefore

        val txtRando = strOrNull("aide_hike_text")
            ?: strOrNull("aide_en_rando")
            ?: fallbackDuring

        val txtGalere = strOrNull("aide_trouble_text")
            ?: strOrNull("aide_en_galere")
            ?: fallbackTrouble

        val newText = when (index) {
            0 -> txtAvant
            1 -> txtRando
            else -> txtGalere
        }

        contentText.text = newText
        scrollView.scrollTo(0, 0)
    }
}
