package com.hikemvp.history

data class TrackInfo(
    val name: String,
    val fileName: String,
    val points: Int,
    val distanceMeters: Double,
    val elevationGain: Double,
    val elevationLoss: Double,
    val startTimeMillis: Long?,
    val endTimeMillis: Long?,
    val hasElevation: Boolean
) {
    val durationMillis: Long?
        get() = if (startTimeMillis != null && endTimeMillis != null && endTimeMillis >= startTimeMillis) {
            endTimeMillis - startTimeMillis
        } else null
}
