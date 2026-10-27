package com.kiteapps.adblocker.core.model

enum class RuleType {
    BLOCK,
    ALLOW
}

data class CustomRule(
    val id: Long = 0,
    val domain: String,
    val type: RuleType,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
