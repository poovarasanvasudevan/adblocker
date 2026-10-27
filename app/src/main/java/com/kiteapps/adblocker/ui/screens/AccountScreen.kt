@file:Suppress("DEPRECATION")

package com.kiteapps.adblocker.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.kiteapps.adblocker.core.auth.GoogleAuthConfig
import com.kiteapps.adblocker.core.model.CloudBackupData
import com.kiteapps.adblocker.core.model.UserProfile
import com.kiteapps.adblocker.core.utils.FeatureGateManager
import com.kiteapps.adblocker.data.billing.BillingManager
import com.kiteapps.adblocker.data.sync.GoogleDriveSyncManager
import com.kiteapps.adblocker.ui.components.BentoStatCard
import com.kiteapps.adblocker.ui.components.ProPaywallSheet
import com.kiteapps.adblocker.ui.components.UserAvatar
import com.kiteapps.adblocker.ui.theme.BlockedCoral
import com.kiteapps.adblocker.ui.theme.GoogleTealLight
import com.kiteapps.adblocker.ui.theme.ShieldActiveGreen
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.BrandGoogle
import compose.icons.tablericons.BrandGoogleDrive
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Cloud
import compose.icons.tablericons.CloudDownload
import compose.icons.tablericons.CloudUpload
import compose.icons.tablericons.Crown
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.Fingerprint
import compose.icons.tablericons.Logout
import compose.icons.tablericons.Mail
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Server
import compose.icons.tablericons.Shield
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.Stars
import compose.icons.tablericons.User
import compose.icons.tablericons.UserCheck
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    userProfile: UserProfile,
    driveSyncManager: GoogleDriveSyncManager,
    billingManager: BillingManager,
    featureGateManager: FeatureGateManager,
    lastCloudBackupTime: Long,
    onGoogleSignInSuccess: (id: String, name: String, email: String, avatarUrl: String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val userTier by billingManager.userTier.collectAsState()
    val isPro by billingManager.isPro.collectAsState()

    var showPaywall by remember { mutableStateOf(false) }
    var paywallTriggerReason by remember { mutableStateOf<String?>(null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSignOutBottomSheet by remember { mutableStateOf(false) }
    var showRestoreBottomSheet by remember { mutableStateOf(false) }
    var pendingRestoreData by remember { mutableStateOf<CloudBackupData?>(null) }
    var showDemoOption by remember { mutableStateOf(false) }

    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    val signOutSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val restoreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GoogleAuthConfig.WEB_CLIENT_ID)
            .requestScopes(GoogleAuthConfig.DRIVE_SCOPE)
            .requestEmail()
            .requestProfile()
            .build()
    }

    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, gso)
    }

    val lastBackupFormatted = remember(lastCloudBackupTime) {
        if (lastCloudBackupTime > 0) {
            SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault()).format(Date(lastCloudBackupTime))
        } else {
            "No cloud backup created yet"
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                onGoogleSignInSuccess(
                    account.id ?: "google_${System.currentTimeMillis()}",
                    account.displayName ?: "Google User",
                    account.email ?: "",
                    account.photoUrl?.toString() ?: ""
                )
                errorMessage = null
                scope.launch {
                    snackbarHostState.showSnackbar("Signed in with Google. Drive sync enabled.")
                }
            } catch (e: ApiException) {
                Timber.w(e, "Google Sign In ApiException statusCode=${e.statusCode}")
                errorMessage = "Google sign-in error (code ${e.statusCode}). You can test with a Demo Google Profile."
                showDemoOption = true
            }
        } else {
            errorMessage = "Google sign-in was cancelled."
            showDemoOption = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Account & Cloud Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (userProfile.isLoggedIn) "Google Cloud & Drive Sync" else "Local Device Storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = TablerIcons.ArrowLeft,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (userProfile.isLoggedIn) ShieldActiveGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (userProfile.isLoggedIn) TablerIcons.CircleCheck else TablerIcons.DeviceMobile,
                                contentDescription = null,
                                tint = if (userProfile.isLoggedIn) ShieldActiveGreen else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (userProfile.isLoggedIn) "Cloud Active" else "Guest",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (userProfile.isLoggedIn) ShieldActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            // 1. Hero Profile Bento Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar Container with Status Ring
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (userProfile.isLoggedIn) GoogleTealLight.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = if (userProfile.isLoggedIn) ShieldActiveGreen else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                UserAvatar(
                                    userProfile = userProfile,
                                    size = 76.dp
                                )
                            }

                            // Provider Mini Badge
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (userProfile.isLoggedIn) TablerIcons.BrandGoogle else TablerIcons.DeviceMobile,
                                    contentDescription = null,
                                    tint = if (userProfile.isLoggedIn) GoogleTealLight else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = userProfile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        if (userProfile.email.isNotBlank()) {
                            Text(
                                text = userProfile.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Security Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (userProfile.isLoggedIn) ShieldActiveGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (userProfile.isLoggedIn) TablerIcons.ShieldCheck else TablerIcons.Shield,
                                    contentDescription = null,
                                    tint = if (userProfile.isLoggedIn) ShieldActiveGreen else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (userProfile.isLoggedIn) "Google Account Verified • Active" else "Device Guest Profile • Unlinked",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (userProfile.isLoggedIn) ShieldActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 2. Bento Stats Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoStatCard(
                        title = "Account Tier",
                        value = userTier.badge,
                        icon = if (isPro) TablerIcons.Crown else TablerIcons.User,
                        iconColor = if (isPro) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        subtitle = if (isPro) userTier.title else "Free • Tap to Upgrade",
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (!isPro) {
                                    paywallTriggerReason = "Upgrade to AdBlocker Pro for unlimited features!"
                                    showPaywall = true
                                }
                            }
                    )

                    BentoStatCard(
                        title = "Device Model",
                        value = userProfile.deviceName.take(12),
                        icon = TablerIcons.DeviceMobile,
                        iconColor = ShieldActiveGreen,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        subtitle = "Hardware Profile",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Pro Upgrade Banner (if not already Pro)
            if (!isPro) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                paywallTriggerReason = "Upgrade to Pro for Unlimited Rules, Cloud Sync & Custom DoH"
                                showPaywall = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = TablerIcons.Stars,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Upgrade to Pro",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF00E676).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "7-DAY TRIAL",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF00E676),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Google Drive Sync, 30+ filter lists & custom encrypted DNS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = TablerIcons.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF)
                            )
                        }
                    }
                }
            }

            // 3. Google Drive Backup & Sync Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = TablerIcons.BrandGoogleDrive,
                                    contentDescription = "Google Drive",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Drive Sync",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Custom rules, filters & DoH DNS URLs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        // Backup Status Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = TablerIcons.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Last Backup:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = lastBackupFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Drive Action Buttons
                        if (userProfile.isLoggedIn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Backup Now
                                Button(
                                    onClick = {
                                        if (!featureGateManager.canUseCloudSync) {
                                            paywallTriggerReason = "Google Drive Cloud Backup requires AdBlocker Pro"
                                            showPaywall = true
                                            return@Button
                                        }
                                        isBackingUp = true
                                        scope.launch {
                                            val currentAccount = GoogleSignIn.getLastSignedInAccount(context)
                                            val result = driveSyncManager.uploadToGoogleDrive(currentAccount)
                                            isBackingUp = false
                                            result.onSuccess { summary ->
                                                snackbarHostState.showSnackbar("Backed up ${summary.rulesCount} rules and ${summary.filtersCount} filters to Google Drive!")
                                            }.onFailure { err ->
                                                snackbarHostState.showSnackbar("Drive upload failed: ${err.localizedMessage}")
                                            }
                                        }
                                    },
                                    enabled = !isBackingUp && !isRestoring,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(21.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    if (isBackingUp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        Icon(
                                            imageVector = TablerIcons.CloudUpload,
                                            contentDescription = "Backup",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Backup Now",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Restore from Drive
                                OutlinedButton(
                                    onClick = {
                                        if (!featureGateManager.canUseCloudSync) {
                                            paywallTriggerReason = "Google Drive Cloud Restore requires AdBlocker Pro"
                                            showPaywall = true
                                            return@OutlinedButton
                                        }
                                        isRestoring = true
                                        scope.launch {
                                            val currentAccount = GoogleSignIn.getLastSignedInAccount(context)
                                            val result = driveSyncManager.downloadFromGoogleDrive(currentAccount)
                                            isRestoring = false
                                            result.onSuccess { data ->
                                                pendingRestoreData = data
                                                showRestoreBottomSheet = true
                                            }.onFailure { err ->
                                                snackbarHostState.showSnackbar("No backup found or drive error: ${err.localizedMessage}")
                                            }
                                        }
                                    },
                                    enabled = !isBackingUp && !isRestoring,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(21.dp)
                                ) {
                                    if (isRestoring) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = TablerIcons.CloudDownload,
                                            contentDescription = "Restore",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Restore",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // If guest: prompt to sign in with Google to enable Drive sync
                            Button(
                                onClick = {
                                    try {
                                        launcher.launch(googleSignInClient.signInIntent)
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to launch Google Sign In")
                                        showDemoOption = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = TablerIcons.BrandGoogle,
                                        contentDescription = "Google",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sign in to Enable Drive Sync",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Demo fallback option if needed
            if (showDemoOption && !userProfile.isLoggedIn) {
                item {
                    OutlinedButton(
                        onClick = {
                            onGoogleSignInSuccess(
                                "google_demo_101",
                                "Google User (${userProfile.deviceName.take(8)})",
                                "user@gmail.com",
                                "https://lh3.googleusercontent.com/a/default-user"
                            )
                            scope.launch {
                                snackbarHostState.showSnackbar("Logged in with Demo Google Account")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(21.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.BrandGoogle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Test with Demo Google Profile",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Error notice banner
            item {
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 4. Detailed Settings & Security Specs Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Security & Storage Details",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        AccountDetailRow(
                            icon = TablerIcons.DeviceMobile,
                            title = "Device Hardware",
                            value = userProfile.deviceName
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        AccountDetailRow(
                            icon = TablerIcons.Mail,
                            title = "Registered Account",
                            value = userProfile.email.ifBlank { "Unregistered" }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        AccountDetailRow(
                            icon = TablerIcons.BrandGoogleDrive,
                            title = "Drive AppData Folder",
                            value = if (userProfile.isLoggedIn) "Enabled (Private App Sandbox)" else "Not linked"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        AccountDetailRow(
                            icon = TablerIcons.ShieldCheck,
                            title = "DNS Firewall Engine",
                            value = "Patricia Trie + DoH"
                        )
                    }
                }
            }

            // 5. Sign Out / Session Reset Action
            if (userProfile.isLoggedIn) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = { showSignOutBottomSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BlockedCoral
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(BlockedCoral.copy(alpha = 0.5f))
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = TablerIcons.Logout,
                                contentDescription = "Sign Out",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign Out to Device Profile",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // ModalBottomSheet for Confirming Restore from Drive
    if (showRestoreBottomSheet && pendingRestoreData != null) {
        val data = pendingRestoreData!!
        ModalBottomSheet(
            onDismissRequest = { showRestoreBottomSheet = false },
            sheetState = restoreSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(GoogleTealLight.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TablerIcons.CloudDownload,
                        contentDescription = null,
                        tint = GoogleTealLight,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = "Restore Cloud Backup?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "This will restore custom whitelist & blocklist rules, custom filters, and DNS configurations from Google Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Source Device:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(data.deviceName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Custom Domain Rules:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${data.customRules.size} rules", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Custom Subscription Filters:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${data.customFilters.size} filters", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("DNS Engine:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(data.dnsSettings.providerId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showRestoreBottomSheet = false },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = {
                            showRestoreBottomSheet = false
                            scope.launch {
                                val summary = driveSyncManager.restoreBackupData(data)
                                snackbarHostState.showSnackbar("Restored ${summary.rulesCount} rules and ${summary.filtersCount} filters successfully!")
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            "Apply & Restore",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Modern Material 3 BottomSheet for Sign Out Confirmation
    if (showSignOutBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSignOutBottomSheet = false },
            sheetState = signOutSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(BlockedCoral.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TablerIcons.Logout,
                        contentDescription = null,
                        tint = BlockedCoral,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = "Sign Out of Google Account?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "You will be switched to your local device profile (${userProfile.deviceName}). Ad blocking, active filters, and DNS protection will remain active without interruption.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSignOutBottomSheet = false },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = {
                            showSignOutBottomSheet = false
                            try {
                                googleSignInClient.signOut()
                            } catch (e: Exception) {
                                // Ignored
                            }
                            onSignOut()
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlockedCoral),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            "Sign Out",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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

@Composable
private fun AccountDetailRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
