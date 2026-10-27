package com.kiteapps.adblocker.core.model

import android.os.Build

data class UserProfile(
    val isLoggedIn: Boolean = false,
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val authProvider: String = "guest", // "google" or "guest"
    val deviceName: String = ""
) {
    companion object {
        fun defaultDeviceName(): String {
            val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            val model = Build.MODEL.orEmpty()
            return if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model".trim().ifEmpty { "Android Device" }
            }
        }

        fun createGuest(deviceName: String = defaultDeviceName()): UserProfile {
            return UserProfile(
                isLoggedIn = false,
                userId = "guest_${System.currentTimeMillis()}",
                displayName = "$deviceName Profile",
                email = "guest@device.local",
                avatarUrl = "",
                authProvider = "guest",
                deviceName = deviceName
            )
        }
    }
}
