package com.kiteapps.adblocker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiteapps.adblocker.core.model.DnsLog
import com.kiteapps.adblocker.core.model.RuleType
import com.kiteapps.adblocker.ui.components.ExpressiveSearchBar
import com.kiteapps.adblocker.ui.components.LogItemRow
import com.kiteapps.adblocker.ui.theme.BlockedCoral
import com.kiteapps.adblocker.ui.theme.ShieldActiveGreen
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Check
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Copy
import compose.icons.tablericons.Trash
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    logs: List<DnsLog>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterBlockedOnly: Boolean?,
    onFilterChange: (Boolean?) -> Unit,
    onClearLogs: () -> Unit,
    onAddCustomRule: (domain: String, type: RuleType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedLogForDetail by remember { mutableStateOf<DnsLog?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Query Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = { showClearConfirmDialog = true }) {
                    Icon(
                        imageVector = TablerIcons.Trash,
                        contentDescription = "Clear Logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            // Material Expressive Compact Search Bar
            ExpressiveSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search domain, app, or package...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // Compact Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterBlockedOnly == null,
                    onClick = { onFilterChange(null) },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(30.dp)
                )
                FilterChip(
                    selected = filterBlockedOnly == true,
                    onClick = { onFilterChange(true) },
                    label = { Text("Blocked", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = TablerIcons.Ban,
                            contentDescription = null,
                            tint = BlockedCoral,
                            modifier = Modifier.size(13.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(30.dp)
                )
                FilterChip(
                    selected = filterBlockedOnly == false,
                    onClick = { onFilterChange(false) },
                    label = { Text("Allowed", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = TablerIcons.Check,
                            contentDescription = null,
                            tint = ShieldActiveGreen,
                            modifier = Modifier.size(13.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Logs stream
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = TablerIcons.Clock,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching queries found" else "No DNS logs recorded yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Queries will appear here as apps connect",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogItemRow(
                            log = log,
                            onClick = { selectedLogForDetail = log }
                        )
                    }
                }
            }
        }
    }

    // Query Detail BottomSheet with Whitelist & Block Actions
    selectedLogForDetail?.let { log ->
        val sheetState = rememberModalBottomSheetState()
        val timeFormatted = remember(log.timestamp) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(log.timestamp))
        }

        ModalBottomSheet(
            onDismissRequest = { selectedLogForDetail = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                // Header: Domain & Status Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = if (log.isBlocked) BlockedCoral.copy(alpha = 0.15f)
                                else ShieldActiveGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (log.isBlocked) TablerIcons.Ban else TablerIcons.CircleCheck,
                            contentDescription = null,
                            tint = if (log.isBlocked) BlockedCoral else ShieldActiveGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = log.domain,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (log.isBlocked) BlockedCoral else ShieldActiveGreen,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (log.isBlocked) "Blocked" else "Allowed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (log.appName.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = log.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detail Rows Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (log.packageName.isNotEmpty()) {
                            DetailRow(label = "Package", value = log.packageName)
                        }
                        DetailRow(label = "Query Type", value = log.queryType)
                        DetailRow(label = "Latency", value = "${log.latencyMs} ms")
                        if (log.resolvedIp.isNotEmpty()) {
                            DetailRow(label = "Resolved IP", value = log.resolvedIp)
                        }
                        if (log.blockedBy.isNotEmpty()) {
                            DetailRow(label = "Blocked By", value = log.blockedBy)
                        }
                        DetailRow(label = "Timestamp", value = timeFormatted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: Copy and Whitelist/Block Domain
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Domain", log.domain))
                            Toast.makeText(context, "Domain copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = {
                            val targetType = if (log.isBlocked) RuleType.ALLOW else RuleType.BLOCK
                            onAddCustomRule(log.domain, targetType)
                            val msg = if (log.isBlocked) "Whitelisted ${log.domain}" else "Blocked ${log.domain}"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            selectedLogForDetail = null
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (log.isBlocked) ShieldActiveGreen else BlockedCoral
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (log.isBlocked) TablerIcons.CircleCheck else TablerIcons.Ban,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (log.isBlocked) "Whitelist Domain" else "Block Domain",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Confirm Clear Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(imageVector = TablerIcons.AlertTriangle, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
            },
            title = { Text("Clear All Query Logs?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("This will delete all recorded DNS queries from the local device database.", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLogs()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
