
package com.hikemvp

// Utilitaires de création SANS nom 'Stats' pour éviter toute ambiguïté.
fun statsOf(distanceM: Double): Stats =
    Stats(distanceM, 0.0, 0.0)

fun statsOf(distanceM: Double, gainM: Int, lossM: Int): Stats =
    Stats(distanceM, gainM.toDouble(), lossM.toDouble())

fun statsOf(distanceM: Double, gainM: Int, lossM: Int, altMin: Int, altMax: Int): Stats =
    Stats(distanceM, gainM.toDouble(), lossM.toDouble(), altMin.toDouble(), altMax.toDouble())

fun statsOf(
    distanceM: Double,
    gainM: Double,
    lossM: Double,
    altMin: Double? = null,
    altMax: Double? = null,
    durationMsTotal: Long? = null,
    durationMsMoving: Long? = null,
    avgSpeedKmh: Double? = null,
    movingAvgSpeedKmh: Double? = null,
    pointsCount: Int = 0
): Stats = Stats(distanceM, gainM, lossM, altMin, altMax, durationMsTotal, durationMsMoving, avgSpeedKmh, movingAvgSpeedKmh, pointsCount)
