package com.kiteapps.adblocker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiteapps.adblocker.core.model.DnsLog
import com.kiteapps.adblocker.ui.theme.BlockedCoral
import com.kiteapps.adblocker.ui.theme.ShieldActiveGreen
import compose.icons.TablerIcons
import compose.icons.tablericons.Activity
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Bolt
import compose.icons.tablericons.ChartBar
import compose.icons.tablericons.Check
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Database
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.World
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    logs: List<DnsLog>,
    totalBlocked: Long,
    totalQueries: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Aggregated Metrics
    val activeQueries = remember(totalQueries, logs) {
        maxOf(totalQueries, logs.size.toLong())
    }
    val activeBlocked = remember(totalBlocked, logs) {
        val logBlocked = logs.count { it.isBlocked }.toLong()
        maxOf(totalBlocked, logBlocked)
    }
    val blockRate = remember(activeBlocked, activeQueries) {
        if (activeQueries > 0L) {
            (activeBlocked.toDouble() / activeQueries.toDouble()) * 100.0
        } else 0.0
    }
    val dataSavedFormatted = remember(activeBlocked) {
        val mb = (activeBlocked * 85.0) / 1024.0
        if (mb >= 1024.0) {
            "${DecimalFormat("#,##0.0").format(mb / 1024.0)} GB"
        } else {
            "${DecimalFormat("#,##0.0").format(mb)} MB"
        }
    }
    val avgLatency = remember(logs) {
        if (logs.isNotEmpty()) {
            val valid = logs.filter { it.latencyMs > 0 }
            if (valid.isNotEmpty()) {
                valid.map { it.latencyMs }.average().toLong()
            } else 12L
        } else 0L
    }

    // Top Querying Apps from logs
    val topApps = remember(logs) {
        logs.groupBy { it.appName.ifBlank { it.packageName.ifBlank { "System / Network" } } }
            .map { (appName, appLogs) ->
                val blockedCount = appLogs.count { it.isBlocked }
                AppStat(
                    appName = appName,
                    packageName = appLogs.firstOrNull()?.packageName ?: "",
                    totalCount = appLogs.size,
                    blockedCount = blockedCount
                )
            }
            .sortedByDescending { it.totalCount }
            .take(5)
    }

    // Top Blocked Domains from logs
    val topBlockedDomains = remember(logs) {
        logs.filter { it.isBlocked }
            .groupBy { it.domain }
            .map { (domain, domainLogs) ->
                DomainStat(
                    domain = domain,
                    count = domainLogs.size,
                    appName = domainLogs.firstOrNull()?.appName ?: ""
                )
            }
            .sortedByDescending { it.count }
            .take(5)
    }

    // Query Types distribution (A, AAAA, HTTPS, etc.)
    val queryTypeStats = remember(logs) {
        logs.groupBy { it.queryType.ifBlank { "A" } }
            .map { (type, list) -> type to list.size }
            .sortedByDescending { it.second }
            .take(4)
    }

    // Time-bucketed logs for Activity Bar Chart (6 intervals)
    val chartBuckets = remember(logs) {
        calculateActivityBuckets(logs, bucketsCount = 7)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Analytics & Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = WindowInsets.navigationBars.asPaddingValues().let { navInsets ->
                PaddingValues(
                    top = 2.dp,
                    bottom = navInsets.calculateBottomPadding() + 20.dp
                )
            }
        ) {
            // Section 1: Bento Overview Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatTile(
                            title = "Threats Blocked",
                            value = DecimalFormat("#,###").format(activeBlocked),
                            subtitle = "${DecimalFormat("#0.0").format(blockRate)}% of queries",
                            icon = TablerIcons.Ban,
                            tint = BlockedCoral,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            title = "Estimated Saved",
                            value = dataSavedFormatted,
                            subtitle = "~85 KB per blocked ad",
                            icon = TablerIcons.Database,
                            tint = ShieldActiveGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatTile(
                            title = "Total Queries",
                            value = DecimalFormat("#,###").format(activeQueries),
                            subtitle = "DNS requests processed",
                            icon = TablerIcons.World,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            title = "Average Latency",
                            value = if (avgLatency > 0) "${avgLatency}ms" else "< 15ms",
                            subtitle = "Local tunnel resolution",
                            icon = TablerIcons.Bolt,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 2: Traffic & Blocking Activity Chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = TablerIcons.ChartBar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Activity Trends",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Legend pills
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LegendBadge(color = MaterialTheme.colorScheme.primary, text = "Allowed")
                                LegendBadge(color = BlockedCoral, text = "Blocked")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive Bar Chart Canvas
                        ActivityBarChart(
                            buckets = chartBuckets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                }
            }

            // Section 3: Protection Rate Progress Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Traffic Filter Breakdown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${DecimalFormat("#0.0").format(blockRate)}% blocked",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (blockRate > 0) BlockedCoral else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Segmented bar
                        val allowedPct = ((100.0 - blockRate).coerceIn(0.0, 100.0) / 100.0).toFloat()
                        val blockedPct = (blockRate.coerceIn(0.0, 100.0) / 100.0).toFloat()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            if (allowedPct > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(allowedPct.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (blockedPct > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(blockedPct.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(BlockedCoral)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Clean Traffic: ${DecimalFormat("#,###").format(maxOf(0L, activeQueries - activeBlocked))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Filtered Threats: ${DecimalFormat("#,###").format(activeBlocked)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Section 4: Top Querying Apps
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = TablerIcons.DeviceMobile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Top Querying Apps",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Recent Logs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (topApps.isEmpty()) {
                            Text(
                                text = "No app queries recorded yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            val maxCount = topApps.maxOfOrNull { it.totalCount } ?: 1
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                topApps.forEach { app ->
                                    AppStatRow(stat = app, maxCount = maxCount)
                                }
                            }
                        }
                    }
                }
            }

            // Section 5: Top Blocked Domains
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = TablerIcons.Ban,
                                    contentDescription = null,
                                    tint = BlockedCoral,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Top Blocked Trackers",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${topBlockedDomains.size} domains",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (topBlockedDomains.isEmpty()) {
                            Text(
                                text = "No blocked domains yet — ads and trackers will show here when blocked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                topBlockedDomains.forEachIndexed { index, domain ->
                                    DomainStatRow(rank = index + 1, stat = domain)
                                }
                            }
                        }
                    }
                }
            }

            // Section 6: Query Protocol Breakdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "DNS Record Types",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (queryTypeStats.isEmpty()) {
                            Text(
                                text = "No records captured yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                queryTypeStats.forEach { (type, count) ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = type,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = count.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LegendBadge(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityBarChart(
    buckets: List<ActivityBucket>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    if (buckets.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Chart updates as network queries occur",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    val maxTotal = maxOf(1, buckets.maxOfOrNull { it.total } ?: 1)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height - 24.dp.toPx()
        val barCount = buckets.size
        val barGroupWidth = width / barCount
        val barWidth = (barGroupWidth * 0.48f).coerceAtMost(22.dp.toPx())

        // Draw horizontal grid lines
        val lineCount = 3
        for (i in 0..lineCount) {
            val y = height * (i.toFloat() / lineCount.toFloat())
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw bars
        buckets.forEachIndexed { index, bucket ->
            val centerX = (index * barGroupWidth) + (barGroupWidth / 2f)
            val allowedHeight = (bucket.allowed.toFloat() / maxTotal.toFloat()) * height
            val blockedHeight = (bucket.blocked.toFloat() / maxTotal.toFloat()) * height

            val barLeft = centerX - (barWidth / 2f)

            // Draw Allowed bar segment
            if (allowedHeight > 0f) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(barLeft, height - allowedHeight),
                    size = Size(barWidth, allowedHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }

            // Draw Blocked bar segment stacked
            if (blockedHeight > 0f) {
                drawRoundRect(
                    color = BlockedCoral,
                    topLeft = Offset(barLeft, (height - allowedHeight - blockedHeight).coerceAtLeast(0f)),
                    size = Size(barWidth, blockedHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }

    // X-axis time label row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        buckets.forEach { b ->
            Text(
                text = b.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun AppStatRow(
    stat: AppStat,
    maxCount: Int
) {
    val progress = (stat.totalCount.toFloat() / maxCount.toFloat()).coerceIn(0.05f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stat.appName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stat.blockedCount > 0) {
                    Surface(
                        color = BlockedCoral.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${stat.blockedCount} blocked",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = BlockedCoral,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${stat.totalCount} reqs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun DomainStatRow(
    rank: Int,
    stat: DomainStat
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.domain,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (stat.appName.isNotEmpty()) {
                Text(
                    text = stat.appName,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Surface(
            color = BlockedCoral.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "${stat.count}×",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = BlockedCoral,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

data class AppStat(
    val appName: String,
    val packageName: String,
    val totalCount: Int,
    val blockedCount: Int
)

data class DomainStat(
    val domain: String,
    val count: Int,
    val appName: String
)

data class ActivityBucket(
    val label: String,
    val allowed: Int,
    val blocked: Int
) {
    val total: Int get() = allowed + blocked
}

private fun calculateActivityBuckets(logs: List<DnsLog>, bucketsCount: Int = 7): List<ActivityBucket> {
    if (logs.isEmpty()) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        return (0 until bucketsCount).map { i ->
            val t = now - ((bucketsCount - 1 - i) * 60_000L)
            ActivityBucket(label = sdf.format(Date(t)), allowed = 0, blocked = 0)
        }
    }

    val minTime = logs.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
    val maxTime = maxOf(minTime + 1000L, logs.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis())
    val span = maxTime - minTime
    val step = (span / bucketsCount).coerceAtLeast(1000L)
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    val buckets = mutableListOf<ActivityBucket>()
    for (i in 0 until bucketsCount) {
        val start = minTime + (i * step)
        val end = if (i == bucketsCount - 1) maxTime + 1L else start + step
        val inBucket = logs.filter { it.timestamp in start until end }
        val blocked = inBucket.count { it.isBlocked }
        val allowed = inBucket.size - blocked
        val labelTime = start + (step / 2)
        buckets.add(
            ActivityBucket(
                label = sdf.format(Date(labelTime)),
                allowed = allowed,
                blocked = blocked
            )
        )
    }
    return buckets
}
