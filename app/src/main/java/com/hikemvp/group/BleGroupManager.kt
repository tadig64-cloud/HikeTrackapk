package com.hikemvp.group

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * BLE scaffold for group mode.
 * This keeps the same public API used by GroupService and compiles on all API levels.
 * Real advertising/scanning code is TODO, but state callbacks are in place.
 */
class BleGroupManager(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onAdvertisingStateChanged(active: Boolean)
        fun onDiscoveringStateChanged(active: Boolean)
        fun onMembersChanged(members: List<String>)
        fun onMessage(msg: String?)
    }

    private var advertising = false
    private var discovering = false
    private val members = mutableListOf<String>()

    fun isAdvertising() = advertising
    fun isDiscovering() = discovering
    fun getMembers(): List<String> = members.toList()

    fun startHost(displayName: String?) {
        // TODO: implement BLE advertising + GATT/server or BLE broadcast
        // For now, just mark as active and notify
        advertising = true
        listener.onAdvertisingStateChanged(true)
        // Host typically starts with itself as member list owner
        updateMembersSimulated(listOfNotNull(displayName ?: "Moi"))
        listener.onMessage("Hôte prêt (BLE en attente)")
    }

    fun startJoin(displayName: String?) {
        // TODO: implement BLE scan + connect / or BLE broadcast listening
        discovering = true
        listener.onDiscoveringStateChanged(true)
        listener.onMessage("Recherche d'un hôte (BLE)…")
        // Simulate discovering one host entry to prove the refresh pipeline
        updateMembersSimulated(listOf("Guide", displayName ?: "Moi"))
    }

    fun stopAll() {
        advertising = false
        discovering = false
        listener.onAdvertisingStateChanged(false)
        listener.onDiscoveringStateChanged(false)
        members.clear()
        listener.onMembersChanged(members)
        listener.onMessage("Arrêt du groupe")
    }

    fun ping() {
        // TODO: send a small BLE payload (e.g., last GPS)
        listener.onMessage("Ping envoyé")
        // Keep the members list stable in the scaffold
        if (members.isEmpty()) {
            updateMembersSimulated(listOf("Moi"))
        } else {
            listener.onMembersChanged(members.toList())
        }
    }

    fun updateMembersSimulated(list: List<String>) {
        members.clear()
        members.addAll(list.distinct())
        listener.onMembersChanged(members.toList())
    }

    @SuppressLint("MissingPermission")
    fun isBleAvailable(): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter: BluetoothAdapter? = bm.adapter
        return adapter?.isEnabled == true
    }
}