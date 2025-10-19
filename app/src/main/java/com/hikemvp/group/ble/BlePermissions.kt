package com.hikemvp.group.ble

import android.Manifest
import android.os.Build

object BlePermissions {
    val required: Array<String> by lazy {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION // needed for BLE scan visibility
        )
        if (Build.VERSION.SDK_INT >= 31) { // Android 12+
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_ADVERTISE
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        list.toTypedArray()
    }
}