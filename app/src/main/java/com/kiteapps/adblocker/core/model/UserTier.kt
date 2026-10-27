package com.kiteapps.adblocker.core.model

enum class UserTier(
    val id: String,
    val title: String,
    val isPro: Boolean,
    val badge: String,
    val description: String
) {
    FREE(
        id = "free",
        title = "AdBlocker Standard",
        isPro = false,
        badge = "Free",
        description = "Core local VPN protection & basic filter lists"
    ),
    PRO_MONTHLY(
        id = "pro_monthly",
        title = "Pro Monthly",
        isPro = true,
        badge = "Pro",
        description = "Unlimited filter lists, cloud sync & encrypted DoH"
    ),
    PRO_ANNUAL(
        id = "pro_annual",
        title = "Pro Annual",
        isPro = true,
        badge = "Pro Plus",
        description = "All Pro perks with 7-day free trial & best annual value"
    ),
    PRO_LIFETIME(
        id = "pro_lifetime",
        title = "Pro Lifetime",
        isPro = true,
        badge = "Lifetime",
        description = "Permanent lifetime access with one-time payment"
    );

    companion object {
        fun fromId(id: String?): UserTier = when (id?.lowercase()) {
            "pro_monthly", "adblocker_pro_monthly" -> PRO_MONTHLY
            "pro_annual", "adblocker_pro_annual" -> PRO_ANNUAL
            "pro_lifetime", "adblocker_pro_lifetime" -> PRO_LIFETIME
            else -> FREE
        }
    }
}
