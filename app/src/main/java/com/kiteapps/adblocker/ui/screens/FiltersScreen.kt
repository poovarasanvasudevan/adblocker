package com.kiteapps.adblocker.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiteapps.adblocker.core.model.CustomRule
import com.kiteapps.adblocker.core.model.FilterList
import com.kiteapps.adblocker.core.model.RuleType
import com.kiteapps.adblocker.ui.theme.BlockedCoral
import com.kiteapps.adblocker.ui.theme.ShieldActiveGreen
import compose.icons.TablerIcons
import compose.icons.tablericons.Adjustments
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Check
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Crown
import compose.icons.tablericons.Link
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Trash
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(
    filters: List<FilterList>,
    customRules: List<CustomRule>,
    isUpdating: Boolean,
    isPro: Boolean = false,
    onToggleFilter: (String, Boolean) -> Unit,
    onAddCustomFilter: (String, String) -> Unit,
    onAddCustomRule: (String, RuleType) -> Unit,
    onRemoveCustomRule: (String) -> Unit,
    onUpdateAllFilters: () -> Unit,
    onUpgradePro: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddFilterBottomSheet by remember { mutableStateOf(false) }
    var showAddRuleBottomSheet by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "updateSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "Filters & Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onUpdateAllFilters,
                        enabled = !isUpdating
                    ) {
                        Icon(
                            imageVector = TablerIcons.Refresh,
                            contentDescription = "Update Filters",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .then(if (isUpdating) Modifier.rotate(rotation) else Modifier)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Compact Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Filter Lists (${filters.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "User Rules (${customRules.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            // Compute filter partitions and sorts for free and pro users
            val (freeFilters, proFilters) = remember(filters) {
                val free = filters.filter { !it.isPro }.sortedWith(
                    compareByDescending<FilterList> { it.isEnabled }.thenBy { it.name }
                )
                val pro = filters.filter { it.isPro }.sortedBy { it.name }
                free to pro
            }

            val sortedProUserFilters = remember(filters) {
                filters.sortedWith(
                    compareByDescending<FilterList> { it.isEnabled }
                        .thenBy { it.name }
                )
            }

            if (selectedTab == 0) {
                // Filter Lists
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp)
                ) {
                    if (!isPro) {
                        // Non-pro user: List enabled item first (non pro item), then display pro items
                        item(key = "header_free") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ACTIVE & FREE LISTS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${freeFilters.count { it.isEnabled }} of ${freeFilters.size} enabled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(freeFilters, key = { it.id }) { filter ->
                            FilterListItemCard(
                                filter = filter,
                                isPro = isPro,
                                onToggleFilter = onToggleFilter,
                                onUpgradePro = onUpgradePro
                            )
                        }

                        item(key = "header_pro") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "PRO FILTER LISTS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${proFilters.size} LOCKED",
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = "Tap to unlock",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        items(proFilters, key = { it.id }) { filter ->
                            FilterListItemCard(
                                filter = filter,
                                isPro = isPro,
                                onToggleFilter = onToggleFilter,
                                onUpgradePro = onUpgradePro
                            )
                        }
                    } else {
                        // Pro user: show enabled items first, then others
                        items(sortedProUserFilters, key = { it.id }) { filter ->
                            FilterListItemCard(
                                filter = filter,
                                isPro = isPro,
                                onToggleFilter = onToggleFilter,
                                onUpgradePro = onUpgradePro
                            )
                        }
                    }
                }
            } else {
                // User Custom Rules
                if (customRules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = TablerIcons.Adjustments,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No custom rules added yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Add Custom Rule' or whitelist/block queries from Activity",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp)
                    ) {
                        items(customRules, key = { it.domain }) { rule ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(30.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (rule.type == RuleType.BLOCK) TablerIcons.Ban else TablerIcons.CircleCheck,
                                            contentDescription = null,
                                            tint = if (rule.type == RuleType.BLOCK) BlockedCoral else ShieldActiveGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rule.domain,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (rule.type == RuleType.BLOCK) "Always Blocked" else "Whitelisted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (rule.type == RuleType.BLOCK) BlockedCoral else ShieldActiveGreen
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveCustomRule(rule.domain) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = TablerIcons.Trash,
                                            contentDescription = "Delete Rule",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                if (selectedTab == 0) showAddFilterBottomSheet = true
                else showAddRuleBottomSheet = true
            },
            icon = { Icon(imageVector = TablerIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp)) },
            text = { Text(if (selectedTab == 0) "Add Filter URL" else "Add Custom Rule", style = MaterialTheme.typography.labelLarge) },
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .height(44.dp)
        )
    }

    // Add Custom Filter List BottomSheet
    if (showAddFilterBottomSheet) {
        val sheetState = rememberModalBottomSheetState()
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { showAddFilterBottomSheet = false },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = TablerIcons.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Add Filter Subscription",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Subscribe to an adblock or hosts format list URL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List Name", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("e.g. Custom Tracking Filter", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("List URL (https://...)", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("https://example.com/hosts.txt", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank() && url.isNotBlank()) {
                            onAddCustomFilter(name.trim(), url.trim())
                            showAddFilterBottomSheet = false
                            Toast.makeText(context, "Adding filter list...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    enabled = name.isNotBlank() && url.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add & Sync Filter", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Add Custom Rule (Whitelist / Block Domain) BottomSheet
    if (showAddRuleBottomSheet) {
        val sheetState = rememberModalBottomSheetState()
        var domain by remember { mutableStateOf("") }
        var ruleType by remember { mutableStateOf(RuleType.BLOCK) }

        ModalBottomSheet(
            onDismissRequest = { showAddRuleBottomSheet = false },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = if (ruleType == RuleType.BLOCK) TablerIcons.Ban else TablerIcons.CircleCheck,
                        contentDescription = null,
                        tint = if (ruleType == RuleType.BLOCK) BlockedCoral else ShieldActiveGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Add Custom Rule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Choose whether to always block or whitelist a domain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Block vs Whitelist Cards Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { ruleType = RuleType.BLOCK },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ruleType == RuleType.BLOCK) BlockedCoral.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = if (ruleType == RuleType.BLOCK) androidx.compose.foundation.BorderStroke(1.5.dp, BlockedCoral)
                        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = TablerIcons.Ban,
                                contentDescription = null,
                                tint = if (ruleType == RuleType.BLOCK) BlockedCoral else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Block Domain",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (ruleType == RuleType.BLOCK) BlockedCoral else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Reject queries",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { ruleType = RuleType.ALLOW },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (ruleType == RuleType.ALLOW) ShieldActiveGreen.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainer
                        ),
                        border = if (ruleType == RuleType.ALLOW) androidx.compose.foundation.BorderStroke(1.5.dp, ShieldActiveGreen)
                        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = TablerIcons.CircleCheck,
                                contentDescription = null,
                                tint = if (ruleType == RuleType.ALLOW) ShieldActiveGreen else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Whitelist",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (ruleType == RuleType.ALLOW) ShieldActiveGreen else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Always allow",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Domain Name", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("e.g. ads.example.com", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val trimmed = domain.trim().lowercase()
                        if (trimmed.isNotBlank()) {
                            onAddCustomRule(trimmed, ruleType)
                            showAddRuleBottomSheet = false
                            val action = if (ruleType == RuleType.BLOCK) "Blocked" else "Whitelisted"
                            Toast.makeText(context, "$action $trimmed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    enabled = domain.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ruleType == RuleType.BLOCK) BlockedCoral else ShieldActiveGreen
                    )
                ) {
                    Icon(
                        imageVector = if (ruleType == RuleType.BLOCK) TablerIcons.Ban else TablerIcons.CircleCheck,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (ruleType == RuleType.BLOCK) "Add to Blocklist" else "Add to Whitelist",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterListItemCard(
    filter: FilterList,
    isPro: Boolean,
    onToggleFilter: (String, Boolean) -> Unit,
    onUpgradePro: () -> Unit
) {
    val isItemEnabled = isPro || !filter.isPro

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isItemEnabled) {
                    Modifier.clickable { onUpgradePro() }
                } else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (filter.isEnabled && isItemEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
            else if (!isItemEnabled) MaterialTheme.colorScheme.surfaceContainerLowest
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isItemEnabled) 1.0f else 0.65f)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = filter.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (filter.isPro) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = TablerIcons.Crown,
                                    contentDescription = "Pro only",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "PRO",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (filter.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = filter.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (filter.ruleCount > 0)
                                "${DecimalFormat("#,###").format(filter.ruleCount)} rules"
                            else "Active",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = filter.category.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = filter.isEnabled && isItemEnabled,
                enabled = isItemEnabled,
                onCheckedChange = {
                    if (isItemEnabled) {
                        onToggleFilter(filter.id, it)
                    } else {
                        onUpgradePro()
                    }
                },
                thumbContent = if (filter.isEnabled && isItemEnabled) {
                    {
                        Icon(
                            imageVector = TablerIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else if (!isItemEnabled) {
                    {
                        Icon(
                            imageVector = TablerIcons.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(SwitchDefaults.IconSize * 0.75f)
                        )
                    }
                } else null
            )
        }
    }
}
