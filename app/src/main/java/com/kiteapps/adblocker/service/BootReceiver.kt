package com.kiteapps.adblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.kiteapps.adblocker.AdBlockerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val app = context.applicationContext as AdBlockerApp
            CoroutineScope(Dispatchers.IO).launch {
                val shouldAutoStart = app.preferencesManager.bootAutoStart.first()
                if (shouldAutoStart) {
                    if (VpnService.prepare(context) == null) {
                        Timber.d("Boot completed: auto-starting AdBlocker VPN")
                        AdBlockVpnService.start(context)
                    } else {
                        Timber.w("Cannot auto-start VPN on boot: permission not granted")
                    }
                }
            }
        }
    }
}
