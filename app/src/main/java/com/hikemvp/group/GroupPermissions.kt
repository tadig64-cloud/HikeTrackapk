package com.hikemvp.group

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object GroupPermissions {
    fun requestAll(activity: Activity, requestCode: Int = 200) {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_ADVERTISE
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION // BLE scan pre-12
        }
        // Localisation pour GPS
        perms += Manifest.permission.ACCESS_FINE_LOCATION
        ActivityCompat.requestPermissions(activity, perms.distinct().toTypedArray(), requestCode)
    }

    fun askDisableBatteryOptim(activity: Activity) {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 23) {
            if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
                val it = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:" + activity.packageName)
                }
                activity.startActivity(it)
            }
        }
    }
}
