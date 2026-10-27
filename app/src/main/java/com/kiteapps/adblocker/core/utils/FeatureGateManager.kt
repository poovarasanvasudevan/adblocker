package com.kiteapps.adblocker.core.utils

import com.kiteapps.adblocker.core.model.UserTier
import com.kiteapps.adblocker.data.billing.BillingManager
import kotlinx.coroutines.flow.StateFlow

class FeatureGateManager(
    private val billingManager: BillingManager
) {
    val userTier: StateFlow<UserTier> = billingManager.userTier
    val isPro: StateFlow<Boolean> = billingManager.isPro

    // Numeric Limits
    val maxActiveFilterLists: Int
        get() = if (isPro.value) Int.MAX_VALUE else FREE_MAX_FILTER_LISTS

    val maxCustomRules: Int
        get() = if (isPro.value) Int.MAX_VALUE else FREE_MAX_CUSTOM_RULES

    val maxBypassedApps: Int
        get() = if (isPro.value) Int.MAX_VALUE else FREE_MAX_BYPASSED_APPS

    // Feature Flags
    val canUseCloudSync: Boolean
        get() = isPro.value

    val canUseCustomDoH: Boolean
        get() = isPro.value

    val canExportLogs: Boolean
        get() = isPro.value

    val canAutoDailyUpdates: Boolean
        get() = isPro.value

    // Limit Checkers
    fun canEnableMoreFilterLists(currentActiveCount: Int): Boolean {
        return isPro.value || currentActiveCount < FREE_MAX_FILTER_LISTS
    }

    fun canAddMoreCustomRules(currentRulesCount: Int): Boolean {
        return isPro.value || currentRulesCount < FREE_MAX_CUSTOM_RULES
    }

    fun canBypassMoreApps(currentBypassedCount: Int): Boolean {
        return isPro.value || currentBypassedCount < FREE_MAX_BYPASSED_APPS
    }

    companion object {
        const val FREE_MAX_FILTER_LISTS = 3
        const val FREE_MAX_CUSTOM_RULES = 25
        const val FREE_MAX_BYPASSED_APPS = 3

        @Volatile
        private var instance: FeatureGateManager? = null

        fun getInstance(billingManager: BillingManager): FeatureGateManager {
            return instance ?: synchronized(this) {
                instance ?: FeatureGateManager(billingManager).also {
                    instance = it
                }
            }
        }
    }
}
