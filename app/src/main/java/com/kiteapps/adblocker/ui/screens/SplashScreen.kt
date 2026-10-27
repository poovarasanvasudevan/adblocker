package com.kiteapps.adblocker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiteapps.adblocker.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    isReadyToNavigate: Boolean,
    onNavigateNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Minimum time to show splash so animations play smoothly (1.8s)
    var minTimeElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        timber.log.Timber.d("SplashScreen: Started display timer")
        delay(1800)
        timber.log.Timber.d("SplashScreen: Timer finished")
        minTimeElapsed = true
    }

    LaunchedEffect(isReadyToNavigate, minTimeElapsed) {
        timber.log.Timber.d("SplashScreen: check isReadyToNavigate=$isReadyToNavigate minTimeElapsed=$minTimeElapsed")
        if (isReadyToNavigate && minTimeElapsed) {
            timber.log.Timber.d("SplashScreen: Navigating next")
            onNavigateNext()
        }
    }

    // Entrance animation
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnim = true
    }

    val shieldScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.75f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "shieldScale"
    )

    val shieldAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(600, easing = LinearOutSlowInEasing),
        label = "shieldAlpha"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(700, delayMillis = 200, easing = LinearOutSlowInEasing),
        label = "textAlpha"
    )

    // Pulsing halo animation
    val infiniteTransition = rememberInfiniteTransition(label = "haloPulse")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Progress bar animation
    val progressAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060913),
                        Color(0xFF0A1020),
                        Color(0xFF03060D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background Hero Graphic (subtle ambient depth)
        Image(
            painter = painterResource(id = R.drawable.splash_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.32f)
        )

        // Gradient vignette overlay for cinematic contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x8003060D),
                            Color(0xFA03060D)
                        ),
                        radius = 1200f
                    )
                )
        )

        // Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            // Center: Shield Emblem & Branding
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glowing Halo Container
                Box(
                    modifier = Modifier.size(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing ambient glow
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .scale(haloPulse)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00E5FF).copy(alpha = haloAlpha),
                                        Color(0xFF10B981).copy(alpha = haloAlpha * 0.5f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Shield App Icon
                    Surface(
                        modifier = Modifier
                            .size(136.dp)
                            .scale(shieldScale)
                            .alpha(shieldAlpha),
                        shape = RoundedCornerShape(32.dp),
                        color = Color.Transparent,
                        shadowElevation = 16.dp
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_hires),
                            contentDescription = "AdBlocker Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(32.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // App Title
                Text(
                    text = "AdBlocker",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.2.sp,
                    color = Color.White,
                    modifier = Modifier.alpha(textAlpha)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Badge / Subtitle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                    modifier = Modifier.alpha(textAlpha)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "PRIVACY & AD-SHIELD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = Color(0xFF67E8F9)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Fast, local DNS filtering without compromises",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(textAlpha)
                )
            }

            // Bottom: Dynamic Loading Bar & Encrypted indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(textAlpha)
            ) {
                // Sleek animated glowing progress bar
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(50.dp)
                            .offset(x = (140 * progressAnim - 25).dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF00E5FF),
                                        Color(0xFF10B981),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Securing local engine...",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
