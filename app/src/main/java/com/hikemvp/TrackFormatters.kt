package com.hikemvp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
fun formatHour(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
fun formatPaceOrSpeed(distanceMeters: Double, movingTimeMs: Long?): String {
    if (movingTimeMs == null || movingTimeMs <= 0) return "—"
    val hours = movingTimeMs / 3600000.0
    val km = distanceMeters / 1000.0
    val speed = if (hours > 0) km / hours else 0.0
    val df = DecimalFormat("0.0")
    return "${df.format(speed)} km/h"
}
