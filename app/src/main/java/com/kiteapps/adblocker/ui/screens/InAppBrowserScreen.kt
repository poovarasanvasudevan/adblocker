package com.kiteapps.adblocker.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kiteapps.adblocker.core.model.DnsLog
import com.kiteapps.adblocker.data.db.CustomRuleRepository
import com.kiteapps.adblocker.data.db.DnsLogRepository
import com.kiteapps.adblocker.data.db.FilterRepository
import com.kiteapps.adblocker.ui.theme.BlockedCoral
import com.kiteapps.adblocker.ui.theme.GoogleTealLight
import com.kiteapps.adblocker.ui.theme.ShieldActiveGreen
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ChevronLeft
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.ExternalLink
import compose.icons.tablericons.Home
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Refresh
import compose.icons.tablericons.Search
import compose.icons.tablericons.Share
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.World
import compose.icons.tablericons.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.URLEncoder

private data class QuickBookmark(
    val title: String,
    val url: String,
    val tag: String
)

private val DEFAULT_BOOKMARKS = listOf(
    QuickBookmark("DuckDuckGo", "https://duckduckgo.com", "Search"),
    QuickBookmark("Google", "https://www.google.com", "Search"),
    QuickBookmark("Wikipedia", "https://en.m.wikipedia.org", "Reference"),
    QuickBookmark("GitHub", "https://github.com", "Code"),
    QuickBookmark("Reddit", "https://reddit.com", "Community"),
    QuickBookmark("Hacker News", "https://news.ycombinator.com", "Tech"),
    QuickBookmark("Android Dev", "https://developer.android.com", "Docs"),
    QuickBookmark("BBC News", "https://www.bbc.com/news", "News")
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppBrowserScreen(
    filterRepo: FilterRepository,
    customRulesRepo: CustomRuleRepository,
    dnsLogRepo: DnsLogRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("") }
    var urlInputText by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var progressPct by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var blockedAdsCount by remember { mutableIntStateOf(0) }
    var isStartPage by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Intercept back button: go back in WebView history if possible, else pop back to main screen
    BackHandler {
        if (!isStartPage && webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else if (!isStartPage) {
            isStartPage = true
            urlInputText = ""
            currentUrl = ""
            webViewRef?.loadUrl("about:blank")
        } else {
            onBack()
        }
    }

    fun navigateToUrl(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val targetUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://duckduckgo.com/?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }

        urlInputText = targetUrl
        currentUrl = targetUrl
        isStartPage = false
        focusManager.clearFocus()
        webViewRef?.loadUrl(targetUrl)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back to App Shield Button
                        IconButton(
                            onClick = {
                                if (!isStartPage && webViewRef?.canGoBack() == true) {
                                    webViewRef?.goBack()
                                } else {
                                    onBack()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = TablerIcons.ArrowLeft,
                                contentDescription = "Back",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Omnibox / Search & URL Bar
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (currentUrl.startsWith("https://")) TablerIcons.Lock else TablerIcons.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (currentUrl.startsWith("https://")) ShieldActiveGreen else MaterialTheme.colorScheme.outline
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                BasicTextField(
                                    value = urlInputText,
                                    onValueChange = { urlInputText = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Go
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            navigateToUrl(urlInputText)
                                        }
                                    ),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (urlInputText.isEmpty()) {
                                                Text(
                                                    text = "Search or type URL",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                if (urlInputText.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            urlInputText = ""
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = TablerIcons.X,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        // Real-time Ad Block Badge
                        Surface(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "$blockedAdsCount ads & trackers blocked on this session by AdBlocker."
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (blockedAdsCount > 0) BlockedCoral.copy(alpha = 0.15f) else ShieldActiveGreen.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = TablerIcons.ShieldCheck,
                                    contentDescription = "Shield",
                                    tint = if (blockedAdsCount > 0) BlockedCoral else ShieldActiveGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$blockedAdsCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (blockedAdsCount > 0) BlockedCoral else ShieldActiveGreen
                                )
                            }
                        }
                    }

                    // Loading Progress Indicator
                    AnimatedVisibility(
                        visible = isLoading && progressPct in 1..99,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        LinearProgressIndicator(
                            progress = { progressPct / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.ChevronLeft,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                            tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }

                    // Forward
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.ChevronRight,
                            contentDescription = "Forward",
                            modifier = Modifier.size(20.dp),
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }

                    // Home / Start Page
                    IconButton(
                        onClick = {
                            isStartPage = true
                            urlInputText = ""
                            currentUrl = ""
                            webViewRef?.loadUrl("about:blank")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.Home,
                            contentDescription = "Home",
                            modifier = Modifier.size(20.dp),
                            tint = if (isStartPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Refresh
                    IconButton(
                        onClick = {
                            if (isLoading) webViewRef?.stopLoading() else webViewRef?.reload()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Share
                    IconButton(
                        onClick = {
                            if (currentUrl.isNotEmpty() && !isStartPage) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share URL"))
                            }
                        },
                        enabled = !isStartPage && currentUrl.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.Share,
                            contentDescription = "Share",
                            modifier = Modifier.size(19.dp),
                            tint = if (!isStartPage && currentUrl.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }

                    // Open in External Browser
                    IconButton(
                        onClick = {
                            if (currentUrl.isNotEmpty() && !isStartPage) {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    // Ignored
                                }
                            }
                        },
                        enabled = !isStartPage && currentUrl.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.ExternalLink,
                            contentDescription = "Open in external browser",
                            modifier = Modifier.size(19.dp),
                            tint = if (!isStartPage && currentUrl.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Full-screen WebView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progressPct = newProgress
                                isLoading = newProgress < 100
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title ?: ""
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                url?.let {
                                    if (it != "about:blank") {
                                        currentUrl = it
                                        urlInputText = it
                                        isStartPage = false
                                    }
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val uri = request?.url ?: return null
                                val host = uri.host?.lowercase() ?: return null

                                // Whitelist check first
                                if (customRulesRepo.isWhitelisted(host)) {
                                    return null
                                }

                                // Blacklist & Filter Rule check
                                val isBlocked = customRulesRepo.isBlacklisted(host) || filterRepo.isDomainBlocked(host)
                                if (isBlocked) {
                                    // Count blocked ad
                                    scope.launch(Dispatchers.Main) {
                                        blockedAdsCount++
                                    }

                                    // Record in DNS Activity Log
                                    scope.launch(Dispatchers.IO) {
                                        dnsLogRepo.insertLog(
                                            DnsLog(
                                                domain = host,
                                                isBlocked = true,
                                                queryType = "A",
                                                latencyMs = 0,
                                                appName = "In-App Browser",
                                                packageName = "com.kiteapps.adblocker",
                                                resolvedIp = "0.0.0.0",
                                                blockedBy = "AdBlock Filter"
                                            )
                                        )
                                    }

                                    // Intercept and return empty byte array (silently drop ad resource)
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                return null
                            }
                        }
                    }
                },
                update = { view ->
                    webViewRef = view
                }
            )

            // Start Page Overlay (when no site is currently loaded)
            if (isStartPage) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        // App Shield Icon & Header
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(GoogleTealLight.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TablerIcons.ShieldCheck,
                                contentDescription = null,
                                tint = GoogleTealLight,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Ad-Free Browser",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Trackers, popups & ad scripts are filtered in real-time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Quick Bookmarks Grid
                        Text(
                            text = "Quick Access",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(DEFAULT_BOOKMARKS) { bookmark ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { navigateToUrl(bookmark.url) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = TablerIcons.World,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bookmark.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = bookmark.tag,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
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
