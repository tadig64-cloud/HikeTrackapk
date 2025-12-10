package com.hikemvp.group

import android.location.Location
import android.os.Handler
import android.os.Looper

class GroupPositionTicker(
    private val isConnected: () -> Boolean,
    private val getLocation: () -> Location?,
    private val canSend: () -> Boolean = { true },
    private val onSend: (Location) -> Unit = { _ -> },
    private val intervalMs: Long = 5_000L,
    private val minDistanceM: Float = 5f
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastSent: Location? = null

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                if (isConnected() && canSend()) {
                    val loc = getLocation()
                    if (loc != null && shouldSend(loc)) {
                        onSend(loc)
                        send(loc)
                        lastSent = Location(loc)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                if (running) handler.postDelayed(this, intervalMs)
            }
        }
    }

    private fun shouldSend(loc: Location): Boolean {
        val last = lastSent ?: return true
        return last.distanceTo(loc) >= minDistanceM
    }

    fun start() {
        if (running) return
        running = true
        handler.post(loop)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    fun triggerOnce() {
        try {
            if (isConnected() && canSend()) {
                val loc = getLocation()
                if (loc != null && shouldSend(loc)) {
                    onSend(loc)
                    send(loc)
                    lastSent = Location(loc)
                }
            }
        } catch (_: Throwable) { }
    }

    private fun send(@Suppress("UNUSED_PARAMETER") loc: Location) { /* passthrough */ }
}
