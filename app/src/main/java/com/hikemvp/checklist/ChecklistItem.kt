package com.hikemvp.checklist

data class ChecklistItem(
    val id: String,      // clé de persistance
    val label: String,
    val isHeader: Boolean = false
)
