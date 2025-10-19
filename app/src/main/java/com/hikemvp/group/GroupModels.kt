package com.hikemvp.group

import org.osmdroid.util.GeoPoint

data class GroupMeta(
    val id: String,
    var name: String
)

data class GroupData(
    val id: String,
    var name: String,
    var members: MutableList<GroupMember> = mutableListOf()
)
