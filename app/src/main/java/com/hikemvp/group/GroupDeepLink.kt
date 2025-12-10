package com.hikemvp.group

object GroupDeepLink {
    @Volatile var pendingCode: String? = null

    /**
     * If a join code is pending (set by DeepLinkActivity),
     * hand it to the caller through [consumer] and clear it.
     * @return true if a code was consumed, false otherwise.
     */
    fun handleIfPresent(consumer: (String) -> Unit): Boolean {
        val code = pendingCode ?: return false
        consumer(code)
        pendingCode = null
        return true
    }
}
