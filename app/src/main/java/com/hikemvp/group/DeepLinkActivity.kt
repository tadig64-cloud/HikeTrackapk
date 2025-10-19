package com.hikemvp.group

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Gère les liens d'invitation de groupe.
 * Formats acceptés :
 *  - https://hiketrack.app/join?code=ABC123
 *  - https://hiketrack.app/join/ABC123   (dernier segment)
 *
 * Le code est stocké dans GroupDeepLink.pendingCode, lu ensuite par GroupActivity.
 * Aucun changement d'UI ici.
 */
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Récupère l'URI du lien d'invitation
        val uri: Uri? = intent?.data

        // Extrait le code (priorité au paramètre ?code=, sinon dernier segment du chemin)
        val code: String? = uri?.let { u: Uri ->
            u.getQueryParameter("code") ?: u.lastPathSegment
        }

        if (!code.isNullOrBlank()) {
            // Stocke le code pour que GroupActivity puisse l'utiliser
            GroupDeepLink.pendingCode = code
            Toast.makeText(this, "Code détecté : " + code, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Lien d'invitation invalide", Toast.LENGTH_SHORT).show()
        }

        // Enchaîne sur l'écran groupe sans modifier l'UI existante
        val intent = Intent(this, GroupActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        startActivity(intent)
        finish()
    }
}