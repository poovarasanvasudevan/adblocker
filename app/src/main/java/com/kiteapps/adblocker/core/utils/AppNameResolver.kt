package com.kiteapps.adblocker.core.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import timber.log.Timber
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class AppNameResolver(private val context: Context) {
    private val uidToAppNameCache = ConcurrentHashMap<Int, String>()
    private val uidToPackageNameCache = ConcurrentHashMap<Int, String>()

    data class AppIdentity(val appName: String, val packageName: String)

    fun resolveIdentity(sourcePort: Int, sourceIp: ByteArray, destIp: ByteArray, destPort: Int): AppIdentity {
        val uid = findUidForConnection(sourcePort, sourceIp, destIp, destPort)
        if (uid < 0) return AppIdentity("", "")
        return AppIdentity(
            appName = getAppNameForUid(uid),
            packageName = getPackageNameForUid(uid)
        )
    }

    fun findUidForConnection(
        sourcePort: Int,
        sourceIp: ByteArray,
        destIp: ByteArray,
        destPort: Int
    ): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val local = InetSocketAddress(InetAddress.getByAddress(sourceIp), sourcePort)
                    val remote = InetSocketAddress(InetAddress.getByAddress(destIp), destPort)
                    val uid = cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
                    if (uid >= 0) return uid
                }
            } catch (e: Exception) {
                // Ignore fallback
            }
        }
        return -1
    }

    fun getAppNameForUid(uid: Int): String {
        return uidToAppNameCache.getOrPut(uid) {
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(uid)
            if (!packages.isNullOrEmpty()) {
                val pkg = packages[0]
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    pkg
                }
            } else {
                when (uid) {
                    0 -> "Root"
                    1000 -> "Android System"
                    1052 -> "DNS Resolver"
                    1073 -> "Network Stack"
                    else -> "UID $uid"
                }
            }
        }
    }

    fun getPackageNameForUid(uid: Int): String {
        return uidToPackageNameCache.getOrPut(uid) {
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(uid)
            packages?.firstOrNull() ?: ""
        }
    }
}
