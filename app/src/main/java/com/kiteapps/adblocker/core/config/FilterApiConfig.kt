package com.kiteapps.adblocker.core.config

object FilterApiConfig {
    /**
     * Unified remote API Endpoint containing all filter lists.
     * Each filter item has "pro": true or "pro": false to distinguish Free vs Pro filters.
     */
    var FILTERS_API_URL: String = "https://v1.kiteapps.in/api/adbl/filters.json"

    /**
     * Set of default free filter IDs for fallback entitlement categorization.
     */
    val DEFAULT_FREE_FILTER_IDS = setOf(
        "adguard_base",
        "adguard_mobile",
        "easylist",
        "stevenblack",
        "adg_adguard_tracking_protection_filter",
        "easyprivacy",
        "yoyo_adservers",
        "adg_adguard_dns_filter"
    )

    fun getCatalogUrl(): String = FILTERS_API_URL
}


