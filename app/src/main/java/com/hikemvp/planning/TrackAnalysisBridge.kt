
package com.hikemvp.planning

object TrackAnalysisBridge {
    fun makeStats(
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
    ): Stats = com.hikemvp.Stats(
        distanceM = distanceM,
        gainM = gainM,
        lossM = lossM,
        altMin = altMin,
        altMax = altMax,
        durationMsTotal = durationMsTotal,
        durationMsMoving = durationMsMoving,
        avgSpeedKmh = avgSpeedKmh,
        movingAvgSpeedKmh = movingAvgSpeedKmh,
        pointsCount = pointsCount
    )

    fun seriesFrom(distancesM: DoubleArray, elevationsM: DoubleArray? = null): ProfileSeries =
        buildProfileSeries(distancesM, elevationsM)
}
