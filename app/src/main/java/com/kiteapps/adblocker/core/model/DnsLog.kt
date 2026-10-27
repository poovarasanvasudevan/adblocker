package com.kiteapps.adblocker.core.model

data class DnsLog(
    val id: Long = 0,
    val domain: String,
    val isBlocked: Boolean,
    val queryType: String = "A",
    val latencyMs: Long = 0,
    val appName: String = "",
    val packageName: String = "",
    val resolvedIp: String = "",
    val blockedBy: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
