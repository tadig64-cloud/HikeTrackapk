package com.hikemvp.profile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activité de "raccourci" affichable dans le lanceur.
 * Elle redirige immédiatement vers ProfileActivity puis se ferme.
 * Ajout non destructif : aucune modification des écrans existants.
 */
class ProfileEntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, ProfileActivity::class.java))
        finish()
    }
}
