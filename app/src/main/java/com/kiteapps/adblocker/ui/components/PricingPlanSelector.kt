package com.kiteapps.adblocker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiteapps.adblocker.data.billing.BillingConstants
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.Crown
import compose.icons.tablericons.Flame
import compose.icons.tablericons.Infinity
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.Stars

@Composable
fun PricingPlanSelector(
    selectedProductId: String,
    onPlanSelected: (String) -> Unit,
    monthlyPrice: String,
    annualPrice: String,
    lifetimePrice: String,
    modifier: Modifier = Modifier
) {
    // Calculate approximate monthly equivalent for annual plan
    val annualMonthlyBreakdown = remember(annualPrice) {
        val cleanNumber = annualPrice.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
        if (cleanNumber != null && cleanNumber > 0) {
            val perMonth = cleanNumber / 12.0
            val symbol = annualPrice.firstOrNull { !it.isDigit() && it != '.' } ?: '$'
            "$symbol${String.format(java.util.Locale.US, "%.2f", perMonth)}/mo"
        } else {
            "$1.25/mo"
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. HERO CARD: Annual Plan (Full Width, Featured)
        HeroAnnualPlanCard(
            isSelected = selectedProductId == BillingConstants.PRODUCT_PRO_ANNUAL,
            monthlyBreakdown = annualMonthlyBreakdown,
            fullAnnualPrice = annualPrice,
            onClick = { onPlanSelected(BillingConstants.PRODUCT_PRO_ANNUAL) }
        )

        // 2. COMPANION ROW: Monthly (Left) & Lifetime (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Monthly Card
            CompactPlanCard(
                title = "Monthly",
                badgeText = "FLEXIBLE",
                badgeColor = Color(0xFF38BDF8),
                price = monthlyPrice,
                period = "/ month",
                subtext = "Cancel anytime",
                icon = TablerIcons.Stars,
                isSelected = selectedProductId == BillingConstants.PRODUCT_PRO_MONTHLY,
                onClick = { onPlanSelected(BillingConstants.PRODUCT_PRO_MONTHLY) },
                modifier = Modifier.weight(1f)
            )

            // Lifetime Card
            CompactPlanCard(
                title = "Lifetime",
                badgeText = "ONE-TIME",
                badgeColor = Color(0xFFFFB300),
                price = lifetimePrice,
                period = "forever",
                subtext = "No recurring fees",
                icon = TablerIcons.Crown,
                isSelected = selectedProductId == BillingConstants.PRODUCT_PRO_LIFETIME,
                onClick = { onPlanSelected(BillingConstants.PRODUCT_PRO_LIFETIME) },
                modifier = Modifier.weight(1f)
            )
        }

        // 3. Dynamic "Zero Risk" Transparency Guarantee Pill
        AnimatedContent(
            targetState = selectedProductId,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "guaranteeBanner"
        ) { targetPlan ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (targetPlan) {
                    BillingConstants.PRODUCT_PRO_LIFETIME -> Color(0xFFFFB300).copy(alpha = 0.10f)
                    BillingConstants.PRODUCT_PRO_ANNUAL -> Color(0xFF00E676).copy(alpha = 0.10f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                border = BorderStroke(
                    1.dp,
                    when (targetPlan) {
                        BillingConstants.PRODUCT_PRO_LIFETIME -> Color(0xFFFFB300).copy(alpha = 0.35f)
                        BillingConstants.PRODUCT_PRO_ANNUAL -> Color(0xFF00E676).copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (targetPlan) {
                            BillingConstants.PRODUCT_PRO_LIFETIME -> TablerIcons.Crown
                            BillingConstants.PRODUCT_PRO_ANNUAL -> TablerIcons.ShieldCheck
                            else -> TablerIcons.Stars
                        },
                        contentDescription = null,
                        tint = when (targetPlan) {
                            BillingConstants.PRODUCT_PRO_LIFETIME -> Color(0xFFFFB300)
                            BillingConstants.PRODUCT_PRO_ANNUAL -> Color(0xFF00E676)
                            else -> Color(0xFF00E5FF)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (targetPlan) {
                            BillingConstants.PRODUCT_PRO_ANNUAL -> "7-Day Free Trial: Pay $0 today. Cancel anytime with 1-tap in Play Store."
                            BillingConstants.PRODUCT_PRO_LIFETIME -> "Permanent License: Linked to your Google account for all future updates."
                            else -> "Flexible Plan: Renews monthly. Pause or cancel anytime."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroAnnualPlanCard(
    isSelected: Boolean,
    monthlyBreakdown: String,
    fullAnnualPrice: String,
    onClick: () -> Unit
) {
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = spring(),
        label = "heroBorder"
    )

    val borderBrush = if (isSelected) {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF00E5FF),
                Color(0xFF00E676)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF00E5FF).copy(alpha = 0.35f),
                Color(0xFF00E676).copy(alpha = 0.20f)
            )
        )
    }

    val backgroundBrush = if (isSelected) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF00E5FF).copy(alpha = 0.14f),
                Color(0xFF00E676).copy(alpha = 0.06f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundBrush)
            .border(BorderStroke(animatedBorderWidth, borderBrush), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Featured Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "MOST POPULAR • 7-DAY TRIAL" Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TablerIcons.Flame,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "MOST POPULAR • 7-DAY TRIAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            ),
                            color = Color(0xFF00E676)
                        )
                    }
                }

                // "SAVE 50%" Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF00E5FF).copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "SAVE 50%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Main Content Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Custom Selection Radio Ring
                PlanSelectionRadio(isSelected = isSelected, activeColor = Color(0xFF00E676))

                Spacer(modifier = Modifier.width(12.dp))

                // Plan Name and Description
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Annual Plan",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Billed $fullAnnualPrice/year after 7-day trial",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pay $0.00 today",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00E676)
                    )
                }

                // Pricing Column
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = monthlyBreakdown,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = fullAnnualPrice,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPlanCard(
    title: String,
    badgeText: String,
    badgeColor: Color,
    price: String,
    period: String,
    subtext: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = spring(),
        label = "compactBorder"
    )

    val borderColor = if (isSelected) badgeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val bgColor = if (isSelected) {
        badgeColor.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        border = BorderStroke(animatedBorderWidth, borderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Selection Ring + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlanSelectionRadio(isSelected = isSelected, activeColor = badgeColor)

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Price
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = period,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PlanSelectionRadio(
    isSelected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) activeColor else Color.Transparent
            )
            .border(
                width = if (isSelected) 0.dp else 2.dp,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = Color(0xFF0A0F1D),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
