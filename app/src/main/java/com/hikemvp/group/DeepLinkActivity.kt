package com.hikemvp.group

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Route les liens d'invitation vers GroupActivity.
 * Gère :
 *   - https://hiketrack.app/join?code=XXXX
 *   - hiketrack://join?code=XXXX
 */
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
        val code: String? = uri?.getQueryParameter("code")

        // Stocke un éventuel code pour traitement côté GroupActivity
        if (!code.isNullOrBlank()) {
            GroupDeepLink.pendingCode = code
        }
        Log.d("GroupDeepLink", "uri=" + intent?.data + " code=" + code)
        Log.d("GroupDeepLink", "forward GroupActivity code=" + code)

        // Relance l'écran groupe (aucun changement d'UI ici)
        startActivity(Intent(this, GroupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (!code.isNullOrBlank()) putExtra("extra_join_code", code)
        })
        finish()
    }
}
