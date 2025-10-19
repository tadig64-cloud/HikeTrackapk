
package com.hikemvp.planning

typealias Stats = com.hikemvp.Stats
typealias TrackStats = com.hikemvp.Stats

// Créateurs compatibles au besoin, avec NOMS différents du constructeur pour éviter les conflits.
fun statsFromMeters(distanceMeters: Double, gain: Double, loss: Double): Stats =
    com.hikemvp.Stats(distanceM = distanceMeters, gainM = gain, lossM = loss)

fun statsFromInts(distanceMeters: Double, gain: Int, loss: Int, altMin: Int, altMax: Int): Stats =
    com.hikemvp.Stats(
        distanceM = distanceMeters,
        gainM = gain.toDouble(),
        lossM = loss.toDouble(),
        altMin = altMin.toDouble(),
        altMax = altMax.toDouble()
    )
