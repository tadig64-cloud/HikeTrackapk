package com.hikemvp.model

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val timeMillis: Long? = null
)

data class Track(
    val name: String? = null,
    val points: List<TrackPoint> = emptyList()
)