package com.hikemvp.group

import org.osmdroid.util.GeoPoint

data class GroupMember(
    val id: String,
    var name: String,
    var point: GeoPoint,
    var color: Int = 0xFF888888.toInt(),
    var isHost: Boolean = false
)
