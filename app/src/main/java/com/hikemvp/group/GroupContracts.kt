package com.hikemvp.group

/**
 * Centralises the broadcast actions, extras and role used by the Group feature.
 * Kept as top‑level constants so GroupActivity can reference them unqualified.
 */

enum class GroupRole {
    HOST,
    MEMBER
}

// Broadcast actions (used by GroupService <-> GroupActivity)
const val ACTION_STATE = "com.hikemvp.group.ACTION_STATE"
const val ACTION_START_HOST = "com.hikemvp.group.ACTION_START_HOST"
const val ACTION_START_JOIN = "com.hikemvp.group.ACTION_START_JOIN"
const val ACTION_PING = "com.hikemvp.group.ACTION_PING"

// Intent extras
const val EXTRA_ROLE = "com.hikemvp.group.EXTRA_ROLE"
const val EXTRA_ADVERTISING = "com.hikemvp.group.EXTRA_ADVERTISING"
const val EXTRA_DISCOVERING = "com.hikemvp.group.EXTRA_DISCOVERING"
const val EXTRA_MEMBERS = "com.hikemvp.group.EXTRA_MEMBERS"
const val EXTRA_MESSAGE = "com.hikemvp.group.EXTRA_MESSAGE"
const val EXTRA_DISPLAY_NAME = "com.hikemvp.group.EXTRA_DISPLAY_NAME"
