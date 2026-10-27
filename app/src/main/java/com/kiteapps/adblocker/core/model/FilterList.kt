package com.kiteapps.adblocker.core.model

data class FilterList(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val isBuiltIn: Boolean,
    val category: String, // "ads", "privacy", "security", "custom"
    val ruleCount: Int,
    val downloadUrl: String,
    val trieUrl: String = "",
    val bloomUrl: String = "",
    val lastUpdated: Long = 0L,
    val isPro: Boolean = false
)
