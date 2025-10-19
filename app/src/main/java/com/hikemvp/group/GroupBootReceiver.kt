
package com.hikemvp.group

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GroupBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("group_nearby", Context.MODE_PRIVATE)
        val role = prefs.getString("role", null) ?: return
        val myId = prefs.getString("myId", null) ?: return
        val myName = prefs.getString("myName", null) ?: return
        val pin = prefs.getString("pin", "0000") ?: "0000"
        if (role == "host") {
            com.hikemvp.group.GroupNearbyService.startHost(context, myId, myName, pin)
        } else if (role == "member") {
            com.hikemvp.group.GroupNearbyService.startMember(context, myId, myName, pin)
        }
    }
}
