package com.hikemvp.offline

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hikemvp.R

/**
 * Activity minimale compatible avec un layout 'offline_list'.
 * Utilise getIdentifier pour éviter les erreurs si l'id/le layout est renommé.
 */
class OfflineMapsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layoutId = resources.getIdentifier("offline_list", "layout", packageName)
        if (layoutId != 0) {
            setContentView(layoutId)
        } else {
            // Fallback vers R.layout.offline_list si présent
            setContentView(R.layout.offline_list)
        }
    }
}
