package com.kiteapps.adblocker.core.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket

class NetworkMonitor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isConnected = MutableStateFlow(checkInitialConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("Network available: $network")
            _isConnected.value = true
            // Verify reachability in background
            scope.launch(Dispatchers.IO) {
                val pingOk = performSocketPing()
                _isConnected.value = pingOk
            }
        }

        override fun onLost(network: Network) {
            Timber.d("Network lost: $network")
            val stillConnected = checkInitialConnectivity()
            _isConnected.value = stillConnected
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Timber.d("Capabilities changed: internet=$hasInternet, validated=$isValidated")
            if (hasInternet && isValidated) {
                _isConnected.value = true
            } else if (!hasInternet) {
                _isConnected.value = false
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register network callback")
        }
        // Verify on startup
        scope.launch(Dispatchers.IO) {
            val pingOk = performSocketPing()
            _isConnected.value = pingOk
        }
    }

    private fun checkInitialConnectivity(): Boolean {
        return try {
            val cm = connectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkReachability(): Boolean = withContext(Dispatchers.IO) {
        _isChecking.value = true
        try {
            val reachable = performSocketPing()
            _isConnected.value = reachable
            reachable
        } finally {
            _isChecking.value = false
        }
    }

    private fun performSocketPing(): Boolean {
        if (!checkInitialConnectivity()) return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("1.1.1.1", 53), 2500)
                true
            }
        } catch (e: Exception) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("8.8.8.8", 53), 2500)
                    true
                }
            } catch (e2: Exception) {
                Timber.w(e2, "Socket ping failed for external DNS roots")
                false
            }
        }
    }
}
