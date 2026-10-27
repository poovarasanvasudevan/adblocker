package com.kiteapps.adblocker.data.billing

object BillingConstants {
    // Subscription Products (configured in Google Play Console)
    const val PRODUCT_PRO_MONTHLY = "adblocker_pro_monthly"
    const val PRODUCT_PRO_ANNUAL = "adblocker_pro_annual"

    // One-Time In-App Purchase Product (configured in Google Play Console)
    const val PRODUCT_PRO_LIFETIME = "adblocker_pro_lifetime"

    // Fallback UI display strings if offline or before BillingClient initializes
    const val DEFAULT_PRICE_MONTHLY = "$1.99"
    const val DEFAULT_PRICE_ANNUAL = "$14.99"
    const val DEFAULT_PRICE_LIFETIME = "$29.99"
}
