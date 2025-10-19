package com.hikemvp

object Constants {
    // ---- Foreground Service / Notification ----
    const val CHANNEL_ID_RECORDING = "rec_channel"
    const val NOTIF_ID_RECORDING = 2001

    // ---- Actions service ----
    const val ACTION_START_RECORD  = "com.hikemvp.action.START_RECORD"
    const val ACTION_STOP_RECORD   = "com.hikemvp.action.STOP_RECORD"
    const val ACTION_PAUSE_RECORD  = "com.hikemvp.action.PAUSE_RECORD"
    const val ACTION_RESUME_RECORD = "com.hikemvp.action.RESUME_RECORD"

    // ---- Statut & diffusion vers l’UI ----
    const val ACTION_RECORDING_STATUS = "com.hikemvp.action.RECORDING_STATUS"
    const val BROADCAST_TRACK_POINT   = "com.hikemvp.action.TRACK_POINT"
    const val BROADCAST_TRACK_RESET   = "com.hikemvp.action.TRACK_RESET"

    const val BROADCAST_GPX_SAVED = "com.hikemvp.action.GPX_SAVED"
    const val EXTRA_GPX_PATH = "extra_gpx_path"


    // ---- Extras communs ----
    const val EXTRA_LAT = "extra_lat"
    const val EXTRA_LON = "extra_lon"
    const val EXTRA_ALT = "extra_alt"
    const val EXTRA_ACTIVE    = "extra_active"
    const val EXTRA_IS_PAUSED = "extra_is_paused"

    // ---- GPS adaptatif : valeurs par défaut (utiles !) ----
    const val TIER_ACTIVE_MIN_TIME_MS = 1000L
    const val TIER_ACTIVE_MIN_DIST_M  = 3f

    const val TIER_SLOW_MIN_TIME_MS   = 5000L
    const val TIER_SLOW_MIN_DIST_M    = 10f

    const val TIER_IDLE_MIN_TIME_MS   = 30000L
    const val TIER_IDLE_MIN_DIST_M    = 25f

    const val SPEED_ACTIVE_MS         = 1.0           // >1 m/s => actif
    const val IDLE_WINDOW_MS          = 30_000L       // 30s sans bouger => idle

    // Prefs
    const val PREF_BATTERY_SAVER = "pref_key_battery_saver"
}
