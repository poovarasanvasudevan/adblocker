package com.kiteapps.adblocker.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.kiteapps.adblocker.AdBlockerApp
import com.kiteapps.adblocker.core.model.DnsServerConfig
import com.kiteapps.adblocker.core.model.VpnState
import com.kiteapps.adblocker.core.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class AdBlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.kiteapps.adblocker.START_VPN"
        const val ACTION_STOP = "com.kiteapps.adblocker.STOP_VPN"

        private val _vpnState = MutableStateFlow(VpnState.STOPPED)
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        val isRunning: Boolean get() = _vpnState.value == VpnState.RUNNING

        fun start(context: Context) {
            val intent = Intent(context, AdBlockVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdBlockVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var goTunnelManager: GoTunnelManager

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        val app = application as AdBlockerApp
        goTunnelManager = app.goTunnelManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                if (_vpnState.value != VpnState.RUNNING && _vpnState.value != VpnState.STARTING) {
                    startVpn()
                }
            }
            ACTION_STOP -> {
                stopVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        _vpnState.value = VpnState.STARTING
        serviceScope.launch {
            try {
                val app = application as AdBlockerApp
                if (!app.networkMonitor.isConnected.value) {
                    Timber.w("Cannot start VPN: No active internet connection")
                    _vpnState.value = VpnState.STOPPED
                    stopSelf()
                    return@launch
                }
                val prefs = app.preferencesManager
                val bypassedApps = prefs.bypassedApps.first()
                val firewalledApps = prefs.firewalledApps.first()
                val selectedDnsId = prefs.dnsProviderId.first()
                val customDns = prefs.customDnsUrl.first()

                goTunnelManager.updateFirewalledPackages(firewalledApps)

                // Select DNS Provider
                val dnsConfig = if (selectedDnsId == "custom" && customDns.isNotBlank()) {
                    DnsServerConfig(
                        id = "custom",
                        name = "Custom DoH",
                        description = customDns,
                        protocol = "DOH",
                        primary = "1.1.1.1",
                        fallback = "8.8.8.8",
                        dohUrl = customDns,
                        isCustom = true
                    )
                } else {
                    DnsServerConfig.PRESETS.find { it.id == selectedDnsId } ?: DnsServerConfig.CLOUDFLARE
                }
                goTunnelManager.configureDns(dnsConfig)

                // Build VPN Interface
                val builder = Builder().apply {
                    setSession("AdBlocker")
                    setMtu(1350)
                    setBlocking(false)
                    addAddress("100.64.100.2", 32)
                    addRoute("100.64.100.1", 32)
                    addDnsServer("100.64.100.1")
                    addRoute("0.0.0.0", 0)

                    // Always disallow self to prevent loops
                    try {
                        addDisallowedApplication(packageName)
                    } catch (e: Exception) {
                        Timber.w(e, "Could not disallow self")
                    }

                    // Disallow user-selected bypassed apps
                    for (pkg in bypassedApps) {
                        try {
                            addDisallowedApplication(pkg)
                        } catch (e: Exception) {
                            Timber.w(e, "Could not disallow app: $pkg")
                        }
                    }
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    Timber.e("Failed to establish VPN interface")
                    _vpnState.value = VpnState.STOPPED
                    stopSelf()
                    return@launch
                }

                vpnInterface = pfd

                // Start Foreground Notification
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    notificationHelper.buildRunningNotification(0, 0)
                )

                // Start Go tunnel engine
                goTunnelManager.start(pfd, { fd -> protect(fd) }) {
                    if (_vpnState.value == VpnState.RUNNING) {
                        Timber.w("Go tunnel engine terminated unexpectedly, stopping VPN service")
                        stopVpn()
                    }
                }

                _vpnState.value = VpnState.RUNNING
                prefs.setVpnEnabled(true)
                Timber.d("AdBlocker VPN started successfully")

                startStatsNotifier()
            } catch (e: Exception) {
                Timber.e(e, "Error starting VPN")
                _vpnState.value = VpnState.STOPPED
                stopVpn()
            }
        }
    }

    private fun startStatsNotifier() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            val app = application as AdBlockerApp
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (_vpnState.value == VpnState.RUNNING) {
                delay(3000)
                val blocked = app.preferencesManager.totalBlocked.first()
                val total = app.preferencesManager.totalQueries.first()
                nm.notify(
                    NotificationHelper.NOTIFICATION_ID,
                    notificationHelper.buildRunningNotification(blocked, total)
                )
            }
        }
    }

    private fun stopVpn() {
        if (_vpnState.value == VpnState.STOPPING || _vpnState.value == VpnState.STOPPED) {
            return
        }
        _vpnState.value = VpnState.STOPPING
        serviceScope.launch {
            try {
                statsJob?.cancel()
                statsJob = null
                goTunnelManager.stop()
                vpnInterface?.close()
                vpnInterface = null
                val app = application as AdBlockerApp
                app.preferencesManager.setVpnEnabled(false)
            } catch (e: Exception) {
                Timber.e(e, "Error during VPN shutdown")
            } finally {
                _vpnState.value = VpnState.STOPPED
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
    }
}
