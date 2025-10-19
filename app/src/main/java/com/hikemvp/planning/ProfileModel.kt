
package com.hikemvp.planning

data class ProfilePoint(
    val distanceM: Double,
    val elevationM: Double? = null,
    val slopePct: Double? = null,
    val timeMs: Long? = null
)

data class ProfileSeries(
    val points: List<ProfilePoint>
)
