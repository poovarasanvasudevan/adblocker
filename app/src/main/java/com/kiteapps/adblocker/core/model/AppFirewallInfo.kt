package com.kiteapps.adblocker.core.model

import android.graphics.drawable.Drawable

data class AppFirewallInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isBypassed: Boolean = false, // Bypass VPN completely
    val isFirewalled: Boolean = false, // Block network completely
    val icon: Drawable? = null
)
