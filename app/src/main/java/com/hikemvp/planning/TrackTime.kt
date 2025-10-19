package com.hikemvp.planning

// Règle simple type Naismith: 12 min/km + 10 min/100 m D+
fun estimateDurationMillis(stats: TrackStats): Long {
    val flatMin = (stats.distanceM / 1000.0) * 12.0
    val climbMin = (stats.gainM / 100.0) * 10.0
    return ((flatMin + climbMin) * 60_000).toLong()
}
