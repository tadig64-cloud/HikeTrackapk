package com.hikemvp.profile

import android.content.Context
import android.content.Intent

object ProfileNav {
    fun open(context: Context) {
        context.startActivity(Intent(context, ProfileActivity::class.java))
    }
}
