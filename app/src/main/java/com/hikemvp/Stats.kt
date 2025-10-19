
package com.hikemvp

data class Stats(
    val distanceM: Double,
    val gainM: Double,
    val lossM: Double,
    val altMin: Double? = null,
    val altMax: Double? = null,
    val durationMsTotal: Long? = null,
    val durationMsMoving: Long? = null,
    val avgSpeedKmh: Double? = null,
    val movingAvgSpeedKmh: Double? = null,
    val pointsCount: Int = 0
)
