package com.kiteapps.adblocker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.kiteapps.adblocker.core.model.UserProfile
import com.kiteapps.adblocker.ui.theme.ShieldActiveGreen
import compose.icons.TablerIcons
import compose.icons.tablericons.BrandGoogle
import compose.icons.tablericons.User

@Composable
fun UserAvatar(
    userProfile: UserProfile,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        if (userProfile.isLoggedIn) {
            if (userProfile.avatarUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(userProfile.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = userProfile.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(1.5.dp, ShieldActiveGreen, CircleShape),
                    loading = {
                        InitialsAvatar(name = userProfile.displayName, size = size)
                    },
                    error = {
                        InitialsAvatar(name = userProfile.displayName, size = size)
                    }
                )
            } else {
                InitialsAvatar(
                    name = userProfile.displayName,
                    size = size,
                    isGoogle = userProfile.authProvider == "google"
                )
            }
        } else {
            // Default Device / Guest Avatar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TablerIcons.User,
                    contentDescription = "Guest Profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}

@Composable
private fun InitialsAvatar(
    name: String,
    size: Dp,
    isGoogle: Boolean = true
) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "U"
    val bgColor = if (isGoogle) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isGoogle) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(bgColor)
            .border(1.5.dp, if (isGoogle) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
