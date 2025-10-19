package com.hikemvp.checklist.data

data class CheckItem(
    val id: String,
    val label: String,
    var checked: Boolean = false
)
