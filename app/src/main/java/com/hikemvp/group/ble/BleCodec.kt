package com.hikemvp.group.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Encodes/decodes a compact location payload for BLE Service Data.
 *
 * Layout (little-endian, 16 bytes total):
 *  - int32  latE7     (latitude * 1e7)
 *  - int32  lonE7     (longitude * 1e7)
 *  - int16  altDm     (altitude in decimeters, range ~ -3276m..+3276m)
 *  - uint16 spdCms    (speed in cm/s, 0..655.35 m/s)
 *  - uint16 bearingCs (bearing in centi-degrees, 0..655.35°, wraps at 36000)
 *  - uint16 secs      (seconds since minute, for churn; not a clock)
 */
object BleCodec {
    const val PAYLOAD_SIZE = 16

    data class Point(
        val lat: Double,
        val lon: Double,
        val altitudeMeters: Double? = null,
        val speedMps: Float? = null,
        val bearingDeg: Float? = null,
        val seconds: Int = 0,
    )

    fun encode(p: Point): ByteArray {
        val buf = ByteBuffer.allocate(PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val latE7 = (p.lat * 1e7).roundToInt()
        val lonE7 = (p.lon * 1e7).roundToInt()
        val altDm = ((p.altitudeMeters ?: 0.0) * 10.0).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        val spdCms = ((p.speedMps ?: 0f) * 100f).toInt().coerceIn(0, 0xFFFF).toShort()
        val bearingCs = ((p.bearingDeg ?: 0f) * 100f).toInt().and(0xFFFF).toShort()
        val secs = (p.seconds % 60).coerceAtLeast(0).toShort()

        buf.putInt(latE7)
        buf.putInt(lonE7)
        buf.putShort(altDm)
        buf.putShort(spdCms)
        buf.putShort(bearingCs)
        buf.putShort(secs)
        return buf.array()
    }

    fun decode(bytes: ByteArray): Point? {
        if (bytes.size < PAYLOAD_SIZE) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val latE7 = buf.int
        val lonE7 = buf.int
        val altDm = buf.short.toInt()
        val spdCms = buf.short.toInt() and 0xFFFF
        val bearingCs = buf.short.toInt() and 0xFFFF
        val secs = buf.short.toInt() and 0xFFFF
        return Point(
            lat = latE7 / 1e7,
            lon = lonE7 / 1e7,
            altitudeMeters = altDm / 10.0,
            speedMps = spdCms / 100f,
            bearingDeg = (bearingCs % 36000) / 100f,
            seconds = secs % 60,
        )
    }
}