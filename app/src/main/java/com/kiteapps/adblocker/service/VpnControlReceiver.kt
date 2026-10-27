package com.kiteapps.adblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import timber.log.Timber

class VpnControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START_VPN = "com.kiteapps.adblocker.ACTION_START_VPN"
        const val ACTION_STOP_VPN = "com.kiteapps.adblocker.ACTION_STOP_VPN"
        const val ACTION_TOGGLE_VPN = "com.kiteapps.adblocker.ACTION_TOGGLE_VPN"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Timber.d("VpnControlReceiver received action: $action")
        when (action) {
            ACTION_START_VPN -> {
                if (VpnService.prepare(context) == null) {
                    AdBlockVpnService.start(context)
                } else {
                    Timber.w("Cannot start VPN via ADB: VPN permission not granted yet by user")
                }
            }
            ACTION_STOP_VPN -> {
                AdBlockVpnService.stop(context)
            }
            ACTION_TOGGLE_VPN -> {
                val state = AdBlockVpnService.vpnState.value
                if (state == com.kiteapps.adblocker.core.model.VpnState.RUNNING ||
                    state == com.kiteapps.adblocker.core.model.VpnState.STARTING
                ) {
                    AdBlockVpnService.stop(context)
                } else {
                    if (VpnService.prepare(context) == null) {
                        AdBlockVpnService.start(context)
                    } else {
                        Timber.w("Cannot toggle start VPN via ADB: VPN permission not granted yet by user")
                    }
                }
            }
        }
    }
}
