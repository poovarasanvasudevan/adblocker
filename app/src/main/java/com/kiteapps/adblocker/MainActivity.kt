package com.kiteapps.adblocker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kiteapps.adblocker.core.model.UserProfile
import com.kiteapps.adblocker.service.AdBlockVpnService
import com.kiteapps.adblocker.ui.screens.AccountScreen
import com.kiteapps.adblocker.ui.screens.AnalyticsScreen
import com.kiteapps.adblocker.ui.screens.AuthScreen
import com.kiteapps.adblocker.ui.screens.InAppBrowserScreen
import com.kiteapps.adblocker.ui.screens.MainScreen
import com.kiteapps.adblocker.ui.screens.NoInternetScreen
import com.kiteapps.adblocker.ui.screens.SplashScreen
import com.kiteapps.adblocker.ui.theme.AdBlockerTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Timber.d("VPN permission granted by user")
            AdBlockVpnService.start(this)
        } else {
            Timber.w("VPN permission denied by user")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Timber.d("Notification permission granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()

        val app = application as AdBlockerApp

        setContent {
            val themeMode by app.preferencesManager.themeMode.collectAsState(initial = "system")
            val useDynamicColor by app.preferencesManager.useDynamicColor.collectAsState(initial = true)
            val authCompletedOrSkipped by app.preferencesManager.authCompletedOrSkipped.collectAsState(initial = null)
            var splashCompleted by remember { mutableStateOf(false) }

            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { isDark }
                )
                onDispose {}
            }

            val isNetworkConnected by app.networkMonitor.isConnected.collectAsStateWithLifecycle()
            val isCheckingNetwork by app.networkMonitor.isChecking.collectAsStateWithLifecycle()
            val rootScope = rememberCoroutineScope()

            AdBlockerTheme(
                darkTheme = isDark,
                dynamicColor = useDynamicColor
            ) {
                if (!isNetworkConnected) {
                    NoInternetScreen(
                        isChecking = isCheckingNetwork,
                        onRetry = {
                            rootScope.launch {
                                val ok = app.networkMonitor.checkReachability()
                                if (ok) {
                                    app.filterRepository.syncCatalogFromRemote(app.billingManager.isPro.value)
                                }
                            }
                        }
                    )
                } else {
                    Crossfade(
                        targetState = splashCompleted,
                        animationSpec = tween(500),
                        label = "splashCrossfade"
                    ) { isSplashDone ->
                        if (!isSplashDone) {
                            SplashScreen(
                                isReadyToNavigate = authCompletedOrSkipped != null,
                                onNavigateNext = {
                                    splashCompleted = true
                                }
                            )
                        } else {
                            val navController = rememberNavController()
                            val startRoute = if (authCompletedOrSkipped == true) "main" else "auth"

                            NavHost(
                                navController = navController,
                                startDestination = startRoute
                            ) {
                            composable("auth") {
                                val scope = rememberCoroutineScope()
                                AuthScreen(
                                    onGoogleSignInSuccess = { id, name, email, avatarUrl ->
                                        scope.launch {
                                            app.preferencesManager.setGoogleUser(id, name, email, avatarUrl)
                                            navController.navigate("main") {
                                                popUpTo("auth") { inclusive = true }
                                            }
                                        }
                                    },
                                    onSkip = { deviceName ->
                                        scope.launch {
                                            app.preferencesManager.setAuthSkipped(deviceName)
                                            navController.navigate("main") {
                                                popUpTo("auth") { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }

                            composable("main") {
                                MainScreen(
                                    prefs = app.preferencesManager,
                                    dnsLogRepo = app.dnsLogRepository,
                                    filterRepo = app.filterRepository,
                                    customRulesRepo = app.customRuleRepository,
                                    billingManager = app.billingManager,
                                    featureGateManager = app.featureGateManager,
                                    onRequestVpnPermission = {
                                        toggleVpn()
                                    },
                                    onNavigateToAnalytics = {
                                        navController.navigate("analytics")
                                    },
                                    onNavigateToBrowser = {
                                        navController.navigate("browser")
                                    },
                                    onNavigateToAccount = {
                                        navController.navigate("account")
                                    }
                                )
                            }

                            composable("analytics") {
                                val recentLogs by app.dnsLogRepository.logsFlow.collectAsStateWithLifecycle()
                                val totalBlocked by app.preferencesManager.totalBlocked.collectAsStateWithLifecycle(initialValue = 0L)
                                val totalQueries by app.preferencesManager.totalQueries.collectAsStateWithLifecycle(initialValue = 0L)

                                AnalyticsScreen(
                                    logs = recentLogs,
                                    totalBlocked = totalBlocked,
                                    totalQueries = totalQueries,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("browser") {
                                InAppBrowserScreen(
                                    filterRepo = app.filterRepository,
                                    customRulesRepo = app.customRuleRepository,
                                    dnsLogRepo = app.dnsLogRepository,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("account") {
                                val userProfile by app.preferencesManager.userProfile.collectAsStateWithLifecycle(initialValue = UserProfile.createGuest())
                                val lastCloudBackupTime by app.preferencesManager.lastCloudBackupTime.collectAsStateWithLifecycle(initialValue = 0L)
                                val scope = rememberCoroutineScope()

                                AccountScreen(
                                    userProfile = userProfile,
                                    driveSyncManager = app.googleDriveSyncManager,
                                    billingManager = app.billingManager,
                                    featureGateManager = app.featureGateManager,
                                    lastCloudBackupTime = lastCloudBackupTime,
                                    onGoogleSignInSuccess = { id, name, email, avatarUrl ->
                                        scope.launch {
                                            app.preferencesManager.setGoogleUser(id, name, email, avatarUrl)
                                        }
                                    },
                                    onSignOut = {
                                        scope.launch {
                                            app.preferencesManager.signOut()
                                        }
                                    },
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    private fun toggleVpn() {
        val app = application as AdBlockerApp
        if (!app.networkMonitor.isConnected.value) {
            android.widget.Toast.makeText(
                this,
                "Active internet connection required to run AdBlocker protection",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        val state = AdBlockVpnService.vpnState.value
        if (state == com.kiteapps.adblocker.core.model.VpnState.RUNNING ||
            state == com.kiteapps.adblocker.core.model.VpnState.STARTING
        ) {
            AdBlockVpnService.stop(this)
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                AdBlockVpnService.start(this)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
