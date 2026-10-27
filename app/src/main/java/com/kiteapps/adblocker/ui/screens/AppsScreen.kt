package com.kiteapps.adblocker.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiteapps.adblocker.core.model.AppFirewallInfo
import com.kiteapps.adblocker.ui.components.ExpressiveSearchBar
import com.kiteapps.adblocker.ui.theme.BlockedCoral
import compose.icons.TablerIcons
import compose.icons.tablericons.Ban
import compose.icons.tablericons.BrandAndroid
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.InfoCircle
import compose.icons.tablericons.Wifi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    bypassedApps: Set<String>,
    firewalledApps: Set<String>,
    onToggleBypass: (String, Boolean) -> Unit,
    onToggleFirewall: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppFirewallInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<AppFirewallInfo>()
            for (app in apps) {
                if (app.packageName == context.packageName) continue
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = pm.getApplicationLabel(app).toString()
                list.add(
                    AppFirewallInfo(
                        packageName = app.packageName,
                        appName = label,
                        isSystemApp = isSystem,
                        isBypassed = false,
                        isFirewalled = false,
                        icon = try { pm.getApplicationIcon(app) } catch (e: Exception) { null }
                    )
                )
            }
            list.sortBy { it.appName.lowercase() }
            installedApps = list
            isLoading = false
        }
    }

    val filteredApps = remember(installedApps, searchQuery, showSystemApps, bypassedApps, firewalledApps) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesSystem = showSystemApps || !app.isSystemApp || bypassedApps.contains(app.packageName) || firewalledApps.contains(app.packageName)
            matchesSearch && matchesSystem
        }.map { app ->
            app.copy(
                isBypassed = bypassedApps.contains(app.packageName),
                isFirewalled = firewalledApps.contains(app.packageName)
            )
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "App Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = { showSystemApps = !showSystemApps }) {
                    Icon(
                        imageVector = if (showSystemApps) TablerIcons.BrandAndroid else TablerIcons.DeviceMobile,
                        contentDescription = "Toggle System Apps",
                        tint = if (showSystemApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
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
                onQueryChange = { searchQuery = it },
                placeholder = "Search installed apps or packages...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // Info Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = TablerIcons.InfoCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bypassed apps route outside VPN. Firewalled apps have network access blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppItemRow(
                            app = app,
                            onToggleBypass = { onToggleBypass(app.packageName, it) },
                            onToggleFirewall = { onToggleFirewall(app.packageName, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItemRow(
    app: AppFirewallInfo,
    onToggleBypass: (Boolean) -> Unit,
    onToggleFirewall: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isFirewalled) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else if (app.isBypassed) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            val bitmap = remember(app.icon) {
                app.icon?.let { drawableToBitmap(it) }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = TablerIcons.BrandAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Bypass Chip Button
            FilterChip(
                selected = app.isBypassed,
                onClick = { onToggleBypass(!app.isBypassed) },
                label = { Text("Bypass", style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(28.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Firewall Toggle
            IconButton(
                onClick = { onToggleFirewall(!app.isFirewalled) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (app.isFirewalled) TablerIcons.Ban else TablerIcons.Wifi,
                    contentDescription = "Toggle Firewall",
                    tint = if (app.isFirewalled) BlockedCoral else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth <= 0) 72 else drawable.intrinsicWidth
    val height = if (drawable.intrinsicHeight <= 0) 72 else drawable.intrinsicHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
