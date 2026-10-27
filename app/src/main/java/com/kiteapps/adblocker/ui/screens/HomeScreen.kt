package com.kiteapps.adblocker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiteapps.adblocker.core.model.DnsLog
import com.kiteapps.adblocker.core.model.UserProfile
import com.kiteapps.adblocker.core.model.VpnState
import com.kiteapps.adblocker.ui.components.BentoStatCard
import com.kiteapps.adblocker.ui.components.HeroShieldCard
import com.kiteapps.adblocker.ui.components.LogItemRow
import com.kiteapps.adblocker.ui.components.UserAvatar
import com.kiteapps.adblocker.ui.theme.BlockedCoral
import com.kiteapps.adblocker.ui.theme.GoogleTealLight
import com.kiteapps.adblocker.ui.theme.ShieldActiveGreen
import compose.icons.TablerIcons
import compose.icons.tablericons.Activity
import compose.icons.tablericons.AdjustmentsHorizontal
import compose.icons.tablericons.Ban
import compose.icons.tablericons.ChartBar
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Crown
import compose.icons.tablericons.Database
import compose.icons.tablericons.Gauge
import compose.icons.tablericons.Search
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.Stars
import compose.icons.tablericons.World
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vpnState: VpnState,
    dnsProviderName: String,
    totalBlocked: Long,
    totalQueries: Long,
    recentLogs: List<DnsLog>,
    userProfile: UserProfile,
    onToggleVpn: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onNavigateToFilters: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onLogClick: (DnsLog) -> Unit,
    isPro: Boolean = false,
    onUpgradeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val estimatedDataSavedMb = remember(totalBlocked) {
        val mb = (totalBlocked * 85.0) / 1024.0
        if (mb >= 1000) {
            "${DecimalFormat("#,##0.0").format(mb / 1024.0)} GB"
        } else {
            "${DecimalFormat("#,##0.0").format(mb)} MB"
        }
    }

    val blockRatio = remember(totalBlocked, totalQueries) {
        if (totalQueries > 0) {
            val pct = (totalBlocked.toDouble() / totalQueries.toDouble()) * 100.0
            "${DecimalFormat("#0.0").format(pct)}%"
        } else {
            "0%"
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TablerIcons.ShieldCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AdBlocker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            actions = {
                if (!isPro) {
                    Surface(
                        onClick = onUpgradeClick,
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF00E5FF).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = TablerIcons.Stars,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "GO PRO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFB300).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.45f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = TablerIcons.Crown,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFFFFB300)
                            )
                        }
                    }
                }

                UserAvatar(
                    userProfile = userProfile,
                    size = 32.dp,
                    onClick = onNavigateToAccount,
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Hero Shield Island Card
            item {
                HeroShieldCard(
                    vpnState = vpnState,
                    dnsProviderName = dnsProviderName,
                    onToggleClick = onToggleVpn
                )
            }

            // Section: Statistics Bento Grid
            item {
                Text(
                    text = "Protection Overview",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoStatCard(
                        title = "Ads Blocked",
                        value = DecimalFormat("#,###").format(totalBlocked),
                        icon = TablerIcons.Ban,
                        iconColor = BlockedCoral,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        subtitle = "$blockRatio blocked",
                        modifier = Modifier.weight(1f)
                    )
                    BentoStatCard(
                        title = "Total Queries",
                        value = DecimalFormat("#,###").format(totalQueries),
                        icon = TablerIcons.Search,
                        iconColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoStatCard(
                        title = "Data Conserved",
                        value = estimatedDataSavedMb,
                        icon = TablerIcons.Database,
                        iconColor = ShieldActiveGreen,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        subtitle = "Bandwidth saved",
                        modifier = Modifier.weight(1f)
                    )
                    BentoStatCard(
                        title = "DNS Engine",
                        value = "DoH",
                        icon = TablerIcons.Gauge,
                        iconColor = GoogleTealLight,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        subtitle = "Encrypted tunnel",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Shortcut to Filters
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    onClick = onNavigateToFilters,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TablerIcons.AdjustmentsHorizontal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Filter Subscriptions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "StevenBlack, AdGuard & custom filters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = TablerIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Links: Analytics & In-App AdBlock Browser
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Analytics Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToAnalytics),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = TablerIcons.ChartBar,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Icon(
                                    imageVector = TablerIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Analytics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Charts & metrics",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // In-App Browser Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToBrowser),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = TablerIcons.World,
                                        contentDescription = null,
                                        tint = GoogleTealLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Icon(
                                    imageVector = TablerIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Browser",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ad-free surfing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Live Activity Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Activity",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onNavigateToActivity,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "View All",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (recentLogs.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = TablerIcons.Activity,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No DNS queries recorded yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(minOf(recentLogs.size, 5)) { index ->
                    val log = recentLogs[index]
                    LogItemRow(
                        log = log,
                        onClick = { onLogClick(log) }
                    )
                }
            }
        }
    }
}
