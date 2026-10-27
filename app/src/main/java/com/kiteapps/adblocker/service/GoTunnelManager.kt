package com.kiteapps.adblocker.service

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.kiteapps.adblocker.core.model.DnsLog
import com.kiteapps.adblocker.core.model.DnsServerConfig
import com.kiteapps.adblocker.core.utils.AppNameResolver
import com.kiteapps.adblocker.data.db.CustomRuleRepository
import com.kiteapps.adblocker.data.db.DnsLogRepository
import com.kiteapps.adblocker.data.db.FilterRepository
import com.kiteapps.adblocker.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import tunnel.AppResolver
import tunnel.DomainChecker
import tunnel.FirewallChecker
import tunnel.SocketProtector
import tunnel.UIDResolver
import java.net.InetAddress
import java.net.InetSocketAddress

class GoTunnelManager(
    private val context: Context,
    private val filterRepo: FilterRepository,
    private val customRulesRepo: CustomRuleRepository,
    private val dnsLogRepo: DnsLogRepository,
    private val prefsManager: PreferencesManager,
    private val appNameResolver: AppNameResolver,
    private val scope: CoroutineScope
) {
    private val engine = tunnel.Tunnel.newEngine()

    @Volatile
    private var isRunning = false

    private var tunnelJob: Job? = null

    @Volatile
    private var firewalledPackages = setOf<String>()

    fun updateFirewalledPackages(packages: Set<String>) {
        firewalledPackages = packages
    }

    fun configureDns(dnsConfig: DnsServerConfig) {
        try {
            engine.setDNS(
                dnsConfig.protocol,
                dnsConfig.primary,
                dnsConfig.fallback,
                dnsConfig.dohUrl
            )
            Timber.d("Engine DNS set to ${dnsConfig.name} (${dnsConfig.dohUrl})")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set DNS in Go engine")
        }
    }

    private fun setupCallbacks() {
        // Domain checker: custom allow > custom block > filters
        engine.setDomainChecker(object : DomainChecker {
            override fun isBlocked(domain: String): Boolean {
                if (customRulesRepo.isWhitelisted(domain)) return false
                if (customRulesRepo.isBlacklisted(domain)) return true
                return filterRepo.isDomainBlocked(domain)
            }

            override fun getBlockReason(domain: String): String {
                if (customRulesRepo.isWhitelisted(domain)) return ""
                if (customRulesRepo.isBlacklisted(domain)) return "CUSTOM_RULE"
                if (filterRepo.isDomainBlocked(domain)) return "FILTER_LIST"
                return ""
            }

            override fun hasCustomRule(domain: String): Long {
                if (customRulesRepo.isWhitelisted(domain)) return 0L
                if (customRulesRepo.isBlacklisted(domain)) return 1L
                return -1L
            }
        })

        // App identity resolver
        engine.setAppResolver(AppResolver { sourcePort, sourceIP, destIP, destPort ->
            try {
                val identity = appNameResolver.resolveIdentity(
                    sourcePort.toInt(), sourceIP, destIP, destPort.toInt()
                )
                if (identity.packageName.isNotEmpty()) identity.packageName else identity.appName
            } catch (e: Exception) {
                ""
            }
        })

        // UID resolver on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                engine.setUIDResolver(UIDResolver { protocol, localIP, localPort, remoteIP, remotePort ->
                    try {
                        val proto = when (protocol.toInt()) {
                            6 -> OsConstants.IPPROTO_TCP
                            17 -> OsConstants.IPPROTO_UDP
                            else -> return@UIDResolver -1L
                        }
                        val local = InetSocketAddress(InetAddress.getByName(localIP), localPort.toInt())
                        val remote = InetSocketAddress(InetAddress.getByName(remoteIP), remotePort.toInt())
                        cm.getConnectionOwnerUid(proto, local, remote).toLong()
                    } catch (e: Exception) {
                        -1L
                    }
                })
            }
        }

        // Per-app Firewall
        engine.setFirewallChecker(FirewallChecker { packageNameOrAppName ->
            firewalledPackages.contains(packageNameOrAppName)
        })

        // Log callback for real-time live activity
        engine.setConnLogEnabled(true)
        engine.setLogCallback { domain, blocked, queryType, responseTimeMs, packageNameOrAppName, resolvedIP, blockedBy ->
            scope.launch(Dispatchers.IO) {
                try {
                    val appName = if (packageNameOrAppName.contains(".")) {
                        runCatching {
                            val pm = context.packageManager
                            val info = pm.getApplicationInfo(packageNameOrAppName, 0)
                            pm.getApplicationLabel(info).toString()
                        }.getOrDefault(packageNameOrAppName)
                    } else {
                        packageNameOrAppName
                    }

                    val queryTypeStr = when (queryType.toInt()) {
                        1 -> "A"
                        28 -> "AAAA"
                        5 -> "CNAME"
                        15 -> "MX"
                        16 -> "TXT"
                        65 -> "HTTPS"
                        else -> "DNS (${queryType.toInt()})"
                    }

                    val entry = DnsLog(
                        domain = domain,
                        isBlocked = blocked,
                        queryType = queryTypeStr,
                        latencyMs = responseTimeMs,
                        appName = appName,
                        packageName = packageNameOrAppName,
                        resolvedIp = resolvedIP,
                        blockedBy = if (blockedBy.isNotEmpty()) blockedBy else if (blocked) "AdBlocker" else "",
                        timestamp = System.currentTimeMillis()
                    )

                    dnsLogRepo.insertLog(entry)
                    prefsManager.incrementQueryStats(blocked)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing DNS log entry")
                }
            }
        }
    }

    fun start(
        vpnInterface: ParcelFileDescriptor,
        socketProtector: ((Int) -> Boolean)? = null,
        onTerminated: (() -> Unit)? = null
    ) {
        if (isRunning) return
        isRunning = true

        setupCallbacks()

        // Pass any compiled trie and bloom paths
        val triePaths = filterRepo.getAdTriePaths()
        val bloomPaths = filterRepo.getAdBloomPaths()
        if (triePaths.isNotEmpty()) {
            engine.setTries(triePaths, "", bloomPaths, "")
        }

        val protector = SocketProtector { fd ->
            socketProtector?.invoke(fd.toInt()) ?: false
        }

        val fd = vpnInterface.fd
        tunnelJob?.cancel()
        tunnelJob = scope.launch(Dispatchers.IO) {
            try {
                Timber.d("Starting Go tunnel engine in full mode with fd=$fd")
                engine.startFull(fd.toLong(), protector)
                Timber.d("Go tunnel engine loop finished normally")
            } catch (e: Exception) {
                Timber.e(e, "Error running Go tunnel engine")
            } finally {
                val wasRunning = isRunning
                isRunning = false
                if (wasRunning) {
                    onTerminated?.invoke()
                }
            }
        }
    }

    fun stop() {
        if (!isRunning && tunnelJob?.isActive != true) return
        isRunning = false
        try {
            engine.stop()
            Timber.d("Go tunnel engine stopped successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Go tunnel engine")
        } finally {
            tunnelJob?.cancel()
            tunnelJob = null
        }
    }
}
