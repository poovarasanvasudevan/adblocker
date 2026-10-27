package com.kiteapps.adblocker.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiteapps.adblocker.core.model.UserTier
import com.kiteapps.adblocker.data.billing.BillingConstants
import com.kiteapps.adblocker.data.billing.BillingEvent
import com.kiteapps.adblocker.data.billing.BillingManager
import compose.icons.TablerIcons
import compose.icons.tablericons.CloudUpload
import compose.icons.tablericons.Crown
import compose.icons.tablericons.ListCheck
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.ShieldLock
import compose.icons.tablericons.Stars
import compose.icons.tablericons.World
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallSheet(
    billingManager: BillingManager,
    triggerReason: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val isPro by billingManager.isPro.collectAsState()
    val userTier by billingManager.userTier.collectAsState()

    var selectedProductId by remember { mutableStateOf(BillingConstants.PRODUCT_PRO_ANNUAL) }
    var isLoading by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Listen for billing events
    LaunchedEffect(Unit) {
        billingManager.billingEvents.collect { event ->
            isLoading = false
            when (event) {
                is BillingEvent.PurchaseSuccess -> {
                    Toast.makeText(context, "🎉 Welcome to ${event.tier.title}!", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
                is BillingEvent.PurchaseError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is BillingEvent.PurchaseCancelled -> {
                    // Silent or brief feedback
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header Shield & Crown Emblem
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.25f),
                                Color(0xFF00E676).copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TablerIcons.ShieldCheck,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "AdBlocker Pro",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Unlock Complete Digital Freedom & Total Privacy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Optional contextual trigger reason
            if (!triggerReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TablerIcons.Stars,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = triggerReason,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Feature Perks List
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PaywallFeatureRow(
                        icon = TablerIcons.ListCheck,
                        title = "Unlimited 30+ Filter Lists",
                        subtitle = "AdGuard, StevenBlack, Malware & Anti-Tracking"
                    )
                    PaywallFeatureRow(
                        icon = TablerIcons.CloudUpload,
                        title = "Google Drive Multi-Device Sync",
                        subtitle = "Sync custom rules, filters & DoH across devices"
                    )
                    PaywallFeatureRow(
                        icon = TablerIcons.World,
                        title = "Encrypted Custom DoH & DoT",
                        subtitle = "Connect NextDNS, Pi-hole & private resolvers"
                    )
                    PaywallFeatureRow(
                        icon = TablerIcons.ShieldLock,
                        title = "Full Per-App Network Firewall",
                        subtitle = "Bypass or isolate unlimited apps with zero leaks"
                    )
                    PaywallFeatureRow(
                        icon = TablerIcons.Refresh,
                        title = "Daily Automatic Threat Updates",
                        subtitle = "Zero-day malicious domain definitions synced daily"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Plan Selection Cards
            Text(
                text = "CHOOSE YOUR PLAN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Redesigned Pricing Plan Selector (Hero Annual + Companion Monthly/Lifetime)
            val monthlyPrice = billingManager.getFormattedPrice(BillingConstants.PRODUCT_PRO_MONTHLY)
            val annualPrice = billingManager.getFormattedPrice(BillingConstants.PRODUCT_PRO_ANNUAL)
            val lifetimePrice = billingManager.getFormattedPrice(BillingConstants.PRODUCT_PRO_LIFETIME)

            PricingPlanSelector(
                selectedProductId = selectedProductId,
                onPlanSelected = { selectedProductId = it },
                monthlyPrice = monthlyPrice,
                annualPrice = annualPrice,
                lifetimePrice = lifetimePrice
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Primary CTA Button
            val ctaButtonText = when (selectedProductId) {
                BillingConstants.PRODUCT_PRO_ANNUAL -> "Start 7-Day Free Trial"
                BillingConstants.PRODUCT_PRO_LIFETIME -> "Unlock Pro Lifetime ($lifetimePrice)"
                else -> "Start Monthly Protection ($monthlyPrice/mo)"
            }

            val ctaButtonColor = when (selectedProductId) {
                BillingConstants.PRODUCT_PRO_LIFETIME -> Color(0xFFFFB300)
                BillingConstants.PRODUCT_PRO_ANNUAL -> Color(0xFF00E676)
                else -> Color(0xFF00E5FF)
            }

            Button(
                onClick = {
                    if (activity != null) {
                        isLoading = true
                        billingManager.launchPurchaseFlow(
                            activity = activity,
                            productId = selectedProductId,
                            onLaunchFailed = { error ->
                                isLoading = false
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ctaButtonColor,
                    contentColor = Color(0xFF050B14)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF050B14),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (selectedProductId) {
                                BillingConstants.PRODUCT_PRO_LIFETIME -> TablerIcons.Crown
                                else -> TablerIcons.Stars
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ctaButtonText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Restore Purchases & Developer Sandbox Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        isLoading = true
                        billingManager.restorePurchases { isRestored, tier ->
                            isLoading = false
                            if (isRestored) {
                                Toast.makeText(context, "Restored: ${tier.title}", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "No active subscription found on this Google account", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Restore Purchases",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sandbox bypass for instant testing on emulators
                TextButton(
                    onClick = {
                        val nextTier = if (isPro) UserTier.FREE else UserTier.PRO_ANNUAL
                        billingManager.setDevTier(nextTier)
                        Toast.makeText(context, "Dev: Switched to ${nextTier.title}", Toast.LENGTH_SHORT).show()
                        if (nextTier.isPro) onDismiss()
                    }
                ) {
                    Text(
                        text = if (isPro) "Dev: Set Free" else "Dev: Unlock Pro",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E676)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 6. Subscriptions Disclaimer
            Text(
                text = "Subscriptions renew automatically via Google Play until cancelled. Manage or cancel anytime in Play Store > Subscriptions.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun PaywallFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E5FF).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
