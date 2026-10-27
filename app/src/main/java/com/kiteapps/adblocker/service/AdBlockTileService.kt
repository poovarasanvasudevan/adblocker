package com.kiteapps.adblocker.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.kiteapps.adblocker.MainActivity
import com.kiteapps.adblocker.core.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class AdBlockTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        stateJob?.cancel()
        stateJob = scope.launch {
            AdBlockVpnService.vpnState.collect { state ->
                updateTile(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateJob?.cancel()
        stateJob = null
    }

    override fun onClick() {
        super.onClick()
        val isRunning = AdBlockVpnService.isRunning
        if (isRunning) {
            AdBlockVpnService.stop(this)
        } else {
            // Check if VPN permission is granted
            if (VpnService.prepare(this) != null) {
                // Must launch activity to ask permission
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            } else {
                AdBlockVpnService.start(this)
            }
        }
    }

    private fun updateTile(state: VpnState) {
        val tile = qsTile ?: return
        when (state) {
            VpnState.RUNNING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "AdBlocker: On"
            }
            VpnState.STARTING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Connecting..."
            }
            VpnState.STOPPING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Stopping..."
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "AdBlocker: Off"
            }
        }
        tile.updateTile()
    }
}
