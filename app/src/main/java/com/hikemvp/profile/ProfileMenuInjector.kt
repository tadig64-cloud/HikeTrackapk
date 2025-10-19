package com.hikemvp.profile

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.appbar.MaterialToolbar
import com.hikemvp.R

object ProfileMenuInjector {
    fun install(activity: Activity, toolbar: MaterialToolbar) {
        if (hasProfileItem(toolbar.menu)) return
        val item = toolbar.menu.add(Menu.NONE, R.id.menu_open_profile, Menu.NONE, activity.getString(R.string.profile_title))
        item.setIcon(R.drawable.ic_profile)
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        toolbar.setOnMenuItemClickListener { it: MenuItem ->
            if (it.itemId == R.id.menu_open_profile) {
                ProfileNav.open(activity); true
            } else false
        }
    }
    private fun hasProfileItem(menu: Menu): Boolean {
        for (i in 0 until menu.size()) if (menu.getItem(i).itemId == R.id.menu_open_profile) return true
        return false
    }
}
