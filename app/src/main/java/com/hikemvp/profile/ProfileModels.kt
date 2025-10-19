package com.hikemvp.profile

enum class UnitSystem { METRIC, IMPERIAL }

data class Profile(
    val displayName: String = "",
    val photoPath: String = "",
    val theme: String = "system", // "light" | "dark" | "system"
    val unitSystem: UnitSystem = UnitSystem.METRIC
)
