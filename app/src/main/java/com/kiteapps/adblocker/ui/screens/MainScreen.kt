package com.kiteapps.adblocker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiteapps.adblocker.core.model.DnsLog
import com.kiteapps.adblocker.core.model.DnsServerConfig
import com.kiteapps.adblocker.core.model.RuleType
import com.kiteapps.adblocker.core.model.UserProfile
import com.kiteapps.adblocker.core.model.VpnState
import com.kiteapps.adblocker.core.utils.FeatureGateManager
import com.kiteapps.adblocker.data.billing.BillingManager
import com.kiteapps.adblocker.data.db.CustomRuleRepository
import com.kiteapps.adblocker.data.db.DnsLogRepository
import com.kiteapps.adblocker.data.db.FilterRepository
import com.kiteapps.adblocker.data.preferences.PreferencesManager
import com.kiteapps.adblocker.service.AdBlockVpnService
import com.kiteapps.adblocker.ui.components.ProPaywallSheet
import compose.icons.TablerIcons
import compose.icons.tablericons.Apps
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Filter
import compose.icons.tablericons.FilterOff
import compose.icons.tablericons.GridDots
import compose.icons.tablericons.History
import compose.icons.tablericons.Settings
import compose.icons.tablericons.Shield
import compose.icons.tablericons.ShieldCheck
import kotlinx.coroutines.launch

enum class Screen(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("Shield", TablerIcons.ShieldCheck, TablerIcons.Shield),
    ACTIVITY("Activity", TablerIcons.History, TablerIcons.Clock),
    FILTERS("Filters", TablerIcons.Filter, TablerIcons.FilterOff),
    APPS("Apps", TablerIcons.Apps, TablerIcons.GridDots),
    SETTINGS("Settings", TablerIcons.Settings, TablerIcons.Settings)
}

