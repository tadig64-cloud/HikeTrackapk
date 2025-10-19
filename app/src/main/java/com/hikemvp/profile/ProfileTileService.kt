@file:Suppress("DEPRECATION")
package com.hikemvp.profile

import android.content.Intent
import android.service.quicksettings.TileService

class ProfileTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val i = Intent(this, ProfileActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        startActivityAndCollapse(i)
    }
}
