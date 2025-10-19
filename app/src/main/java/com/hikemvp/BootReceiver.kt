package com.hikemvp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Si on enregistrait avant le reboot, relance le service
        if (RecordingPrefs_isActive(context)) {
            val svc = Intent(context, TrackRecordingService::class.java)
                .setAction(Constants.ACTION_START_RECORD)
            if (Build.VERSION.SDK_INT >= 26) {
                ContextCompat.startForegroundService(context, svc)
            } else {
                context.startService(svc)
            }
        }
    }

    // Copie locale pour accéder à RecordingPrefs (déclaré private dans TrackRecordingService.kt)
    private fun RecordingPrefs_isActive(ctx: Context): Boolean {
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            .getBoolean("rec_active", false)
    }
}