@Composable
fun MainScreen(
    prefs: PreferencesManager,
    dnsLogRepo: DnsLogRepository,
    filterRepo: FilterRepository,
    customRulesRepo: CustomRuleRepository,
    billingManager: BillingManager,
    featureGateManager: FeatureGateManager,
    onRequestVpnPermission: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    val scope = rememberCoroutineScope()

    var showPaywall by remember { mutableStateOf(false) }
    var paywallTriggerReason by remember { mutableStateOf<String?>(null) }

    // State collections
    val isPro by billingManager.isPro.collectAsStateWithLifecycle()
    val userProfile by prefs.userProfile.collectAsStateWithLifecycle(initialValue = UserProfile.createGuest())
    val vpnState by AdBlockVpnService.vpnState.collectAsStateWithLifecycle()
    val totalBlocked by prefs.totalBlocked.collectAsStateWithLifecycle(initialValue = 0L)
    val totalQueries by prefs.totalQueries.collectAsStateWithLifecycle(initialValue = 0L)
    val currentDnsId by prefs.dnsProviderId.collectAsStateWithLifecycle(initialValue = "cloudflare")
    val customDnsUrl by prefs.customDnsUrl.collectAsStateWithLifecycle(initialValue = "")
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = "system")
    val useDynamicColor by prefs.useDynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val bootAutoStart by prefs.bootAutoStart.collectAsStateWithLifecycle(initialValue = false)
    val showNotifications by prefs.showNotifications.collectAsStateWithLifecycle(initialValue = true)
    val bypassedApps by prefs.bypassedApps.collectAsStateWithLifecycle(initialValue = emptySet())
    val firewalledApps by prefs.firewalledApps.collectAsStateWithLifecycle(initialValue = emptySet())

    val recentLogs by dnsLogRepo.logsFlow.collectAsStateWithLifecycle()
    val filters by filterRepo.filtersFlow.collectAsStateWithLifecycle()
    val isUpdatingFilters by filterRepo.isUpdatingFlow.collectAsStateWithLifecycle()
    val customRules by customRulesRepo.rulesFlow.collectAsStateWithLifecycle()

    // Activity Screen filters
    var activitySearchQuery by remember { mutableStateOf("") }
    var activityFilterBlockedOnly by remember { mutableStateOf<Boolean?>(null) }

    val filteredActivityLogs = remember(recentLogs, activitySearchQuery, activityFilterBlockedOnly) {
        recentLogs.filter { log ->
            val matchesBlocked = when (activityFilterBlockedOnly) {
                true -> log.isBlocked
                false -> !log.isBlocked
                null -> true
            }
            val matchesSearch = activitySearchQuery.isBlank() ||
                    log.domain.contains(activitySearchQuery, ignoreCase = true) ||
                    log.appName.contains(activitySearchQuery, ignoreCase = true) ||
                    log.packageName.contains(activitySearchQuery, ignoreCase = true)
            matchesBlocked && matchesSearch
        }
    }

    val dnsProviderName = remember(currentDnsId, customDnsUrl) {
        if (currentDnsId == "custom") "Custom DoH"
        else DnsServerConfig.PRESETS.find { it.id == currentDnsId }?.name ?: "Cloudflare DNS"
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                Screen.entries.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = { Text(screen.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (currentScreen) {
                Screen.HOME -> {
                    HomeScreen(
                        vpnState = vpnState,
                        dnsProviderName = dnsProviderName,
                        totalBlocked = totalBlocked,
                        totalQueries = totalQueries,
                        recentLogs = recentLogs,
                        userProfile = userProfile,
                        isPro = isPro,
                        onUpgradeClick = {
                            paywallTriggerReason = "Upgrade to AdBlocker Pro for unlimited features!"
                            showPaywall = true
                        },
                        onToggleVpn = {
                            onRequestVpnPermission()
                        },
                        onNavigateToActivity = { currentScreen = Screen.ACTIVITY },
                        onNavigateToFilters = { currentScreen = Screen.FILTERS },
                        onNavigateToAnalytics = onNavigateToAnalytics,
                        onNavigateToBrowser = onNavigateToBrowser,
                        onNavigateToAccount = onNavigateToAccount,
                        onLogClick = { log ->
                            currentScreen = Screen.ACTIVITY
                        }
                    )
                }

                Screen.ACTIVITY -> {
                    ActivityLogScreen(
                        logs = filteredActivityLogs,
                        searchQuery = activitySearchQuery,
                        onSearchQueryChange = { activitySearchQuery = it },
                        filterBlockedOnly = activityFilterBlockedOnly,
                        onFilterChange = { activityFilterBlockedOnly = it },
                        onClearLogs = { scope.launch { dnsLogRepo.clearLogs() } },
                        onAddCustomRule = { domain, type ->
                            if (!featureGateManager.canAddMoreCustomRules(customRules.size)) {
                                paywallTriggerReason = "Free tier supports up to 25 custom rules. Upgrade to Pro for unlimited rules!"
                                showPaywall = true
                            } else {
                                scope.launch { customRulesRepo.addRule(domain, type) }
                            }
                        }
                    )
                }

                Screen.FILTERS -> {
                    FiltersScreen(
                        filters = filters,
                        customRules = customRules,
                        isUpdating = isUpdatingFilters,
                        isPro = isPro,
                        onUpgradePro = {
                            paywallTriggerReason = "Upgrade to Pro to unlock premium filter lists, malware blockers & tracker shields!"
                            showPaywall = true
                        },
                        onToggleFilter = { id, enabled ->
                            val filter = filters.find { it.id == id }
                            if (filter != null && filter.isPro && !isPro) {
                                paywallTriggerReason = "This premium filter list requires AdBlocker Pro."
                                showPaywall = true
                            } else if (enabled && !featureGateManager.canEnableMoreFilterLists(filters.count { it.isEnabled })) {
                                paywallTriggerReason = "Free tier supports up to 3 active filter lists. Upgrade to Pro for all 30+ lists!"
                                showPaywall = true
                            } else {
                                scope.launch { filterRepo.toggleFilter(id, enabled) }
                            }
                        },
                        onAddCustomFilter = { name, url ->
                            if (!isPro) {
                                paywallTriggerReason = "Adding custom filter subscriptions requires AdBlocker Pro."
                                showPaywall = true
                            } else {
                                scope.launch { filterRepo.addCustomFilter(name, url) }
                            }
                        },
                        onAddCustomRule = { domain, type ->
                            if (!featureGateManager.canAddMoreCustomRules(customRules.size)) {
                                paywallTriggerReason = "Free tier supports up to 25 custom rules. Upgrade to Pro for unlimited rules!"
                                showPaywall = true
                            } else {
                                scope.launch { customRulesRepo.addRule(domain, type) }
                            }
                        },
                        onRemoveCustomRule = { domain ->
                            scope.launch { customRulesRepo.removeRule(domain) }
                        },
                        onUpdateAllFilters = {
                            scope.launch { filterRepo.updateAllActiveFilters() }
                        }
                    )
                }

                Screen.APPS -> {
                    AppsScreen(
                        bypassedApps = bypassedApps,
                        firewalledApps = firewalledApps,
                        onToggleBypass = { pkg, bypass ->
                            if (bypass && !bypassedApps.contains(pkg) && !featureGateManager.canBypassMoreApps(bypassedApps.size)) {
                                paywallTriggerReason = "Free tier supports up to 3 bypassed apps. Upgrade to Pro for unlimited per-app firewall!"
                                showPaywall = true
                            } else {
                                scope.launch { prefs.setAppBypassed(pkg, bypass) }
                            }
                        },
                        onToggleFirewall = { pkg, block ->
                            scope.launch { prefs.setAppFirewalled(pkg, block) }
                        }
                    )
                }

                Screen.SETTINGS -> {
                    SettingsScreen(
                        currentDnsId = currentDnsId,
                        customDnsUrl = customDnsUrl,
                        themeMode = themeMode,
                        useDynamicColor = useDynamicColor,
                        bootAutoStart = bootAutoStart,
                        showNotifications = showNotifications,
                        userProfile = userProfile,
                        onDnsSelect = { id -> scope.launch { prefs.setDnsProviderId(id) } },
                        onCustomDnsChange = { url ->
                            if (url.isNotBlank() && !featureGateManager.canUseCustomDoH) {
                                paywallTriggerReason = "Custom encrypted DoH/DoT endpoints require AdBlocker Pro."
                                showPaywall = true
                            } else {
                                scope.launch { prefs.setCustomDnsUrl(url) }
                            }
                        },
                        onThemeSelect = { mode -> scope.launch { prefs.setThemeMode(mode) } },
                        onDynamicColorToggle = { enabled -> scope.launch { prefs.setDynamicColor(enabled) } },
                        onBootAutoStartToggle = { enabled -> scope.launch { prefs.setBootAutoStart(enabled) } },
                        onShowNotificationsToggle = { enabled -> scope.launch { prefs.setShowNotifications(enabled) } },
                        onResetStats = { scope.launch { prefs.resetStats() } },
                        onNavigateToAccount = onNavigateToAccount
                    )
                }
            }
        }

        if (showPaywall) {
            ProPaywallSheet(
                billingManager = billingManager,
                triggerReason = paywallTriggerReason,
                onDismiss = { showPaywall = false }
            )
        }
    }
}
