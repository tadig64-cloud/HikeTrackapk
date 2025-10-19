
package com.hikemvp.planning

fun buildProfileSeries(distancesM: DoubleArray, elevationsM: DoubleArray? = null): ProfileSeries {
    val n = distancesM.size
    val pts = ArrayList<ProfilePoint>(n)
    for (i in 0 until n) {
        val ele = elevationsM?.getOrNull(i)
        pts.add(ProfilePoint(distanceM = distancesM[i], elevationM = ele))
    }
    return ProfileSeries(pts)
}
