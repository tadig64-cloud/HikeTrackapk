package com.hikemvp.history

import android.content.Context
import com.hikemvp.profile.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exemple d'enregistrement d'une rando à la fin d'une session.
 * A appeler quand tu STOP ton enregistrement.
 */
object HistoryIntegration {

    fun saveAfterRecording(
        scope: CoroutineScope,
        context: Context,
        dateUtcMillis: Long,
        distanceMeters: Double,
        elevationUpMeters: Double,
        durationSeconds: Long,
        notes: String = ""
    ) {
        scope.launch(Dispatchers.IO) {
            val rec = HikeRecord(
                dateUtcMillis = dateUtcMillis,
                distanceMeters = distanceMeters,
                elevationUpMeters = elevationUpMeters,
                durationSeconds = durationSeconds,
                notes = notes
            )
            HikeStorage.add(context, rec)
        }
    }
}
