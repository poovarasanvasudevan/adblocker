package com.kiteapps.adblocker

import android.app.Application
import com.kiteapps.adblocker.core.utils.AppNameResolver
import com.kiteapps.adblocker.core.utils.FeatureGateManager
import com.kiteapps.adblocker.data.billing.BillingManager
import com.kiteapps.adblocker.data.db.AdBlockDatabaseHelper
import com.kiteapps.adblocker.data.db.CustomRuleRepository
import com.kiteapps.adblocker.data.db.DnsLogRepository
import com.kiteapps.adblocker.data.db.FilterRepository
import com.kiteapps.adblocker.data.preferences.PreferencesManager
import com.kiteapps.adblocker.data.sync.GoogleDriveSyncManager
import com.kiteapps.adblocker.service.GoTunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class AdBlockerApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var databaseHelper: AdBlockDatabaseHelper
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var dnsLogRepository: DnsLogRepository
        private set

    lateinit var customRuleRepository: CustomRuleRepository
        private set

    lateinit var filterRepository: FilterRepository
        private set

    lateinit var appNameResolver: AppNameResolver
        private set

    lateinit var goTunnelManager: GoTunnelManager
        private set

    lateinit var googleDriveSyncManager: GoogleDriveSyncManager
        private set

    lateinit var billingManager: BillingManager
        private set

    lateinit var featureGateManager: FeatureGateManager
        private set

    lateinit var networkMonitor: com.kiteapps.adblocker.core.utils.NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        databaseHelper = AdBlockDatabaseHelper(this)
        preferencesManager = PreferencesManager(this)
        appNameResolver = AppNameResolver(this)

        networkMonitor = com.kiteapps.adblocker.core.utils.NetworkMonitor(this, applicationScope)
        billingManager = BillingManager.getInstance(this, preferencesManager)
        featureGateManager = FeatureGateManager.getInstance(billingManager)

        dnsLogRepository = DnsLogRepository(databaseHelper, applicationScope)
        customRuleRepository = CustomRuleRepository(databaseHelper, applicationScope)
        filterRepository = FilterRepository(this, databaseHelper, applicationScope)

        googleDriveSyncManager = GoogleDriveSyncManager(
            context = this,
            customRuleRepo = customRuleRepository,
            filterRepo = filterRepository,
            prefs = preferencesManager
        )

        goTunnelManager = GoTunnelManager(
            context = this,
            filterRepo = filterRepository,
            customRulesRepo = customRuleRepository,
            dnsLogRepo = dnsLogRepository,
            prefsManager = preferencesManager,
            appNameResolver = appNameResolver,
            scope = applicationScope
        )

        // Observe user Pro status to load normal.json vs pro.json catalog automatically
        applicationScope.launch {
            billingManager.isPro.collect { isPro ->
                filterRepository.setProStatusAndSync(isPro)
            }
        }

        // When internet connectivity becomes available, sync filter catalog from remote API
        applicationScope.launch {
            networkMonitor.isConnected.collect { isConnected ->
                if (isConnected) {
                    Timber.d("Network connected: triggering filter catalog sync")
                    filterRepository.syncCatalogFromRemote(billingManager.isPro.value)
                }
            }
        }

        Timber.i("AdBlockerApp initialized successfully")
    }
}
